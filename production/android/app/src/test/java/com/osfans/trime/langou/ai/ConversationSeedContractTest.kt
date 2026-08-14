/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.ai

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class ConversationSeedContractTest :
    StringSpec({
        "uses a bounded stable conversation seed instead of the entire live transcript" {
            val source =
                File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt").readText()

            source shouldContain "contextSeed = stableConversationSeed(snapshot.turns)"
            source shouldContain "private fun stableConversationSeed"
            source shouldContain ".take(3)"
            source shouldContain ".take(80)"
        }
    })
