/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class AccessibilityEventSubscriptionContractTest :
    StringSpec({
        "subscribes to every event that can restart chat capture after the IME opens" {
            val config = File("src/main/res/xml/accessibility_service_config.xml").readText()

            config shouldContain "typeWindowContentChanged"
            config shouldContain "typeViewTextChanged"
            config shouldContain "typeWindowsChanged"
            config shouldContain "typeViewScrolled"
        }
    })
