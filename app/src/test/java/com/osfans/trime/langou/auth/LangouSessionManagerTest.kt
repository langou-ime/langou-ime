/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.auth

import com.osfans.trime.langou.network.TokenPair
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

class LangouSessionManagerTest :
    StringSpec({
        "creates a guest then rotates its refresh token before expiry" {
            var now = 1_000L
            val api = RecordingSessionApi()
            val store = MemorySessionStore()
            val manager =
                LangouSessionManager(
                    api = api,
                    store = store,
                    deviceIdFactory = { "dev_generated" },
                    epochSeconds = { now },
                )

            val first = runBlocking { manager.validSession() }
            first.deviceId shouldBe "dev_generated"
            first.tokens.accessToken shouldBe "access-1"
            api.guestCalls shouldBe 1

            now = 1_041L
            val rotated = runBlocking { manager.validSession() }
            rotated.tokens.accessToken shouldBe "access-2"
            rotated.tokens.refreshToken shouldBe "refresh-2"
            api.refreshCalls shouldBe 1
            store.session shouldBe rotated
        }

        "atomically replaces a guest with a verified user session" {
            val store = MemorySessionStore()
            val manager =
                LangouSessionManager(
                    api = RecordingSessionApi(),
                    store = store,
                    deviceIdFactory = { "dev_generated" },
                    epochSeconds = { 2_000L },
                )
            runBlocking { manager.validSession() }
            val userTokens =
                TokenPair(
                    accessToken = "user-access",
                    refreshToken = "user-refresh",
                    tokenType = "bearer",
                    expiresIn = 900,
                    subjectType = "user",
                )

            val user = runBlocking { manager.replaceSession(userTokens) }

            user.deviceId shouldBe "dev_generated"
            user.expiresAtEpochSeconds shouldBe 2_900L
            manager.storedSession() shouldBe user
        }
    })

private class RecordingSessionApi : SessionApi {
    var guestCalls = 0
    var refreshCalls = 0

    override suspend fun createGuestSession(
        deviceId: String,
        platform: String,
        appVersion: String,
    ): TokenPair {
        guestCalls += 1
        return tokenPair("1")
    }

    override suspend fun refreshSession(refreshToken: String): TokenPair {
        refreshCalls += 1
        refreshToken shouldBe "refresh-1"
        return tokenPair("2")
    }

    private fun tokenPair(suffix: String) =
        TokenPair(
            accessToken = "access-$suffix",
            refreshToken = "refresh-$suffix",
            tokenType = "bearer",
            expiresIn = 100,
            subjectType = "guest",
        )
}

private class MemorySessionStore : SessionStore {
    var session: StoredSession? = null

    override fun save(session: StoredSession) {
        this.session = session
    }

    override fun load(): StoredSession? = session

    override fun clear() {
        session = null
    }
}
