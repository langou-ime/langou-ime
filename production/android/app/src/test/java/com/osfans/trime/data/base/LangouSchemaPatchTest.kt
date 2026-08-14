/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.base

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class LangouSchemaPatchTest :
    StringSpec({
        "enables only the branded full-pinyin and nine-key schemas" {
            LangouSchemaPatch.CONTENT shouldContain "schema: langou_pinyin"
            LangouSchemaPatch.CONTENT shouldContain "schema: langou_t9"
            LangouSchemaPatch.CONTENT shouldNotContain "schema: luna_pinyin\n"
        }

        "migrates the exact legacy Trime schema patch" {
            LangouSchemaPatch.replacementFor(null) shouldBe LangouSchemaPatch.CONTENT
            LangouSchemaPatch.replacementFor(
                """
                patch:
                  schema_list:
                    - schema: luna_pinyin
                    - schema: luna_pinyin_simp
                """.trimIndent(),
            ) shouldBe LangouSchemaPatch.CONTENT
        }

        "preserves custom schemas while prepending langou defaults" {
            LangouSchemaPatch.replacementFor(
                "patch:\n  schema_list:\n    - schema: user_custom",
            ) shouldBe
                """
                patch:
                  schema_list:
                    - schema: langou_pinyin
                    - schema: langou_t9
                    - schema: user_custom
                """.trimIndent()
        }

        "injects schema_list into existing patch blocks without removing other patch fields" {
            LangouSchemaPatch.replacementFor(
                """
                patch:
                  menu/page_size: 7
                """.trimIndent(),
            ) shouldBe
                """
                patch:
                  schema_list:
                    - schema: langou_pinyin
                    - schema: langou_t9
                  menu/page_size: 7
                """.trimIndent()
        }

        "appends patch block when a custom file has no patch section yet" {
            LangouSchemaPatch.replacementFor(
                """
                custom_key:
                  value: keep_me
                """.trimIndent(),
            ) shouldBe
                """
                custom_key:
                  value: keep_me

                patch:
                  schema_list:
                    - schema: langou_pinyin
                    - schema: langou_t9
                """.trimIndent()
        }
    })
