/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.daemon

/**
 * Keeps the RIME dispatcher alive while a session operation is suspended between native calls.
 *
 * Callers serialize access to this object with the daemon lock.
 */
internal class RimeOperationLease {
    private var activeOperations = 0

    fun acquire() {
        activeOperations += 1
    }

    fun canFinalize(hasSessions: Boolean): Boolean = !hasSessions && activeOperations == 0

    fun releaseAndCanFinalize(hasSessions: Boolean): Boolean {
        check(activeOperations > 0) { "No RIME operation lease to release" }
        activeOperations -= 1
        return canFinalize(hasSessions)
    }
}
