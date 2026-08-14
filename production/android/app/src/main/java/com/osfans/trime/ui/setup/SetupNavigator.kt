/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

object SetupNavigator {
    data class Navigation(
        val nextIndex: Int?,
        val launchAction: Boolean,
    )

    fun firstUndoneIndex(doneStates: List<Boolean>): Int? =
        doneStates.indexOfFirst { done -> !done }.takeIf { it >= 0 }

    /**
     * Continues a setup chain after Android returns control to the app.
     *
     * A system screen is opened automatically only when the page the user just left has become
     * complete. If they cancelled it, [nextIndex] remains the current page and Android is never
     * allowed to trap them in a permission-screen loop.
     */
    fun next(
        currentIndex: Int,
        doneStates: List<Boolean>,
    ): Navigation {
        val nextIndex = firstUndoneIndex(doneStates)
        val completedCurrentPage =
            currentIndex in doneStates.indices && doneStates[currentIndex]
        return Navigation(
            nextIndex = nextIndex,
            launchAction =
                nextIndex != null &&
                    completedCurrentPage &&
                    nextIndex > currentIndex,
        )
    }
}
