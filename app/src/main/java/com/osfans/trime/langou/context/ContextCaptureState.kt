/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

object ContextCaptureState {
    @Volatile
    private var activePackageName: String? = null

    fun activate(packageName: String) {
        activePackageName = packageName.takeIf(String::isNotBlank)
    }

    fun deactivate() {
        activePackageName = null
        ContextSnapshotStore.clear()
    }

    fun isActive(packageName: String): Boolean = activePackageName == packageName
}
