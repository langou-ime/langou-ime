/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.File

class LangouThemeTest :
    StringSpec({
        val themeFile = File("src/main/assets/shared/trime.yaml")
        val theme = Theme.decode(Yaml.parseToYamlNode(themeFile.readText()).mapping!!)

        "ships only the three langou color skins" {
            theme.name shouldBe "奶油懒狗"
            theme.colorSchemes.map { it.id }.shouldContainExactlyInAnyOrder(
                "default",
                "soda",
                "moon",
            )
        }

        "ships production langou pinyin t9 number and symbol keyboards" {
            theme.presetKeyboards.keys.shouldContainExactlyInAnyOrder(
                "default",
                "langou_pinyin",
                "qwerty",
                "letter",
                "langou_t9",
                "number",
                "symbols",
            )
            theme.presetKeyboards.getValue("langou_pinyin").keys.size shouldBe 34
            theme.presetKeyboards.getValue("langou_t9").keys.size shouldBe 15
            theme.presetKeyboards.getValue("langou_pinyin").keys.map { it.click } shouldContain
                "To_t9"
            theme.presetKeyboards.getValue("langou_t9").keys.map { it.click } shouldContain
                "To_qwerty"
        }

        "langou pinyin keyboard contains each latin letter exactly once" {
            theme.presetKeyboards
                .getValue("langou_pinyin")
                .keys
                .map { it.click }
                .filter { it.length == 1 && it.single().isLetter() }
                .shouldContainExactly("qwertyuiopasdfghjklzxcvbnm".map(Char::toString))
        }
    })
