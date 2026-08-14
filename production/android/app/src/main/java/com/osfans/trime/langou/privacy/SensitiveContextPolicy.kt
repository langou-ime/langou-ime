/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.privacy

import android.text.InputType
import java.util.Locale

data class ContextSignals(
    val inputType: Int,
    val packageName: String,
    val screenLabels: String = "",
    val secureWindow: Boolean = false,
)

object SensitiveContextPolicy {
    private val blockedPackageFragments =
        setOf(
            "alipay",
            "unionpay",
            "tenpay",
            ".bank",
            ".icbc",
            ".cmb",
            ".ccb",
            ".boc",
            "wallet",
            "keepass",
            "bitwarden",
            "onepassword",
            "1password",
            "passwordmanager",
        )

    private val sensitiveScreenPattern =
        Regex(
            "支付密码|交易密码|付款码|收银台|银行卡|信用卡|借记卡|安全验证|身份验证|" +
                "验证码|动态码|转账|汇款|确认付款|password|passcode|payment|" +
                "confirm payment|checkout|wallet|security verification|" +
                "identity verification|verification code|one-time password|" +
                "enter pin|bank card|credit card|debit card|money transfer",
            RegexOption.IGNORE_CASE,
        )

    fun canCollect(signals: ContextSignals): Boolean {
        if (signals.secureWindow || isPassword(signals.inputType)) return false
        val normalizedPackage = signals.packageName.lowercase(Locale.ROOT)
        if (blockedPackageFragments.any(normalizedPackage::contains)) return false
        if (sensitiveScreenPattern.containsMatchIn(signals.screenLabels)) return false
        return true
    }

    private fun isPassword(inputType: Int): Boolean {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT ->
                variation in
                    setOf(
                        InputType.TYPE_TEXT_VARIATION_PASSWORD,
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    )
            InputType.TYPE_CLASS_NUMBER ->
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }
}
