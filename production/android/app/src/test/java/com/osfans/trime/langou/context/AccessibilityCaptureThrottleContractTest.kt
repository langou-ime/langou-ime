/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class AccessibilityCaptureThrottleContractTest :
    StringSpec({
        "filters noisy accessibility events and throttles repeated captures" {
            val source =
                File("src/main/java/com/osfans/trime/langou/context/LangouAccessibilityService.kt")
                    .readText()

            source shouldContain "private fun shouldHandleEvent(event: AccessibilityEvent)"
            source shouldContain "AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED"
            source shouldContain "AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED"
            source shouldContain "AccessibilityEvent.TYPE_VIEW_SCROLLED"
            source shouldContain "if (event == null || !shouldHandleEvent(event)) return"
            source shouldContain "if (!beginAccessibilityCapture()) return"
            source shouldContain "ACCESSIBILITY_CAPTURE_THROTTLE_MILLIS = 120L"
        }
    })
