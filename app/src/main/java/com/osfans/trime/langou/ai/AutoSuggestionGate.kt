package com.osfans.trime.langou.ai

import com.osfans.trime.langou.privacy.ContextSignals
import com.osfans.trime.langou.privacy.SensitiveContextPolicy
import java.security.MessageDigest

enum class SuggestionDecision {
    Generate,
    SkipDuplicate,
    SkipDraftOnly,
    SkipBlank,
    SkipOversized,
    BlockSensitive,
}

enum class SuggestionTrigger {
    ContextChange,
    DraftChange,
    ManualRefresh,
}

class AutoSuggestionGate(
    private val maxContextCharacters: Int = DEFAULT_MAX_CONTEXT_CHARACTERS,
) {
    private var lastSuccessfulFingerprint: String? = null
    private var pendingFingerprint: String? = null

    fun evaluate(
        signals: ContextSignals,
        conversationId: String,
        context: String,
        trigger: SuggestionTrigger,
    ): SuggestionDecision {
        if (!SensitiveContextPolicy.canCollect(signals)) {
            return SuggestionDecision.BlockSensitive
        }
        if (trigger == SuggestionTrigger.DraftChange) {
            return SuggestionDecision.SkipDraftOnly
        }
        val normalized = context.trim()
        if (normalized.isEmpty()) return SuggestionDecision.SkipBlank
        if (normalized.length > maxContextCharacters) return SuggestionDecision.SkipOversized

        val fingerprint = fingerprint(conversationId, normalized)
        if (
            fingerprint == pendingFingerprint ||
            (
                trigger != SuggestionTrigger.ManualRefresh &&
                    fingerprint == lastSuccessfulFingerprint
                )
        ) {
            return SuggestionDecision.SkipDuplicate
        }
        pendingFingerprint = fingerprint
        return SuggestionDecision.Generate
    }

    fun complete(
        conversationId: String,
        context: String,
        successful: Boolean,
    ) {
        val fingerprint = fingerprint(conversationId, context.trim())
        if (pendingFingerprint != fingerprint) return
        if (successful) {
            lastSuccessfulFingerprint = fingerprint
        }
        pendingFingerprint = null
    }

    private fun fingerprint(
        conversationId: String,
        normalizedContext: String,
    ): String = "$conversationId\u0000$normalizedContext".sha256()

    fun reset() {
        lastSuccessfulFingerprint = null
        pendingFingerprint = null
    }

    private fun String.sha256(): String = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DEFAULT_MAX_CONTEXT_CHARACTERS = 2_000
    }
}
