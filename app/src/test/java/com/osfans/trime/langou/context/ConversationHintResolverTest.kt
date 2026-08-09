/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import com.osfans.trime.langou.memory.IdentityConfidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ConversationHintResolverTest :
    StringSpec({
        "uses a centered top-bar title as a high-confidence conversation hint" {
            val result =
                ConversationHintResolver.resolve(
                    items =
                        listOf(
                            VisibleText("返回", 60, 1_080, centerY = 130),
                            VisibleText("小夏", 540, 1_080, centerY = 140),
                            VisibleText("今晚去吃饭吗？", 230, 1_080, centerY = 900),
                        ),
                    screenHeight = 2_400,
                )

            result.text shouldBe "小夏"
            result.confidence shouldBe IdentityConfidence.High
        }

        "does not guess from app names controls or message bubbles" {
            val result =
                ConversationHintResolver.resolve(
                    items =
                        listOf(
                            VisibleText("微信", 540, 1_080, centerY = 140),
                            VisibleText("更多", 1_000, 1_080, centerY = 140),
                            VisibleText("今晚去吃饭吗？", 230, 1_080, centerY = 900),
                        ),
                    screenHeight = 2_400,
                )

            result.text shouldBe null
            result.confidence shouldBe IdentityConfidence.Low
        }

        "captures context only for the active keyboard package" {
            ContextCaptureState.deactivate()
            ContextCaptureState.activate("com.tencent.mm")

            ContextCaptureState.isActive("com.tencent.mm") shouldBe true
            ContextCaptureState.isActive("com.tencent.mobileqq") shouldBe false

            ContextCaptureState.deactivate()
            ContextCaptureState.isActive("com.tencent.mm") shouldBe false
        }
    })
