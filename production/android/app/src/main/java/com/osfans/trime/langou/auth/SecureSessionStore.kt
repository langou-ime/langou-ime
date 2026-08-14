package com.osfans.trime.langou.auth

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.osfans.trime.langou.network.TokenPair
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredSession(
    val deviceId: String,
    val tokens: TokenPair,
    val expiresAtEpochSeconds: Long = 0,
)

interface SecretCipher {
    fun encrypt(value: String): String

    fun decrypt(value: String): String
}

interface SessionStore {
    fun save(session: StoredSession)

    fun load(): StoredSession?

    fun clear()
}

interface KeyValueStore {
    fun get(key: String): String?

    fun put(
        key: String,
        value: String,
    )

    fun remove(vararg keys: String)
}

class SecureSessionStore(
    private val store: KeyValueStore,
    private val cipher: SecretCipher,
) : SessionStore {
    override fun save(session: StoredSession) {
        store.put(DEVICE_ID, session.deviceId)
        store.put(ACCESS_TOKEN, cipher.encrypt(session.tokens.accessToken))
        store.put(REFRESH_TOKEN, cipher.encrypt(session.tokens.refreshToken))
        store.put(TOKEN_TYPE, session.tokens.tokenType)
        store.put(EXPIRES_IN, session.tokens.expiresIn.toString())
        store.put(SUBJECT_TYPE, session.tokens.subjectType)
        store.put(EXPIRES_AT, session.expiresAtEpochSeconds.toString())
    }

    override fun load(): StoredSession? {
        val deviceId = store.get(DEVICE_ID) ?: return null
        val accessToken = store.get(ACCESS_TOKEN)?.let(cipher::decrypt) ?: return null
        val refreshToken = store.get(REFRESH_TOKEN)?.let(cipher::decrypt) ?: return null
        val tokenType = store.get(TOKEN_TYPE) ?: return null
        val expiresIn = store.get(EXPIRES_IN)?.toLongOrNull() ?: return null
        val subjectType = store.get(SUBJECT_TYPE) ?: return null
        val expiresAt = store.get(EXPIRES_AT)?.toLongOrNull() ?: 0
        return StoredSession(
            deviceId = deviceId,
            tokens =
                TokenPair(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    tokenType = tokenType,
                    expiresIn = expiresIn,
                    subjectType = subjectType,
                ),
            expiresAtEpochSeconds = expiresAt,
        )
    }

    override fun clear() {
        store.remove(
            DEVICE_ID,
            ACCESS_TOKEN,
            REFRESH_TOKEN,
            TOKEN_TYPE,
            EXPIRES_IN,
            SUBJECT_TYPE,
            EXPIRES_AT,
        )
    }

    private companion object {
        const val DEVICE_ID = "device_id"
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val TOKEN_TYPE = "token_type"
        const val EXPIRES_IN = "expires_in"
        const val SUBJECT_TYPE = "subject_type"
        const val EXPIRES_AT = "expires_at"
    }
}

class SharedPreferencesKeyValueStore(
    private val preferences: SharedPreferences,
) : KeyValueStore {
    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(
        key: String,
        value: String,
    ) {
        check(preferences.edit().putString(key, value).commit()) {
            "Unable to persist secure session"
        }
    }

    override fun remove(vararg keys: String) {
        val editor = preferences.edit()
        keys.forEach(editor::remove)
        check(editor.commit()) {
            "Unable to clear secure session"
        }
    }
}

class AndroidKeystoreCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SecretCipher {
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext =
            Base64.encodeToString(
                cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            )
        return "$iv.$ciphertext"
    }

    override fun decrypt(value: String): String {
        val parts = value.split('.', limit = 2)
        require(parts.size == 2) { "Invalid encrypted session value" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        return cipher
            .doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
            .toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore =
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
                load(null)
            }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(
                            keyAlias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
    }

    private companion object {
        const val DEFAULT_KEY_ALIAS = "tech.langou.ime.session.v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
