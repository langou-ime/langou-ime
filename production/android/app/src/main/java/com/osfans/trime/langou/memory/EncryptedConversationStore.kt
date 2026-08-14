/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.memory

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EncryptedConversationStore(
    private val directory: File,
    private val cipher: MemoryCipher,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    init {
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create encrypted conversation memory directory"
        }
    }

    @Synchronized
    fun read(conversationId: String): ConversationMemory? {
        val file = fileFor(conversationId)
        if (!file.isFile) return null
        val decoded =
            runCatching {
                json.decodeFromString<ConversationMemory>(
                    cipher.decrypt(file.readBytes()).decodeToString(),
                )
            }.getOrNull() ?: return null
        val pruned = decoded.pruned(nowEpochMillis())
        if (pruned != decoded) write(pruned)
        return pruned
    }

    @Synchronized
    fun merge(
        conversationId: String,
        newTurns: List<StoredTurn>,
        summary: String? = null,
    ): ConversationMemory {
        val now = nowEpochMillis()
        val existing = read(conversationId)
        val mergedTurns =
            (existing?.turns.orEmpty() + newTurns)
                .asSequence()
                .filter { it.role == "self" || it.role == "other" }
                .filter { it.text.isNotBlank() }
                .filter { now - it.capturedAtEpochMillis < RETENTION_MILLIS }
                .fold(mutableListOf<StoredTurn>()) { result, turn ->
                    val normalized = turn.copy(text = turn.text.trim().take(MAX_TURN_CHARACTERS))
                    if (result.lastOrNull()?.let { it.role == normalized.role && it.text == normalized.text } != true) {
                        result += normalized
                    }
                    result
                }.takeLast(MAX_TURNS)
        return ConversationMemory(
            conversationId = conversationId,
            turns = mergedTurns,
            summary = (summary ?: existing?.summary).orEmpty().trim().take(MAX_SUMMARY_CHARACTERS),
            updatedAtEpochMillis = now,
        ).also(::write)
    }

    @Synchronized
    fun delete(conversationId: String) {
        val file = fileFor(conversationId)
        if (file.exists()) check(file.delete()) { "Unable to delete conversation memory" }
    }

    @Synchronized
    fun deleteAll() {
        directory.listFiles { file -> file.extension == FILE_EXTENSION }.orEmpty().forEach { file ->
            check(file.delete()) { "Unable to delete conversation memory" }
        }
    }

    @Synchronized
    fun listIds(): List<String> =
        directory
            .listFiles { file -> file.extension == FILE_EXTENSION }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .sorted()

    private fun write(memory: ConversationMemory) {
        val destination = fileFor(memory.conversationId)
        val temporary = File.createTempFile(destination.name, ".tmp", directory)
        try {
            val encrypted = cipher.encrypt(json.encodeToString(memory).encodeToByteArray())
            FileOutputStream(temporary).use { output ->
                output.write(encrypted)
                output.fd.sync()
            }
            runCatching {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            temporary.delete()
        }
    }

    private fun fileFor(conversationId: String): File {
        require(CONVERSATION_ID.matches(conversationId)) { "Invalid conversation id" }
        return File(directory, "$conversationId.$FILE_EXTENSION")
    }

    private fun ConversationMemory.pruned(now: Long): ConversationMemory {
        val retained =
            turns
                .filter { now - it.capturedAtEpochMillis < RETENTION_MILLIS }
                .takeLast(MAX_TURNS)
        return if (retained == turns) this else copy(turns = retained, updatedAtEpochMillis = now)
    }

    private companion object {
        const val FILE_EXTENSION = "memory"
        const val MAX_TURNS = 100
        const val MAX_TURN_CHARACTERS = 2_000
        const val MAX_SUMMARY_CHARACTERS = 4_000
        const val RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
        val CONVERSATION_ID = Regex("^[A-Za-z0-9_-]{8,64}$")
    }
}
