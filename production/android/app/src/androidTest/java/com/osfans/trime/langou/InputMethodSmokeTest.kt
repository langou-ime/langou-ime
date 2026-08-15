/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou

import android.Manifest
import android.content.ComponentName
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.osfans.trime.ime.core.TrimeInputMethodService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.AfterClass
import org.junit.BeforeClass
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

    @Test
    fun fullPinyinCommitsAChineseCandidateInARealEditableField() {
        ActivityScenario.launch(ImeHostActivity::class.java).use { scenario ->
            assertTrue(
                awaitCondition {
                    var active = false
                    scenario.onActivity { activity ->
                        active =
                            activity
                                .getSystemService(InputMethodManager::class.java)
                                .isActive(activity.editor) &&
                            ViewCompat
                                .getRootWindowInsets(activity.editor)
                                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    }
                    active
                },
            )

            assertTrue(
                "full pinyin schema and keyboard must become ready before accepting keys",
                awaitCondition(timeoutMillis = 120_000) {
                    TrimeInputMethodService.debugSelectedSchemaId() == "langou_pinyin" &&
                        TrimeInputMethodService.debugActiveKeyboardId() == "langou_pinyin"
                },
            )

            instrumentation.sendStringSync("nihao")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_SPACE)

            var actualText = ""
            val committed =
                awaitCondition {
                    scenario.onActivity { activity -> actualText = activity.editor.text.toString() }
                    actualText.contains("你好")
                }
            assertTrue(
                "full pinyin must commit 你好; actual='$actualText', " +
                    "schema='${TrimeInputMethodService.debugSelectedSchemaId()}', " +
                    "keyboard='${TrimeInputMethodService.debugActiveKeyboardId()}'",
                committed,
            )
        }
    }

    @Test
    fun oneTapNineKeyLayoutCommitsAChineseCandidate() {
        ActivityScenario.launch(ImeHostActivity::class.java).use { scenario ->
            assertTrue(awaitImeVisible(scenario))

            val device = UiDevice.getInstance(instrumentation)
            if (!device.hasObject(By.desc("9键"))) {
                val fullPinyinSwitch =
                    device.wait(
                        Until.findObject(By.desc("26键")),
                        10_000,
                    )
                assertNotNull("九键键盘必须直接显示 26键 切换键", fullPinyinSwitch)
                clickImeObject(device, fullPinyinSwitch)
            }
            val nineKeySwitch =
                device.wait(
                    Until.findObject(By.desc("9键")),
                    10_000,
                )
            assertNotNull("全拼键盘必须直接显示 9键 切换键", nineKeySwitch)
            clickImeObject(device, nineKeySwitch)
            assertTrue(
                "点一次 9键 后内部 schema 必须切到 langou_t9",
                awaitCondition {
                    TrimeInputMethodService.debugSelectedSchemaId() == "langou_t9"
                },
            )
            assertTrue(
                "点一次 9键 后内部 keyboard 必须切到 langou_t9",
                awaitCondition {
                    TrimeInputMethodService.debugActiveKeyboardId() == "langou_t9"
                },
            )
            assertTrue(
                "点一次 9键 后必须直接显示九宫格",
                device.wait(Until.hasObject(By.desc("ABC")), 10_000),
            )

            instrumentation.sendStringSync("64426")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_SPACE)

            assertTrue(
                awaitCondition {
                    var text = ""
                    scenario.onActivity { activity -> text = activity.editor.text.toString() }
                    text.contains("你好")
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

    private fun clickImeObject(device: UiDevice, target: androidx.test.uiautomator.UiObject2) {
        target.click()
        device.waitForIdle(500)
    }

    companion object {
        private var previousIme: String = ""

        @JvmStatic
        @BeforeClass
        fun selectLangouImeForClass() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetContext = instrumentation.targetContext
            val imeComponent =
                ComponentName(
                    targetContext,
                    "com.osfans.trime.ime.core.TrimeInputMethodService",
                )
            previousIme =
                Settings.Secure.getString(
                    targetContext.contentResolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD,
                ).orEmpty()
            shell(instrumentation, "settings put secure show_ime_with_hard_keyboard 1")
            shell(instrumentation, "ime enable ${imeComponent.flattenToShortString()}")
            shell(instrumentation, "ime set ${imeComponent.flattenToShortString()}")
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

        @JvmStatic
        @AfterClass
        fun restorePreviousImeAfterClass() {
            if (previousIme.isNotBlank()) {
                shell(InstrumentationRegistry.getInstrumentation(), "ime set $previousIme")
            }
        }

        private fun shell(
            instrumentation: android.app.Instrumentation,
            command: String,
        ): String {
            val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
            return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
    }
}
