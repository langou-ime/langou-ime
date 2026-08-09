/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.memory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class MemoryControlsTest :
    StringSpec({
        "persists summaries only for the still-active high-confidence chat" {
            val store = testStore("langou-memory-summary-test")
            val controls = ConversationMemoryController(store)
            val identity = ConversationIdentity("conv_0123456789abcdef", persistent = true)

            controls.saveSummary(
                requestIdentity = identity,
                activeConversationId = identity.id,
                updateConversationId = identity.id,
                summary = "朋友；喜欢简短自然回复。",
            ) shouldBe true
            store.read(identity.id)!!.summary shouldBe "朋友；喜欢简短自然回复。"

            controls.saveSummary(
                requestIdentity = identity,
                activeConversationId = "conv_changed000000000",
                updateConversationId = identity.id,
                summary = "不应串到旧聊天",
            ) shouldBe false
            controls.saveSummary(
                requestIdentity = ConversationIdentity("temp_0123456789abcdef", false),
                activeConversationId = "temp_0123456789abcdef",
                updateConversationId = "temp_0123456789abcdef",
                summary = "低置信度不落盘",
            ) shouldBe false
        }

        "deletes one local chat or all local chat memory" {
            val store = testStore("langou-memory-controls-test")
            val controls = ConversationMemoryController(store)
            store.merge("conv_aaaaaaaaaaaaaaaa", emptyList(), "第一段记忆")
            store.merge("conv_bbbbbbbbbbbbbbbb", emptyList(), "第二段记忆")

            controls.deleteConversation("conv_aaaaaaaaaaaaaaaa")
            store.listIds().shouldContainExactly("conv_bbbbbbbbbbbbbbbb")
            controls.deleteAll()
            store.listIds() shouldBe emptyList()
        }
    })

private fun testStore(name: String) =
    EncryptedConversationStore(
        directory = Files.createTempDirectory(name).toFile(),
        cipher = TestMemoryCipher(),
    )

private class TestMemoryCipher : MemoryCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray =
        plaintext.map { (it.toInt() xor 0x33).toByte() }.toByteArray()

    override fun decrypt(ciphertext: ByteArray): ByteArray =
        ciphertext.map { (it.toInt() xor 0x33).toByte() }.toByteArray()
}
