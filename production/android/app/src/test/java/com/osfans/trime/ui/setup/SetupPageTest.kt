/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.core.spec.style.StringSpec

class SetupPageTest :
    StringSpec({
        "first run groups input method and context permission steps" {
            SetupPage.entries.shouldContainExactly(
                SetupPage.Enable,
                SetupPage.Select,
                SetupPage.ContextAccess,
                SetupPage.LegacyCapture,
            )
        }
    })
