package com.osfans.trime.langou.ai

import com.osfans.trime.langou.privacy.ContextSignals
import com.osfans.trime.langou.privacy.SensitiveContextPolicy
import java.security.MessageDigest

enum class SuggestionDecision {
    Generate,
    SkipDuplicate,
    SkipBlank,
    SkipOversized,
    BlockSensitive,
}

class AutoSuggestionGate(
    private val maxContextCharacters: Int = DEFAULT_MAX_CONTEXT_CHARACTERS,
) {
    private var lastSuccessfulFingerprint: String? = null
    private var pendingFingerprint: String? = null

    fun evaluate(
        signals: ContextSignals,
        context: String,
    ): SuggestionDecision {
        if (!SensitiveContextPolicy.canCollect(signals)) {
            return SuggestionDecision.BlockSensitive
        }
        val normalized = context.trim()
        if (normalized.isEmpty()) return SuggestionDecision.SkipBlank
        if (normalized.length > maxContextCharacters) return SuggestionDecision.SkipOversized

        val fingerprint = normalized.sha256()
        if (fingerprint == lastSuccessfulFingerprint || fingerprint == pendingFingerprint) {
            return SuggestionDecision.SkipDuplicate
        }
        pendingFingerprint = fingerprint
        return SuggestionDecision.Generate
    }

    fun complete(
        context: String,
        successful: Boolean,
    ) {
        val fingerprint = context.trim().sha256()
        if (pendingFingerprint != fingerprint) return
        if (successful) {
            lastSuccessfulFingerprint = fingerprint
        }
        pendingFingerprint = null
    }

    fun reset() {
        lastSuccessfulFingerprint = null
        pendingFingerprint = null
    }

    private fun String.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DEFAULT_MAX_CONTEXT_CHARACTERS = 2_000
    }
}
