/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou

import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.langou.context.ContextSnapshotStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class AiSuggestionSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val imeComponent =
        ComponentName(
            targetContext,
            "com.osfans.trime.ime.core.TrimeInputMethodService",
        )
    private var previousIme: String = ""
    private var previousAccessibilityServices: String = ""
    private var previousAccessibilityEnabled: String = "0"

    @Before
    fun configureImeAndOverrides() {
        previousIme =
            Settings.Secure.getString(
                targetContext.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            ).orEmpty()
        previousAccessibilityServices =
            Settings.Secure.getString(
                targetContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
        previousAccessibilityEnabled =
            Settings.Secure.getString(
                targetContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
            ).orEmpty().ifBlank { "0" }
        val debugContextService =
            ComponentName(
                targetContext,
                "com.osfans.trime.langou.context.LangouAccessibilityService",
            ).flattenToString()
        val enabledServices =
            (previousAccessibilityServices.split(':').filter(String::isNotBlank) + debugContextService)
                .distinct()
                .joinToString(":")
        shell("settings put secure enabled_accessibility_services $enabledServices")
        shell("settings put secure accessibility_enabled 1")
        shell("am broadcast -n ${targetContext.packageName}/com.osfans.trime.langou.LangouDebugReceiver --es command install_fake_ai")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        shell("ime enable ${imeComponent.flattenToShortString()}")
        shell("ime set ${imeComponent.flattenToShortString()}")
        assertEquals(
            "instrumentation must run against the debug Langou IME package",
            "tech.langou.ime.debug",
            targetContext.packageName,
        )
        assertEquals(
            "the debug Langou IME must become the default input method before continuing",
            imeComponent.flattenToShortString(),
            Settings.Secure.getString(
                targetContext.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            ),
        )
        assertNotEquals(
            "connected tests must not silently fall back to the release IME package",
            "tech.langou.ime/com.osfans.trime.ime.core.TrimeInputMethodService",
            Settings.Secure.getString(
                targetContext.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            ),
        )
    }

    @After
    fun restoreState() {
        ContextSnapshotStore.clear()
        if (previousIme.isNotBlank()) {
            shell("ime set $previousIme")
        }
        if (previousAccessibilityServices.isBlank()) {
            shell("settings delete secure enabled_accessibility_services")
        } else {
            shell("settings put secure enabled_accessibility_services $previousAccessibilityServices")
        }
        shell("settings put secure accessibility_enabled $previousAccessibilityEnabled")
        shell("am broadcast -n ${targetContext.packageName}/com.osfans.trime.langou.LangouDebugReceiver --es command clear")
    }

    @Test
    fun showsThreeAiSuggestionsFromConversationContext() {
        ActivityScenario.launch(ImeHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.editor.requestFocus()
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(
                    activity.editor,
                    InputMethodManager.SHOW_IMPLICIT,
                )
                activity.forceShowIme()
            }

            assertTrue(awaitImeVisible(scenario))

            shell(
                "am broadcast -n ${targetContext.packageName}/com.osfans.trime.langou.LangouDebugReceiver " +
                    "--es command inject_context",
            )

            val expected =
                listOf(
                    "好呀，火锅可以呀，我早点出发～",
                    "当然可以呀，你定好位告诉我～",
                    "可以，不过我想早点确认时间。",
                )
            assertTrue(
                "the IME must generate exactly three replies from the injected conversation; " +
                    "actual=${TrimeInputMethodService.debugAiSuggestionTexts()}",
                awaitCondition(timeoutMillis = 20_000) {
                    TrimeInputMethodService.debugAiSuggestionTexts() == expected
                },
            )
            assertEquals(expected, TrimeInputMethodService.debugAiSuggestionTexts())

            val device = UiDevice.getInstance(instrumentation)
            assertTrue(
                "the first generated context reply must be visible in the IME suggestion strip",
                awaitCondition(timeoutMillis = 10_000) {
                    device.hasObject(By.text(expected.first()))
                },
            )
        }
    }

    private fun awaitCondition(
        timeoutMillis: Long = 15_000,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    private fun awaitImeVisible(
        scenario: ActivityScenario<ImeHostActivity>,
        timeoutMillis: Long = 20_000,
    ): Boolean = awaitCondition(timeoutMillis) {
        var active = false
        scenario.onActivity { activity ->
            activity.editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java).showSoftInput(
                activity.editor,
                InputMethodManager.SHOW_IMPLICIT,
            )
            activity.forceShowIme()
            active =
                activity
                    .getSystemService(InputMethodManager::class.java)
                    .isActive(activity.editor) &&
                ViewCompat
                    .getRootWindowInsets(activity.editor)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        active
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor =
            instrumentation.uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
    }
}
