/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.base

internal object LangouSchemaPatch {
    private val requiredSchemas =
        listOf(
            "langou_pinyin",
            "langou_t9",
        )

    val CONTENT =
        """
        patch:
          schema_list:
            - schema: langou_pinyin
            - schema: langou_t9
        """.trimIndent()

    private val legacyTrimePatch =
        """
        patch:
          schema_list:
            - schema: luna_pinyin
            - schema: luna_pinyin_simp
        """.trimIndent()

    fun replacementFor(existing: String?): String? =
        when {
            existing == null -> CONTENT
            existing.trim() == legacyTrimePatch -> CONTENT
            else -> ensureManagedSchemas(existing).takeIf { it != existing }
        }

    private fun ensureManagedSchemas(existing: String): String {
        val desiredSchemas = (requiredSchemas + extractExistingSchemas(existing)).distinct()
        val schemaListBlock =
            buildString {
                append("  schema_list:\n")
                desiredSchemas.forEach { schemaId ->
                    append("    - schema: ")
                    append(schemaId)
                    append('\n')
                }
            }.trimEnd()

        val normalized = existing.trimEnd()
        val schemaListRegex =
            Regex("""(?ms)^  schema_list:\n(?:^\s*-\s*schema:\s*[^\n]+\n?)+""")
        if (schemaListRegex.containsMatchIn(normalized)) {
            return normalized.replace(schemaListRegex, schemaListBlock)
        }

        val patchRegex = Regex("""(?m)^patch:\s*$""")
        if (patchRegex.containsMatchIn(normalized)) {
            return normalized.replaceFirst(patchRegex, "patch:\n$schemaListBlock")
        }

        return "$normalized\n\npatch:\n$schemaListBlock"
    }

    private fun extractExistingSchemas(existing: String): List<String> =
        Regex("""(?m)^\s*-\s*schema:\s*([^\s#]+)\s*$""")
            .findAll(existing)
            .map { it.groupValues[1].trim() }
            .filter(String::isNotBlank)
            .toList()
}
