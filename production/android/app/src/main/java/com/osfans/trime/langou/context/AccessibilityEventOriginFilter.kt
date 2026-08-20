/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

internal fun eventBelongsToActiveApp(
    eventPackageName: CharSequence?,
    activePackageName: String,
): Boolean = eventPackageName == null || eventPackageName.toString() == activePackageName
