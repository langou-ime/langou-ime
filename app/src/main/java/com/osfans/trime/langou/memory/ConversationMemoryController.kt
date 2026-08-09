/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.memory

class ConversationMemoryController(
    private val store: EncryptedConversationStore,
) {
    fun saveSummary(
        requestIdentity: ConversationIdentity,
        activeConversationId: String?,
        updateConversationId: String,
        summary: String,
    ): Boolean {
        if (!requestIdentity.persistent) return false
        if (requestIdentity.id != updateConversationId) return false
        if (requestIdentity.id != activeConversationId) return false
        val normalized = summary.trim()
        if (normalized.isEmpty()) return false
        store.merge(requestIdentity.id, emptyList(), normalized)
        return true
    }

    fun deleteConversation(conversationId: String) {
        store.delete(conversationId)
    }

    fun deleteAll() {
        store.deleteAll()
    }
}
