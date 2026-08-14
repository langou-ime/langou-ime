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
            source.indexOf("selection.items.forEachIndexed") shouldBeLessThan
                source.indexOf("langou_ai_refresh")
            source.indexOf("selection.items.forEachIndexed") shouldBeLessThan
                source.indexOf("langou_ai_forget_chat")
        }

        "allows reply chips to wrap to two lines instead of forcing one-line truncation" {
            source shouldContain "createChip(text, multiline = true)"
            source shouldContain "maxLines = if (multiline) 2 else 1"
            source shouldContain "isSingleLine = !multiline"
        }
    })

private infix fun Int.shouldBeLessThan(other: Int) {
    (this in 0 until other) shouldBe true
}
