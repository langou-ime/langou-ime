/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout

class RimeLifecycleTest :
    StringSpec({
        "whenAtState does not lose a transition delivered while registering the observer" {
            val lifecycle = TransitionDuringRegistrationLifecycle()

            val completed =
                withTimeout(1_000) {
                    lifecycle.whenAtState(RimeLifecycle.State.READY) { true }
                }

            completed shouldBe true
        }
    })

private class TransitionDuringRegistrationLifecycle : RimeLifecycle {
    private var state = RimeLifecycle.State.STARTING

    override val currentState: RimeLifecycle.State
        get() = state

    override val lifecycleScope: CoroutineScope = CoroutineScope(SupervisorJob())

    override fun addObserver(observer: RimeLifecycleObserver) {
        state = RimeLifecycle.State.READY
        observer.onChanged(state)
    }

    override fun removeObserver(observer: RimeLifecycleObserver) = Unit
}
