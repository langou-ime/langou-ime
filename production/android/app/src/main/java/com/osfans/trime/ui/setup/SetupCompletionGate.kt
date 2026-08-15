/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

object SetupCompletionGate {
    fun canFinish(
        permissionsDone: Boolean,
        keyboardReady: Boolean,
    ): Boolean = permissionsDone && keyboardReady
}
