/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.privacy

object ClientRedactor {
    private val patterns =
        listOf(
            Regex("""(?<!\d)\d{17}[\dXx](?!\d)""") to "[身份证]",
            Regex("""(?<!\d)(?:\d[\s-]?){15,18}\d(?!\d)""") to "[银行卡]",
            Regex("""(?<!\d)(?:\+?86[\s-]?)?1[3-9](?:[\s-]?\d){9}(?!\d)""") to "[手机号]",
            Regex(
                """(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}(?![A-Z0-9.-])""",
            ) to "[邮箱]",
        )

    fun redact(value: String): String =
        patterns.fold(value) { sanitized, (pattern, replacement) ->
            pattern.replace(sanitized, replacement)
        }
}
