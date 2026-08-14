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

        "merges adjacent lines from the same bubble and filters read-status noise" {
            val items =
                listOf(
                    VisibleText("这周六可以呀", centerX = 850, screenWidth = 1080, centerY = 780),
                    VisibleText("下午三点见？", centerX = 852, screenWidth = 1080, centerY = 860),
                    VisibleText("已读", centerX = 920, screenWidth = 1080, centerY = 930),
                    VisibleText("那我先订票", centerX = 220, screenWidth = 1080, centerY = 1080),
                )

            ChatTextSegmenter.segment(items) shouldContainExactly
                listOf(
                    ChatTurn("self", "这周六可以呀\n下午三点见？"),
                    ChatTurn("other", "那我先订票"),
                )
        }

        "sorts OCR or accessibility lines by on-screen position before building turns" {
            val items =
                listOf(
                    VisibleText("我这边可以", centerX = 860, screenWidth = 1080, centerY = 1280),
                    VisibleText("那就周六见", centerX = 220, screenWidth = 1080, centerY = 940),
                    VisibleText("下午三点？", centerX = 225, screenWidth = 1080, centerY = 1020),
                )

            ChatTextSegmenter.segment(items) shouldContainExactly
                listOf(
                    ChatTurn("other", "那就周六见\n下午三点？"),
                    ChatTurn("self", "我这边可以"),
                )
        }
    })
