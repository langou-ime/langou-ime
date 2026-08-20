/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class AiCaptureStabilityContractTest :
    StringSpec({
        "absorbs keyboard layout churn without cancelling the active AI reply" {
            val accessibilitySource =
                File("src/main/java/com/osfans/trime/langou/context/LangouAccessibilityService.kt")
                    .readText()
            val imeSource =
                File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt")
                    .readText()

            accessibilitySource shouldContain "OCR_THROTTLE_MILLIS = 8_000L"
            imeSource shouldContain
                "trigger == SuggestionTrigger.ContextChange && langouSuggestionJob?.isActive == true"
            imeSource shouldContain "reason=request_in_flight"
        }
    })
