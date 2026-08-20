/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.ai

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class AiSuggestionsUiContractTest :
    StringSpec({
        val source =
            File("src/main/java/com/osfans/trime/ime/bar/ui/AiSuggestionsUi.kt").readText()

        "renders reply chips before management actions" {
            val layout = source.substringAfter("private fun ensureSuggestionLayout()")
            layout.indexOf("suggestionChips.forEach") shouldBeLessThan
                layout.indexOf("content.addView(refreshChip")
            layout.indexOf("suggestionChips.forEach") shouldBeLessThan
                layout.indexOf("content.addView(forgetChip")
        }

        "allows reply chips to wrap to two lines instead of forcing one-line truncation" {
            source shouldContain "maxWidth = ctx.dp(MAX_SUGGESTION_WIDTH_DP)"
            source shouldContain "maxLines = if (multiline) 2 else 1"
            source shouldContain "isSingleLine = !multiline"
        }

        "updates stable reply chips while SSE suggestions stream in" {
            source shouldContain "private val suggestionChips"
            source shouldContain "suggestionChips.forEachIndexed { index, chip ->"
            source shouldContain "chip.text = selection.items.getOrNull(index).orEmpty()"
            source shouldContain "chip.isVisible = index < selection.items.size"
        }

        "keeps the first reply visible after every streamed update" {
            source shouldContain "root.post { root.scrollTo(0, 0) }"
        }
    })

private infix fun Int.shouldBeLessThan(other: Int) {
    (this in 0 until other) shouldBe true
}
