/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.base

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

class BundledRimeDataTest :
    StringSpec({
        val shared = File("build/generated/rimeAssets/shared")

        "bundles full pinyin vocabulary and emoji data" {
            listOf(
                "default.yaml",
                "luna_pinyin.dict.yaml",
                "luna_pinyin_simp.schema.yaml",
                "pinyin.yaml",
                "essay.txt",
                "emoji_suggestion.yaml",
                "opencc/emoji.json",
            ).forEach { relativePath ->
                shared.resolve(relativePath).isFile shouldBe true
            }
        }

        "bundles langou numeric nine key schema" {
            val schema = shared.resolve("langou_t9.schema.yaml").readText()

            schema.contains("schema_id: langou_t9") shouldBe true
            schema.contains(
                "xlit/abcdefghijklmnopqrstuvwxyz/22233344455566677778889999/",
            ) shouldBe true
            schema.contains("t9_processor") shouldBe false
        }

        "checksums include every generated schema required on first install" {
            val checksums = File("src/main/assets/checksums.json").readText()

            listOf(
                "shared/default.yaml",
                "shared/luna_pinyin.dict.yaml",
                "shared/luna_pinyin_simp.schema.yaml",
                "shared/langou_t9.schema.yaml",
                "shared/essay.txt",
                "shared/emoji_suggestion.yaml",
            ).forEach { relativePath ->
                checksums.contains("\"$relativePath\"") shouldBe true
            }
        }
    })
