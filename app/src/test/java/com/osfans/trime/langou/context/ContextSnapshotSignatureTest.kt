/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import com.osfans.trime.langou.memory.IdentityConfidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ContextSnapshotSignatureTest :
    StringSpec({
        "ignores whitespace-only OCR jitter while still changing for real chat updates" {
            val base =
                ChatContextSnapshot(
                    packageName = "com.tencent.mm",
                    application = "wechat",
                    conversationHint = "小夏",
                    identityConfidence = IdentityConfidence.High,
                    turns =
                        listOf(
                            ChatTurn("other", "今晚   一起吃饭吗？"),
                            ChatTurn("self", "可以呀\n几点？"),
                        ),
                    capturedAtEpochMillis = 1L,
                )

            val jitter =
                base.copy(
                    conversationHint = " 小夏 ",
                    turns =
                        listOf(
                            ChatTurn("other", "今晚 一起吃饭吗？"),
                            ChatTurn("self", "可以呀   几点？"),
                        ),
                    capturedAtEpochMillis = 2L,
                )

            val changed =
                base.copy(
                    turns =
                        listOf(
                            ChatTurn("other", "今晚一起吃饭吗？"),
                            ChatTurn("self", "可以呀，七点见？"),
                        ),
                    capturedAtEpochMillis = 3L,
                )

            base.stableSignature() shouldBe jitter.stableSignature()
            (base.stableSignature() == changed.stableSignature()) shouldBe false
        }
    })
