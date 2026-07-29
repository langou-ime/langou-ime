// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.setup

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.osfans.trime.R
import com.osfans.trime.langou.context.ContextPermissionStatus
import com.osfans.trime.langou.context.LegacyCapturePermissionActivity
import com.osfans.trime.langou.context.LegacyScreenshotBroker
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.appContext

enum class SetupPage {
    Enable,
    Select,
    ContextAccess,
    LegacyCapture,
    ;

    fun getStepText(context: Context) = context.getText(
        when (this) {
            Enable -> R.string.setup__step_one
            Select -> R.string.setup__step_two
            ContextAccess -> R.string.setup__step_context
            LegacyCapture -> R.string.setup__step_legacy_capture
        },
    )

    fun getHintText(context: Context) = context.getText(
        when (this) {
            Enable -> R.string.setup__enable_ime_hint
            Select -> R.string.setup__select_ime_hint
            ContextAccess -> R.string.setup__context_access_hint
            LegacyCapture -> R.string.setup__legacy_capture_hint
        },
    )

    fun getButtonText(context: Context) = context.getText(
        when (this) {
            Enable -> R.string.setup__enable_ime
            Select -> R.string.setup__select_ime
            ContextAccess -> R.string.setup__context_access_action
            LegacyCapture -> R.string.setup__legacy_capture_action
        },
    )

    fun getButtonAction(context: Context) {
        when (this) {
            Enable -> InputMethodUtils.showImeEnablerActivity(context)
            Select -> InputMethodUtils.showImePicker()
            ContextAccess ->
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            LegacyCapture ->
                context.startActivity(Intent(context, LegacyCapturePermissionActivity::class.java))
        }
    }

    fun isDone() = when (this) {
        Enable -> InputMethodUtils.checkIsTrimeEnabled()
        Select -> InputMethodUtils.checkIsTrimeSelected()
        ContextAccess -> ContextPermissionStatus.isAccessibilityEnabled(appContext)
        LegacyCapture -> LegacyScreenshotBroker.isAvailable()
    }

    companion object {
        fun availablePages(): List<SetupPage> =
            entries.filter { it != LegacyCapture || Build.VERSION.SDK_INT < Build.VERSION_CODES.R }

        fun SetupPage.isLastPage() = this == availablePages().last()

        fun Int.isLastPage() = this == availablePages().size - 1

        fun hasUndonePage() = availablePages().any { !it.isDone() }

        fun firstUndonePage() = availablePages().firstOrNull { !it.isDone() }
    }
}
