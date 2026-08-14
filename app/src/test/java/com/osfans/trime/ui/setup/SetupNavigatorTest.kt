/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SetupNavigatorTest :
    StringSpec({
        "automatically advances to the first unfinished system permission" {
            SetupNavigator.firstUndoneIndex(listOf(true, false, false)) shouldBe 1
            SetupNavigator.firstUndoneIndex(listOf(true, true, false)) shouldBe 2
            SetupNavigator.firstUndoneIndex(listOf(true, true, true)) shouldBe null
        }

        "automatically opens the next system confirmation after a completed step" {
            SetupNavigator.next(
                currentIndex = 0,
                doneStates = listOf(true, false, false),
            ) shouldBe SetupNavigator.Navigation(nextIndex = 1, launchAction = true)
            SetupNavigator.next(
                currentIndex = 1,
                doneStates = listOf(true, true, false),
            ) shouldBe SetupNavigator.Navigation(nextIndex = 2, launchAction = true)
        }

        "does not relaunch a system screen after cancellation" {
            SetupNavigator.next(
                currentIndex = 1,
                doneStates = listOf(true, false, false),
            ) shouldBe SetupNavigator.Navigation(nextIndex = 1, launchAction = false)
        }
    })
