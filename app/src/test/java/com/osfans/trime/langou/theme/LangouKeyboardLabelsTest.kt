/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.theme

import io.kotest.core.spec.style.StringSpec
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

        "shows an explicit full-pinyin and t9 schema switch on both keyboards" {
            theme shouldContain "Schema_switch:\n    label: \"26/9\"\n    send: Control+grave"
            theme.substringAfter("qwerty:").substringBefore("letter:") shouldContain
                "{click: Schema_switch"
            theme.substringAfter("langou_t9:").substringBefore("number:") shouldContain
                "{click: Schema_switch"
        }
    })
