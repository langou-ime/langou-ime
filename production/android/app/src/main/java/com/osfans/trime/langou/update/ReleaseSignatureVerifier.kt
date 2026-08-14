/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.update

import com.osfans.trime.langou.network.ReleaseManifest
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

object ReleaseSignatureVerifier {
    private val json = Json { encodeDefaults = true }

    fun verify(
        manifest: ReleaseManifest,
        publicKeyBase64: String,
    ): Boolean =
        verifyBytes(
            publicKeyBase64 = publicKeyBase64,
            message = canonicalPayload(manifest),
            signatureBase64 = manifest.signature,
        )

    fun verifyBytes(
        publicKeyBase64: String,
        message: ByteArray,
        signatureBase64: String,
    ): Boolean =
        runCatching {
            val publicKey = Base64.getDecoder().decode(publicKeyBase64)
            val signature = Base64.getDecoder().decode(signatureBase64)
            require(publicKey.size == PUBLIC_KEY_BYTES)
            require(signature.size == SIGNATURE_BYTES)
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signature)
        }.getOrDefault(false)

    internal fun canonicalPayload(manifest: ReleaseManifest): ByteArray =
        json
            .encodeToString(
                buildJsonObject {
                    put("mandatory", manifest.mandatory)
                    put("minimum_supported_version", manifest.minimumSupportedVersion)
                    put("platform", manifest.platform)
                    put("published_at", manifest.publishedAt)
                    put("sha256", manifest.sha256)
                    put("size", manifest.size)
                    put("url", manifest.url)
                    put("version", manifest.version)
                },
            ).toByteArray(Charsets.UTF_8)

    private const val PUBLIC_KEY_BYTES = 32
    private const val SIGNATURE_BYTES = 64
}
