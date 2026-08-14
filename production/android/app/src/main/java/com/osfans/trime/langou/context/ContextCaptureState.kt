/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ContextCaptureState {
    private val mutableActivePackages = MutableStateFlow<String?>(null)
    val activePackages = mutableActivePackages.asStateFlow()

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
}
