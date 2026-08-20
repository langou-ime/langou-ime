/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow

object ContextCaptureState {
    private val mutableActivePackages = MutableStateFlow<String?>(null)
    val activePackages = mutableActivePackages.asStateFlow()
    private val mutableCaptureRequests =
        MutableSharedFlow<String>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val captureRequests = mutableCaptureRequests.asSharedFlow()

    fun activate(packageName: String) {
        val normalized = packageName.takeIf(String::isNotBlank)
        if (mutableActivePackages.value != normalized) {
            ContextSnapshotStore.clear()
            mutableActivePackages.value = normalized
        }
    }

    fun deactivate() {
        mutableActivePackages.value = null
        ContextSnapshotStore.clear()
    }

    fun isActive(packageName: String): Boolean = activePackages.value == packageName

    fun requestCapture(packageName: String): Boolean {
        if (!isActive(packageName)) return false
        return mutableCaptureRequests.tryEmit(packageName)
    }
}
