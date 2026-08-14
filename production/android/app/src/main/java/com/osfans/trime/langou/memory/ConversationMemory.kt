/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.memory

import kotlinx.serialization.Serializable

@Serializable
data class StoredTurn(
    val role: String,
    val text: String,
    val capturedAtEpochMillis: Long,
)

@Serializable
data class ConversationMemory(
    val conversationId: String,
    val turns: List<StoredTurn> = emptyList(),
    val summary: String = "",
    val updatedAtEpochMillis: Long,
)

data class RetrievedMemory(
    val summary: String,
    val turns: List<StoredTurn>,
)

interface MemoryCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray): ByteArray
}
