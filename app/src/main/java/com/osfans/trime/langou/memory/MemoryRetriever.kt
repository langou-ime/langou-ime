/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.memory

import java.util.Locale

class MemoryRetriever(
    private val maxTurns: Int = 12,
    private val maxCharacters: Int = 2_000,
) {
    init {
        require(maxTurns > 0)
        require(maxCharacters > 0)
    }

    fun retrieve(
        memory: ConversationMemory,
        latestText: String,
    ): RetrievedMemory {
        val recentCount = maxOf(1, maxTurns - maxOf(1, maxTurns / 3))
        val recent = memory.turns.takeLast(recentCount)
        val recentSet = recent.toSet()
        val queryTokens = tokens(latestText)
        val relevant =
            memory.turns
                .asSequence()
                .filterNot(recentSet::contains)
                .map { turn -> turn to tokens(turn.text).count(queryTokens::contains) }
                .filter { (_, score) -> score > 0 }
                .sortedWith(
                    compareByDescending<Pair<StoredTurn, Int>> { it.second }
                        .thenByDescending { it.first.capturedAtEpochMillis },
                ).take(maxTurns - recent.size)
                .map(Pair<StoredTurn, Int>::first)
                .toList()
        val selected =
            (relevant + recent)
                .distinct()
                .sortedBy(StoredTurn::capturedAtEpochMillis)
                .takeLast(maxTurns)
        var remaining = maxCharacters
        val bounded =
            selected
                .asReversed()
                .mapNotNull { turn ->
                    if (remaining <= 0) return@mapNotNull null
                    val text = turn.text.takeLast(remaining)
                    remaining -= text.length
                    turn.copy(text = text)
                }.asReversed()
        return RetrievedMemory(
            summary = memory.summary.take(maxCharacters),
            turns = bounded,
        )
    }

    private fun tokens(value: String): Set<String> {
        val normalized = value.lowercase(Locale.ROOT)
        val characterPairs = normalized.windowed(size = 2, step = 1).filterNot(String::isBlank)
        val words = WORD.findAll(normalized).map(MatchResult::value)
        return (characterPairs.asSequence() + words).toSet()
    }

    private companion object {
        val WORD = Regex("[a-z0-9_]{2,}")
    }
}
