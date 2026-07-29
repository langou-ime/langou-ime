/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.update

import com.osfans.trime.langou.network.ReleaseManifest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.Base64

class ReleaseSignatureVerifierTest :
    StringSpec({
        "verifies the RFC 8032 Ed25519 test vector on Android-compatible code" {
            val publicKey =
                Base64.getEncoder().encodeToString(
                    hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
                )
            val signature =
                Base64.getEncoder().encodeToString(
                    hex(
                        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
                            "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
                    ),
                )

            ReleaseSignatureVerifier.verifyBytes(publicKey, byteArrayOf(), signature) shouldBe true
            ReleaseSignatureVerifier.verifyBytes(publicKey, byteArrayOf(1), signature) shouldBe false
        }

        "rejects a non-canonical signature with the Ed25519 group order added to S" {
            val publicKey =
                Base64.getEncoder().encodeToString(
                    hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
                )
            val canonicalSignature =
                hex(
                    "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
                        "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
                )
            val malleatedSignature =
                Base64.getEncoder().encodeToString(addGroupOrderToScalar(canonicalSignature))

            ReleaseSignatureVerifier.verifyBytes(
                publicKey,
                byteArrayOf(),
                malleatedSignature,
            ) shouldBe false
        }

        "matches the FastAPI canonical manifest signature byte for byte" {
            val publicKey =
                Base64.getEncoder().encodeToString(
                    hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
                )
            val manifest =
                ReleaseManifest(
                    platform = "android",
                    version = "1.2.3",
                    minimumSupportedVersion = "1.0.0",
                    mandatory = false,
                    url = "https://download.langou.tech/langou.apk",
                    size = 42_000_000,
                    sha256 = "a".repeat(64),
                    signature =
                        "ucT6cxiU2rmDd83nRyoQ0XgqjmFs3hB2Ylr2qBMC4qj7DbXcwEDQt9qX7AfofpQ" +
                            "ufdmxKnw+WkJcVIavOnDQBQ==",
                    publishedAt = "2026-07-26T12:00:00Z",
                )

            ReleaseSignatureVerifier.verify(manifest, publicKey) shouldBe true
            ReleaseSignatureVerifier.verify(manifest.copy(size = 42_000_001), publicKey) shouldBe false
        }
    })

private fun hex(value: String): ByteArray =
    value
        .chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

private fun addGroupOrderToScalar(signature: ByteArray): ByteArray {
    val ed25519GroupOrderLittleEndian =
        hex("edd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010")
    val result = signature.copyOf()
    var carry = 0
    for (index in ed25519GroupOrderLittleEndian.indices) {
        val sum =
            (result[32 + index].toInt() and 0xff) +
                (ed25519GroupOrderLittleEndian[index].toInt() and 0xff) +
                carry
        result[32 + index] = sum.toByte()
        carry = sum ushr 8
    }
    check(carry == 0)
    return result
}
