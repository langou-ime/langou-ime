package com.osfans.trime.langou.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatContextSnapshot(
    val packageName: String,
    val application: String,
    val turns: List<ChatTurn>,
    val capturedAtEpochMillis: Long,
)

object ContextSnapshotStore {
    private val current = MutableStateFlow<ChatContextSnapshot?>(null)
    val snapshots: StateFlow<ChatContextSnapshot?> = current.asStateFlow()

    fun update(snapshot: ChatContextSnapshot) {
        current.value = snapshot
    }

    fun get(
        packageName: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ChatContextSnapshot? {
        val snapshot = current.value ?: return null
        if (snapshot.packageName != packageName) return null
        if (nowEpochMillis - snapshot.capturedAtEpochMillis > MAX_AGE_MILLIS) {
            current.compareAndSet(snapshot, null)
            return null
        }
        return snapshot
    }

    fun clear() {
        current.value = null
    }

    private const val MAX_AGE_MILLIS = 60_000L
}
