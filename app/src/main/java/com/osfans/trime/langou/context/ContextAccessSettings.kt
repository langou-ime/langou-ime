/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object ContextAccessSettings {
    private const val ACCESSIBILITY_DETAILS_SETTINGS =
        "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

    /** Opens Langou's own service page when supported, otherwise the accessibility service list. */
    fun open(context: Context) {
        fun Intent.fromContext(): Intent = apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val component = ComponentName(context, LangouAccessibilityService::class.java)
        val details =
            Intent(ACCESSIBILITY_DETAILS_SETTINGS)
                .putExtra(
                    Intent.EXTRA_COMPONENT_NAME,
                    component,
                ).fromContext()
        if (details.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(details)
                return
            } catch (_: ActivityNotFoundException) {
                // OEM declared the detail activity but did not expose it to third-party apps.
            } catch (_: SecurityException) {
                // Fall back to Android's public accessibility settings entry point.
            }
        }
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).fromContext())
    }
}
