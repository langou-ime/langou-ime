/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ChatTextSegmenterTest :
    StringSpec({
        "segments visible bubbles by alignment while excluding controls and the editor" {
            val items =
                listOf(
                    VisibleText("20:31", centerX = 540, screenWidth = 1080),
                    VisibleText("今晚去吃火锅吗？", centerX = 240, screenWidth = 1080),
                    VisibleText("发送", centerX = 940, screenWidth = 1080),
                    VisibleText(
                        "我正想问你呢",
                        centerX = 840,
                        screenWidth = 1080,
                    ),
                    VisibleText(
                        "正在输入的草稿",
                        centerX = 540,
                        screenWidth = 1080,
                        editable = true,
                    ),
                )

            ChatTextSegmenter.segment(items) shouldContainExactly
                listOf(
                    ChatTurn("other", "今晚去吃火锅吗？"),
                    ChatTurn("self", "我正想问你呢"),
                )
        }

        "deduplicates repeated accessibility labels and bounds total context" {
            val items =
                buildList {
                    repeat(20) { index ->
                        add(
                            VisibleText(
                                text = "第${index}条消息".repeat(20),
                                centerX = if (index % 2 == 0) 200 else 800,
                                screenWidth = 1080,
                            ),
                        )
                    }
                    add(VisibleText("最后一条", centerX = 800, screenWidth = 1080))
                    add(VisibleText("最后一条", centerX = 800, screenWidth = 1080))
                }

            val turns = ChatTextSegmenter.segment(items)

            (turns.sumOf { it.text.length } <= 1_600) shouldBe true
            turns.last() shouldBe ChatTurn("self", "最后一条")
        }
    })
