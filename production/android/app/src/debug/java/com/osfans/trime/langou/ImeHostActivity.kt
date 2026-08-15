/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class ImeHostActivity : Activity() {
    lateinit var editor: EditText
        private set

    private var remainingShowAttempts = 0
    private val showIme =
        object : Runnable {
            override fun run() {
                if (!hasWindowFocus() || remainingShowAttempts-- <= 0) return
                getSystemService(InputMethodManager::class.java).showSoftInput(
                    editor,
                    InputMethodManager.SHOW_IMPLICIT,
                )
                WindowCompat.getInsetsController(window, editor).show(WindowInsetsCompat.Type.ime())
                val visible =
                    ViewCompat
                        .getRootWindowInsets(editor)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                if (!visible) editor.postDelayed(this, 250)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        editor =
            EditText(this).apply {
                hint = "懒狗输入法离线输入测试"
                minLines = 3
                requestFocus()
            }
        setContentView(editor)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        editor.removeCallbacks(showIme)
        if (hasFocus && editor.isAttachedToWindow) {
            remainingShowAttempts = 40
            editor.post(showIme)
        }
    }

    override fun onDestroy() {
        editor.removeCallbacks(showIme)
        super.onDestroy()
    }

    fun forceShowIme() {
        editor.removeCallbacks(showIme)
        remainingShowAttempts = 40
        WindowCompat.getInsetsController(window, editor).show(WindowInsetsCompat.Type.ime())
        editor.post(showIme)
    }
}
