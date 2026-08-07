/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

class ImeHostActivity : Activity() {
    lateinit var editor: EditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor =
            EditText(this).apply {
                hint = "懒狗输入法离线输入测试"
                minLines = 3
                requestFocus()
            }
        setContentView(editor)
        editor.post {
            getSystemService(InputMethodManager::class.java).showSoftInput(
                editor,
                InputMethodManager.SHOW_IMPLICIT,
            )
        }
    }
}
