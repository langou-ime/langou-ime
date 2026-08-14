/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.ai

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class FocusedSuggestionContextContractTest :
    StringSpec({
        "builds the suggestion dedupe context around the latest other turn instead of the entire transcript" {
            val source =
                File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt").readText()

            source shouldContain "focusedSuggestionContext(turns)"
            source shouldContain "val latestOtherIndex = turns.indexOfLast { it.role == \"other\" }"
            source shouldContain "turns.subList(maxOf(0, latestOtherIndex - 3), latestOtherIndex + 1)"
        }
    })
