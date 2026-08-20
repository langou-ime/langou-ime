/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AccessibilityEventOriginFilterTest :
    StringSpec({
        "accepts target or unattributed events and rejects IME feedback events" {
            eventBelongsToActiveApp("com.tencent.mm", "com.tencent.mm") shouldBe true
            eventBelongsToActiveApp(null, "com.tencent.mm") shouldBe true
            eventBelongsToActiveApp("tech.langou.ime", "com.tencent.mm") shouldBe false
            eventBelongsToActiveApp("tech.langou.ime.debug", "com.tencent.mm") shouldBe false
        }
    })
