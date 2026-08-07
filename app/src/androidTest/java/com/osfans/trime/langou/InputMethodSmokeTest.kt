/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class InputMethodSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val imeComponent =
        ComponentName(
            targetContext,
            "com.osfans.trime.ime.core.TrimeInputMethodService",
        )
    private var previousIme: String = ""

    @Before
    fun selectLangouIme() {
        previousIme =
            Settings.Secure.getString(
                targetContext.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            ).orEmpty()
        shell("settings put secure show_ime_with_hard_keyboard 1")
        shell("ime enable ${imeComponent.flattenToShortString()}")
        shell("ime set ${imeComponent.flattenToShortString()}")
    }

    @After
    fun restorePreviousIme() {
        if (previousIme.isNotBlank()) {
            shell("ime set $previousIme")
        }
    }

    @Test
    fun inputMethodAndCaptureServicesHaveTheExpectedSecurityBoundary() {
        val packageManager = targetContext.packageManager
        val ime = packageManager.getServiceInfo(imeComponent, 0)
        assertTrue(ime.exported)
        assertEquals(Manifest.permission.BIND_INPUT_METHOD, ime.permission)

        val accessibility =
            packageManager.getServiceInfo(
                ComponentName(
                    targetContext,
                    "com.osfans.trime.langou.context.LangouAccessibilityService",
                ),
                0,
            )
        assertTrue(accessibility.exported)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, accessibility.permission)

        val legacyCapture =
            packageManager.getServiceInfo(
                ComponentName(
                    targetContext,
                    "com.osfans.trime.langou.context.LegacyCaptureService",
                ),
                0,
            )
        assertFalse(legacyCapture.exported)
    }

    @Test
    fun selectedLangouImeOpensForARealEditableField() {
        ActivityScenario.launch(ImeHostActivity::class.java).use { scenario ->
            assertTrue(
                awaitCondition {
                    Settings.Secure.getString(
                        targetContext.contentResolver,
                        Settings.Secure.DEFAULT_INPUT_METHOD,
                    ) == imeComponent.flattenToShortString()
                },
            )

            assertTrue(
                awaitCondition {
                    var active = false
                    scenario.onActivity { activity ->
                        val inputMethodManager =
                            activity.getSystemService(InputMethodManager::class.java)
                        active =
                            inputMethodManager.isActive(activity.editor) &&
                                ViewCompat
                                    .getRootWindowInsets(activity.editor)
                                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    }
                    active
                },
            )
        }
    }

    private fun awaitCondition(
        timeoutMillis: Long = 20_000,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(250)
        }
        return condition()
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor =
            instrumentation.uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
    }
}
