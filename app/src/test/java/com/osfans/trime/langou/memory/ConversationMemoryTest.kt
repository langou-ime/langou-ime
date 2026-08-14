/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.memory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class ConversationMemoryTest :
    StringSpec({
        "keeps high-confidence chats stable and isolates low-confidence sessions" {
            var nextEphemeralId = 0
            val resolver =
                ConversationIdentityResolver(
                    hasher = ConversationIdHasher { value -> value.hashCode().toUInt().toString(16) },
                    ephemeralIdFactory = { "temp_${++nextEphemeralId}_session" },
                )

            val xia = resolver.resolve("wechat", " 小夏 ", IdentityConfidence.High)
            val xiaAgain = resolver.resolve("wechat", "小夏", IdentityConfidence.High)
            val jie = resolver.resolve("wechat", "阿杰", IdentityConfidence.High)
            val uncertain =
                resolver.resolve(
                    "wechat",
                    null,
                    IdentityConfidence.Low,
                    contextSeed = "other:周六有空吗？\nself:可以呀",
                )
            val uncertainAgain =
                resolver.resolve(
                    "wechat",
                    null,
                    IdentityConfidence.Low,
                    contextSeed = "other:周六有空吗？\nself:可以呀",
                )
            val uncertainDifferentChat =
                resolver.resolve(
                    "wechat",
                    null,
                    IdentityConfidence.Low,
                    contextSeed = "other:明天把合同发我\nself:好",
                )

            xia.id shouldBe xiaAgain.id
            xia.id shouldNotBe jie.id
            xia.persistent shouldBe true
            uncertain.id shouldBe uncertainAgain.id
            uncertain.id shouldNotBe uncertainDifferentChat.id
            uncertain.persistent shouldBe false
            uncertain.id shouldNotBe xia.id
        }

        "encrypts records and caps recent turns at one hundred" {
            val directory = Files.createTempDirectory("langou-memory-test").toFile()
            val store =
                EncryptedConversationStore(
                    directory = directory,
                    cipher = PrefixMemoryCipher(),
                    nowEpochMillis = { DAY_MILLIS * 40 },
                )
            val turns =
                (1..105).map { index ->
                    StoredTurn(
                        role = if (index % 2 == 0) "self" else "other",
                        text = "私密消息$index",
                        capturedAtEpochMillis = DAY_MILLIS * 39 + index,
                    )
                }

            store.merge("conv_0123456789abcdef", turns, summary = "朋友；喜欢简短回复")
            val saved = store.read("conv_0123456789abcdef")!!

            saved.turns.size shouldBe 100
            saved.turns.first().text shouldBe "私密消息6"
            saved.summary shouldBe "朋友；喜欢简短回复"
            directory
                .listFiles()!!
                .single { it.extension == "memory" }
                .readText() shouldNotContain "私密消息"
        }

        "drops expired turns and deletes one chat or every chat" {
            val directory = Files.createTempDirectory("langou-memory-delete-test").toFile()
            var now = DAY_MILLIS * 50
            val store =
                EncryptedConversationStore(
                    directory = directory,
                    cipher = PrefixMemoryCipher(),
                    nowEpochMillis = { now },
                )
            store.merge(
                "conv_aaaaaaaaaaaaaaaa",
                listOf(StoredTurn("other", "过期消息", DAY_MILLIS * 19)),
            )
            store.merge(
                "conv_bbbbbbbbbbbbbbbb",
                listOf(StoredTurn("other", "新消息", DAY_MILLIS * 49)),
            )

            store.read("conv_aaaaaaaaaaaaaaaa")!!.turns shouldBe emptyList()
            store.delete("conv_aaaaaaaaaaaaaaaa")
            store.listIds().shouldContainExactly("conv_bbbbbbbbbbbbbbbb")
            store.deleteAll()
            store.listIds() shouldBe emptyList()
        }

        "retrieves the summary and a bounded mix of relevant and recent turns" {
            val memory =
                ConversationMemory(
                    conversationId = "conv_0123456789abcdef",
                    turns =
                        listOf(
                            StoredTurn("other", "上次说周末去看电影", 1),
                            StoredTurn("self", "我喜欢科幻片", 2),
                            StoredTurn("other", "工作今天有点忙", 3),
                            StoredTurn("self", "辛苦啦", 4),
                            StoredTurn("other", "周末电影几点开始？", 5),
                        ),
                    summary = "朋友；称呼小夏；喜欢轻松简短的语气。",
                    updatedAtEpochMillis = 5,
                )

            val retrieved = MemoryRetriever(maxTurns = 4).retrieve(memory, "电影几点开始")

            retrieved.summary shouldBe memory.summary
            retrieved.turns.size shouldBe 4
            retrieved.turns.map { it.text } shouldBe
                listOf("上次说周末去看电影", "工作今天有点忙", "辛苦啦", "周末电影几点开始？")
        }
    })

private class PrefixMemoryCipher : MemoryCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray =
        "encrypted:".encodeToByteArray() + plaintext.map { (it.toInt() xor 0x5A).toByte() }

    override fun decrypt(ciphertext: ByteArray): ByteArray =
        ciphertext
            .drop("encrypted:".length)
            .map { (it.toInt() xor 0x5A).toByte() }
            .toByteArray()
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
