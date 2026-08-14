/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.ai

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

class AiPriorityContractTest :
    StringSpec({
        "keeps AI suggestions above inline and clipboard suggestions" {
            val source =
                File("src/main/java/com/osfans/trime/ime/bar/InputBarDelegate.kt").readText()

            val aiIndex = source.indexOf("isAiSuggestionPresent -> AlwaysUi.State.AiSuggestion")
            val inlineIndex =
                source.indexOf("isInlineSuggestionPresent -> AlwaysUi.State.InlineSuggestion")
            val clipboardIndex = source.indexOf("isClipboardFresh -> AlwaysUi.State.Clipboard")

            (aiIndex in 0 until inlineIndex) shouldBe true
            (inlineIndex in 0 until clipboardIndex) shouldBe true
        }
    })
