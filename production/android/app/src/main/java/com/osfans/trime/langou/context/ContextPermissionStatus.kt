package com.osfans.trime.langou.context

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object ContextPermissionStatus {
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, LangouAccessibilityService::class.java)
        val enabled =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
        return enabled
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }
}
