/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class KeyboardSchemaSyncContractTest :
    StringSpec({
        "input start and layout toggles resync the visible keyboard to the selected langou schema" {
            val service =
                File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt").readText()
            val listener =
                File("src/main/java/com/osfans/trime/ime/keyboard/CommonKeyboardActionListener.kt").readText()

            service shouldContain "fun syncLangouKeyboardToSchema(schemaId: String)"
            service shouldContain "inputView?.syncKeyboardToSchema(schemaId)"
            service shouldContain "syncLangouKeyboardToSchema(managedSchema)"
            listener shouldContain "service.syncLangouKeyboardToSchema(target)"
        }
    })
