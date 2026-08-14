/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class AccessibilityContextRetentionContractTest :
    StringSpec({
        "does not immediately clear the last chat snapshot when a transient accessibility pass has no turns" {
            val source =
                File("src/main/java/com/osfans/trime/langou/context/LangouAccessibilityService.kt")
                    .readText()

            source shouldContain "val previous = ContextSnapshotStore.get(packageName)"
            source shouldContain "reason=no_turns_keep_previous"
        }
    })
