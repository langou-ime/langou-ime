/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangouPinyinLayoutTest :
    StringSpec({
        "switches directly between branded full pinyin and nine key" {
            LangouPinyinLayout.targetSchema("langou_pinyin") shouldBe "langou_t9"
            LangouPinyinLayout.targetSchema("langou_t9") shouldBe "langou_pinyin"
            LangouPinyinLayout.targetSchema("unknown") shouldBe "langou_t9"
        }

        "falls back to branded full pinyin when the current schema is outside langou managed layouts" {
            LangouPinyinLayout.ensureManagedSchema("langou_pinyin") shouldBe "langou_pinyin"
            LangouPinyinLayout.ensureManagedSchema("langou_t9") shouldBe "langou_t9"
            LangouPinyinLayout.ensureManagedSchema("luna_pinyin") shouldBe "langou_pinyin"
            LangouPinyinLayout.ensureManagedSchema("") shouldBe "langou_pinyin"
        }
    })
