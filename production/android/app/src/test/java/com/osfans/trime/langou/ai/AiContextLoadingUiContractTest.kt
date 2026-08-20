/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.ai

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class AiContextLoadingUiContractTest :
    StringSpec({
        "keeps existing suggestions visible while waiting for a refreshed context snapshot" {
            val inputBar =
                File("src/main/java/com/osfans/trime/ime/bar/InputBarDelegate.kt").readText()
            val service =
                File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt").readText()

            inputBar shouldContain
                "fun showAiContextLoading(preserveExistingSuggestions: Boolean = false)"
            inputBar shouldContain
                "if (preserveExistingSuggestions && isAiSuggestionPresent) return"
            service shouldContain
                "showAiContextLoading(preserveExistingSuggestions = debugAiSuggestions.isNotEmpty())"
            service shouldContain "ContextCaptureState.requestCapture(packageName)"
            service shouldContain "awaitLangouContextSnapshot(packageName)"
            service shouldContain "withTimeoutOrNull(LANGOU_CONTEXT_CAPTURE_TIMEOUT_MS)"
            service shouldContain "LANGOU_CONTEXT_CAPTURE_TIMEOUT_MS = 5_000L"
            service shouldContain "Langou context unavailable package=%s"
        }
    })
