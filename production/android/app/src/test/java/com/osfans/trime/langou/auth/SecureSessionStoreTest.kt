/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.auth

import com.osfans.trime.langou.network.TokenPair
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class SecureSessionStoreTest :
    StringSpec({
        "stores only encrypted session values and clears them on logout" {
            val preferences = MemoryKeyValueStore()
            val store = SecureSessionStore(preferences, PrefixCipher())
            val session =
                StoredSession(
                    deviceId = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
                    tokens =
                        TokenPair(
                            accessToken = "plain-access",
                            refreshToken = "plain-refresh",
                            tokenType = "bearer",
                            expiresIn = 900,
                            subjectType = "guest",
                        ),
                )

            store.save(session)

            preferences.values.toString() shouldNotContain "plain-access"
            preferences.values.toString() shouldNotContain "plain-refresh"
            store.load() shouldBe session

            store.clear()
            store.load().shouldBeNull()
            preferences.values.isEmpty() shouldBe true
        }
    })

private class PrefixCipher : SecretCipher {
    override fun encrypt(value: String): String = "cipher:${value.reversed()}"

    override fun decrypt(value: String): String =
        value.removePrefix("cipher:").reversed()
}

private class MemoryKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun remove(vararg keys: String) {
        keys.forEach(values::remove)
    }
}
