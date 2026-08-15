/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class SetupCompletionGateTest :
    StringSpec({
        "setup only finishes after permissions and both Chinese layouts are ready" {
            SetupCompletionGate.canFinish(permissionsDone = false, keyboardReady = false)
                .shouldBeFalse()
            SetupCompletionGate.canFinish(permissionsDone = true, keyboardReady = false)
                .shouldBeFalse()
            SetupCompletionGate.canFinish(permissionsDone = false, keyboardReady = true)
                .shouldBeFalse()
            SetupCompletionGate.canFinish(permissionsDone = true, keyboardReady = true)
                .shouldBeTrue()
        }
    })
