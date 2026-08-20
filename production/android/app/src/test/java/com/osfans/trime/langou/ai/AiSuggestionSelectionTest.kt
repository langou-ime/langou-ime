/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.ai

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class AiSuggestionSelectionTest :
    StringSpec({
        "exposes at most three nonblank suggestions and only inserts selected text" {
            val inserted = mutableListOf<String>()
            val selection = AiSuggestionSelection { inserted.add(it) }

            selection.update(listOf(" 好呀～ ", "", "可以呀", "明天见", "不会显示"))

            selection.items shouldContainExactly listOf("好呀～", "可以呀", "明天见")
            selection.select(1) shouldBe true
            inserted shouldContainExactly listOf("可以呀")
            selection.select(9) shouldBe false
            inserted shouldContainExactly listOf("可以呀")
        }

        "commits the exact reply text shown on a chip when streamed state moves on" {
            val inserted = mutableListOf<String>()
            val selection = AiSuggestionSelection { inserted.add(it) }

            selection.update(listOf("新的流式回复"))

            selection.selectDisplayed("用户当前看到的回复") shouldBe true
            inserted shouldContainExactly listOf("用户当前看到的回复")
        }
    })
