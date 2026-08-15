/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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

        "schema selection stays inside the serialized ready operation" {
            val service =
                File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt").readText()
            val keyboardListener =
                File(
                    "src/main/java/com/osfans/trime/ime/keyboard/CommonKeyboardActionListener.kt",
                ).readText()
            val layoutSwitch =
                keyboardListener
                    .substringAfter("private fun handleLangouPinyinLayout()")
                    .substringBefore("\n            private fun ")

            service shouldNotContain
                "rime.launchOnReady { api ->\n            lifecycleScope.launch {"
            layoutSwitch shouldNotContain "service.lifecycleScope.launch"
        }

        "repeated input starts serialize schema fallback and deployment" {
            val service =
                File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt").readText()
            val fallback =
                service
                    .substringAfter("private fun ensureLangouSchemaSelected()")
                    .substringBefore("private fun scheduleLangouSuggestions")

            fallback shouldContain "postRimeJob {"
            fallback shouldNotContain "rime.launchOnReady"
        }
    })
