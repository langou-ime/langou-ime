/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class ManagedSchemaFallbackContractTest :
    StringSpec({
        "input start forces non-langou schemas back onto branded full pinyin" {
            val service =
                File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt").readText()
            val layout =
                File("src/main/java/com/osfans/trime/langou/theme/LangouPinyinLayout.kt").readText()

            service shouldContain "ensureLangouSchemaSelected()"
            service shouldContain "Langou schema fallback current=%s target=%s"
            service shouldContain "val managedSchema = LangouPinyinLayout.ensureManagedSchema(currentSchema)"
            layout shouldContain "fun ensureManagedSchema(currentSchema: String): String"
            layout shouldContain "else -> FULL_PINYIN_SCHEMA"
        }
    })
