/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.privacy

import android.text.InputType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SensitiveContextPolicyTest :
    StringSpec({
        "blocks every password input variation" {
            val passwordTypes =
                listOf(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                )

            passwordTypes.forEach {
                SensitiveContextPolicy.canCollect(
                    ContextSignals(inputType = it, packageName = "com.example.chat"),
                ) shouldBe false
            }
        }

        "blocks secure payment banking password manager and system security surfaces" {
            listOf(
                ContextSignals(1, "com.eg.android.AlipayGphone"),
                ContextSignals(1, "com.icbc"),
                ContextSignals(1, "com.x8bit.bitwarden"),
                ContextSignals(1, "com.android.settings", screenLabels = "安全验证"),
                ContextSignals(1, "com.example.app", secureWindow = true),
                ContextSignals(1, "com.example.app", screenLabels = "请输入支付密码"),
                ContextSignals(1, "com.tencent.mm", screenLabels = "Confirm payment"),
                ContextSignals(1, "com.tencent.mm", screenLabels = "Enter PIN"),
                ContextSignals(1, "com.tencent.mm", screenLabels = "Verification code"),
            ).forEach { signals ->
                SensitiveContextPolicy.canCollect(signals) shouldBe false
            }
        }

        "allows ordinary chat editors" {
            SensitiveContextPolicy.canCollect(
                ContextSignals(
                    inputType = InputType.TYPE_CLASS_TEXT,
                    packageName = "com.tencent.mm",
                    screenLabels = "聊天信息",
                ),
            ) shouldBe true
        }
    })
