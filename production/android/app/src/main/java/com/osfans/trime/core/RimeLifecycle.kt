/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// Adapted from https://github.com/fcitx5-android/fcitx5-android/blob/364afb44dcf0d9e3db3d43a21a32601b2190cbdf/app/src/main/java/org/fcitx/fcitx5/android/core/FcitxLifecycle.kt
package com.osfans.trime.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

class RimeLifecycleRegistry : RimeLifecycle {

    private val observers = ConcurrentLinkedQueue<RimeLifecycleObserver>()

    override fun addObserver(observer: RimeLifecycleObserver) {
        observers.add(observer)
    }

    override fun removeObserver(observer: RimeLifecycleObserver) {
        observers.remove(observer)
    }

    override val currentState: RimeLifecycle.State
        get() = internalState

    private var internalState = RimeLifecycle.State.STOPPED

    override val lifecycleScope: CoroutineScope = RimeLifecycleScope(this)

    fun emitState(state: RimeLifecycle.State) = synchronized(internalState) {
        when (state) {
            RimeLifecycle.State.STARTING -> {
                checkAtState(RimeLifecycle.State.STOPPED)
                internalState = RimeLifecycle.State.STARTING
            }
            RimeLifecycle.State.READY -> {
                checkAtState(RimeLifecycle.State.STARTING)
                internalState = RimeLifecycle.State.READY
            }
            RimeLifecycle.State.STOPPING -> {
                checkAtState(RimeLifecycle.State.READY)
                internalState = RimeLifecycle.State.STOPPING
            }
            RimeLifecycle.State.STOPPED -> {
                checkAtState(RimeLifecycle.State.STOPPING)
                internalState = RimeLifecycle.State.STOPPED
            }
        }
        observers.forEach { it.onChanged(state) }
    }

    private fun checkAtState(state: RimeLifecycle.State) = takeIf { (internalState == state) }
        ?: throw IllegalStateException("Currently not at $state! Actual state is $internalState")
}

interface RimeLifecycle {
    val currentState: State
    val lifecycleScope: CoroutineScope

    fun addObserver(observer: RimeLifecycleObserver)
    fun removeObserver(observer: RimeLifecycleObserver)

    enum class State {
        STARTING,
        READY,
        STOPPING,
        STOPPED,
    }
}

interface RimeLifecycleOwner {
    val lifecycle: RimeLifecycle
}

val RimeLifecycleOwner.lifecycleScope get() = lifecycle.lifecycleScope

fun interface RimeLifecycleObserver {
    fun onChanged(value: RimeLifecycle.State)
}

class RimeLifecycleScope(
    val lifecycle: RimeLifecycle,
    override val coroutineContext: CoroutineContext = SupervisorJob(),
) : CoroutineScope,
    RimeLifecycleObserver {
    override fun onChanged(value: RimeLifecycle.State) {
        if (lifecycle.currentState >= RimeLifecycle.State.STOPPING) {
            coroutineContext.cancelChildren()
        }
    }
}

suspend fun <T> RimeLifecycle.whenAtState(
    state: RimeLifecycle.State,
    block: suspend CoroutineScope.() -> T,
): T {
    if (state != currentState) {
        awaitState(state)
    }
    return block(lifecycleScope)
}

suspend inline fun <T> RimeLifecycle.whenReady(
    noinline block: suspend CoroutineScope.() -> T,
) = whenAtState(RimeLifecycle.State.READY, block)

suspend fun RimeLifecycle.awaitState(state: RimeLifecycle.State) {
    suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        lateinit var observer: RimeLifecycleObserver

        fun unregisterAndResume() {
            if (completed.compareAndSet(false, true)) {
                removeObserver(observer)
                continuation.resume(Unit)
            }
        }

        observer = RimeLifecycleObserver {
            if (currentState == state) {
                unregisterAndResume()
            }
        }
        continuation.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) {
                removeObserver(observer)
            }
        }
        addObserver(observer)

        // The state can change between the caller's initial check and observer
        // registration. Re-checking here closes that race without double-resuming.
        if (currentState == state) {
            unregisterAndResume()
        }
    }
}
