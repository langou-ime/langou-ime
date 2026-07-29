/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.ai

import com.osfans.trime.langou.privacy.ContextSignals
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AutoSuggestionGateTest :
    StringSpec({
        "generates only for changed non-sensitive chat context" {
            val gate = AutoSuggestionGate()
            val chatSignals =
                ContextSignals(
                    inputType = 1,
                    packageName = "com.tencent.mm",
                    screenLabels = "",
                    secureWindow = false,
                )

            gate.evaluate(chatSignals, "今晚去吃火锅吗？") shouldBe SuggestionDecision.Generate
            gate.evaluate(chatSignals, "今晚去吃火锅吗？") shouldBe SuggestionDecision.SkipDuplicate
            gate.complete("今晚去吃火锅吗？", successful = true)
            gate.evaluate(chatSignals, "今晚去吃火锅吗？") shouldBe SuggestionDecision.SkipDuplicate
            gate.evaluate(chatSignals, "改成明天去吃火锅吗？") shouldBe SuggestionDecision.Generate

            gate.evaluate(
                chatSignals.copy(inputType = 0x00000081),
                "银行卡密码",
            ) shouldBe SuggestionDecision.BlockSensitive
        }

        "does not generate for blank or oversized context" {
            val gate = AutoSuggestionGate(maxContextCharacters = 20)
            val signals =
                ContextSignals(
                    inputType = 1,
                    packageName = "org.telegram.messenger",
                    screenLabels = "",
                    secureWindow = false,
                )

            gate.evaluate(signals, "   ") shouldBe SuggestionDecision.SkipBlank
            gate.evaluate(signals, "这是一段超过二十个字符而且不应该上传给服务器的聊天上下文") shouldBe
                SuggestionDecision.SkipOversized
        }

        "allows the same context to retry after a failed AI request" {
            val gate = AutoSuggestionGate()
            val signals =
                ContextSignals(
                    inputType = 1,
                    packageName = "com.tencent.mm",
                )

            gate.evaluate(signals, "对方：在吗？") shouldBe SuggestionDecision.Generate
            gate.complete("对方：在吗？", successful = false)
            gate.evaluate(signals, "对方：在吗？") shouldBe SuggestionDecision.Generate
        }
    })
