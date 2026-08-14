package com.osfans.trime.langou.auth

import com.osfans.trime.langou.network.TokenPair
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SessionApi {
    suspend fun createGuestSession(
        deviceId: String,
        platform: String,
        appVersion: String,
    ): TokenPair

    suspend fun refreshSession(refreshToken: String): TokenPair
}

class LangouSessionManager(
    private val api: SessionApi,
    private val store: SessionStore,
    private val deviceIdFactory: () -> String,
    private val epochSeconds: () -> Long,
    private val appVersion: String = "1.0.0",
) {
    private val mutex = Mutex()

    suspend fun validSession(): StoredSession =
        mutex.withLock {
            val existing = store.load()
            if (existing == null) {
                return@withLock createGuest()
            }
            if (existing.expiresAtEpochSeconds > epochSeconds() + REFRESH_LEEWAY_SECONDS) {
                return@withLock existing
            }

            val rotated = api.refreshSession(existing.tokens.refreshToken)
            persist(existing.deviceId, rotated)
        }

    suspend fun replaceSession(tokens: TokenPair): StoredSession =
        mutex.withLock {
            require(tokens.subjectType == "user") {
                "Only a verified user session may replace the current session"
            }
            val deviceId = store.load()?.deviceId ?: deviceIdFactory()
            persist(deviceId, tokens)
        }

    fun storedSession(): StoredSession? = store.load()

    fun clear() {
        store.clear()
    }

    private suspend fun createGuest(): StoredSession {
        val deviceId = deviceIdFactory()
        val tokens =
            api.createGuestSession(
                deviceId = deviceId,
                platform = "android",
                appVersion = appVersion,
            )
        return persist(deviceId, tokens)
    }

    private fun persist(
        deviceId: String,
        tokens: TokenPair,
    ): StoredSession =
        StoredSession(
            deviceId = deviceId,
            tokens = tokens,
            expiresAtEpochSeconds = epochSeconds() + tokens.expiresIn,
        ).also(store::save)

    private companion object {
        const val REFRESH_LEEWAY_SECONDS = 60
    }
}
