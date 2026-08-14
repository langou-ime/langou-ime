/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.memory

import java.util.Locale
import java.util.UUID

enum class IdentityConfidence {
    High,
    Low,
}

data class ConversationIdentity(
    val id: String,
    val persistent: Boolean,
)

fun interface ConversationIdHasher {
    fun hash(value: String): String
}

class ConversationIdentityResolver(
    private val hasher: ConversationIdHasher,
    private val ephemeralIdFactory: () -> String = {
        "temp_${UUID.randomUUID().toString().replace("-", "")}"
    },
) {
    private val ephemeralIds = mutableMapOf<String, String>()

    fun resolve(
        application: String,
        conversationHint: String?,
        confidence: IdentityConfidence,
        contextSeed: String? = null,
    ): ConversationIdentity {
        val normalizedApplication = application.trim().lowercase(Locale.ROOT)
        val normalizedHint =
            conversationHint
                ?.trim()
                ?.replace(WHITESPACE, " ")
                ?.lowercase(Locale.ROOT)
                .orEmpty()
        val normalizedSeed =
            contextSeed
                ?.trim()
                ?.replace(WHITESPACE, " ")
                ?.lowercase(Locale.ROOT)
                .orEmpty()
        if (confidence == IdentityConfidence.High && normalizedHint.isNotEmpty()) {
            return ConversationIdentity(
                id = "conv_${hasher.hash("$normalizedApplication\u0000$normalizedHint")}",
                persistent = true,
            )
        }
        val sessionKey =
            buildString {
                append(normalizedApplication)
                append('\u0000')
                append(normalizedHint)
                if (normalizedSeed.isNotEmpty()) {
                    append('\u0000')
                    append(hasher.hash(normalizedSeed))
                }
            }
        return ConversationIdentity(
            id = ephemeralIds.getOrPut(sessionKey, ephemeralIdFactory),
            persistent = false,
        )
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
