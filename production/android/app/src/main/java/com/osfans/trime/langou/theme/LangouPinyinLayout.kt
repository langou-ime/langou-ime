/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.theme

object LangouPinyinLayout {
    const val FULL_PINYIN_SCHEMA = "langou_pinyin"
    const val NINE_KEY_SCHEMA = "langou_t9"

    fun targetSchema(currentSchema: String): String =
        if (currentSchema == NINE_KEY_SCHEMA) FULL_PINYIN_SCHEMA else NINE_KEY_SCHEMA

    fun ensureManagedSchema(currentSchema: String): String =
        when (currentSchema) {
            FULL_PINYIN_SCHEMA,
            NINE_KEY_SCHEMA,
            -> currentSchema
            else -> FULL_PINYIN_SCHEMA
        }
}
