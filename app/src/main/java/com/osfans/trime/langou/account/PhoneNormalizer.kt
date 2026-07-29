/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.account

object PhoneNormalizer {
    private val e164 = Regex("""^\+[1-9]\d{7,14}$""")
    private val mainlandMobile = Regex("""^1[3-9]\d{9}$""")
    private val smsCode = Regex("""^\d{6}$""")

    fun toE164(input: String): String? {
        val compact = input.filterNot(Char::isWhitespace)
        val normalized =
            when {
                mainlandMobile.matches(compact) -> "+86$compact"
                compact.startsWith("+86") && mainlandMobile.matches(compact.drop(3)) -> compact
                e164.matches(compact) -> compact
                else -> return null
            }
        return normalized.takeIf(e164::matches)
    }

    fun validCode(input: String): Boolean = smsCode.matches(input)
}
