/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.ai

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class ContextFirstSuggestionRequestContractTest :
    StringSpec({
        "automatic suggestion requests are built from conversation turns instead of editor draft text" {
            val service =
                File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt").readText()
            val requestBlock =
                service.substringAfter("val suggestionRequest =")
                    .substringBefore("langouApi.streamSuggestions(")

            service shouldContain "trigger: SuggestionTrigger = SuggestionTrigger.ContextChange"
            requestBlock shouldContain "turns = turns"
            requestBlock shouldContain "memorySummary = retrieved.summary.takeIf(String::isNotBlank)"
            requestBlock shouldContain "if (trigger == SuggestionTrigger.ManualRefresh)"
            requestBlock shouldContain "\"context_change\""
            requestBlock shouldNotContain "draft ="
        }
    })
