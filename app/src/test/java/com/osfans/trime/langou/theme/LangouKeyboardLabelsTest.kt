/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class LangouKeyboardLabelsTest :
    StringSpec({
        val theme = File("src/main/assets/shared/trime.yaml").readText()

        "uses compact labels for keyboard function keys" {
            theme shouldContain "Shift_L: {label: \"⇧\", send: Shift_L}"
            theme shouldContain "BackSpace: {label: \"⌫\", repeatable: true, send: BackSpace}"
            theme shouldContain "Return: {label: \"换行\", send: Return}"
            theme shouldContain "Left: {label: \"←\", repeatable: true, send: Left}"
            theme shouldContain "Right: {label: \"→\", repeatable: true, send: Right}"
        }

        "switches full-pinyin and t9 directly without exposing the Rime schema menu" {
            theme shouldContain
                "To_t9:\n    label: \"9键\"\n    command: langou_toggle_pinyin_layout"
            theme shouldContain
                "To_qwerty:\n    label: \"26键\"\n    command: langou_toggle_pinyin_layout"
            theme.contains("Control+grave") shouldBe false
            theme.substringAfter("  langou_pinyin:\n").substringBefore("  qwerty:\n") shouldContain
                "{click: To_t9"
            theme.substringAfter("  langou_t9:\n").substringBefore("  number:\n") shouldContain
                "{click: To_qwerty"
        }
    })
