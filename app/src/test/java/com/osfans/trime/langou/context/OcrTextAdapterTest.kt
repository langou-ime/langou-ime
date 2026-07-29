/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly

class OcrTextAdapterTest :
    StringSpec({
        "keeps confident OCR lines and preserves their horizontal position" {
            val lines =
                listOf(
                    OcrLine("要一起看电影吗？", confidence = 0.91f, centerX = 220),
                    OcrLine("低可信噪声", confidence = 0.42f, centerX = 500),
                    OcrLine("好呀", confidence = 0.88f, centerX = 850),
                )

            OcrTextAdapter.toVisibleText(lines, screenWidth = 1_080) shouldContainExactly
                listOf(
                    VisibleText("要一起看电影吗？", centerX = 220, screenWidth = 1_080),
                    VisibleText("好呀", centerX = 850, screenWidth = 1_080),
                )
        }
    })
