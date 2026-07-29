/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.release

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

private fun locateNativeRepositoryRoot(): File {
    val workingDirectory = System.getProperty("user.dir") ?: error("user.dir is unavailable")
    var directory = File(workingDirectory).absoluteFile
    while (!File(directory, "app/src/main/jni/CMakeLists.txt").isFile) {
        directory = directory.parentFile ?: error("Android repository root was not found")
    }
    return directory
}

class NativeDependencySecurityContractTest :
    StringSpec({
        val repositoryRoot = locateNativeRepositoryRoot()
        val nativeRoot = File(repositoryRoot, "app/src/main/jni")

        "the runtime links the patched top-level OpenCC rather than librime's old vendored copy" {
            val cmake = File(nativeRoot, "CMakeLists.txt").readText()
            val conversion = File(nativeRoot, "OpenCC/src/Conversion.cpp").readText()
            val segmentation =
                File(nativeRoot, "OpenCC/src/MaxMatchSegmentation.cpp").readText()

            cmake shouldContain "add_subdirectory(OpenCC)"
            cmake shouldNotContain "add_subdirectory(librime/deps/opencc)"
            conversion shouldContain "remainingLength"
            segmentation shouldContain "remainingLength"
        }

        "vendored RapidJSON contains the exponent underflow fix" {
            val reader =
                File(
                    nativeRoot,
                    "OpenCC/deps/rapidjson-1.1.0/rapidjson/reader.h",
                ).readText()

            reader shouldContain "int maxExp = (expFrac + 2147483639) / 10;"
            reader shouldNotContain
                "if (exp >= 214748364) {                         // Issue #313"
        }

        "the packaged OpenCC 1.4.1 resources are complete and precompiled" {
            val resources = File(repositoryRoot, "app/src/main/opencc-1.4.1")
            val dictionaries =
                resources
                    .listFiles { file -> file.extension == "ocd2" }
                    ?.map(File::getName)
                    .orEmpty()
                    .sorted()
            val configs =
                resources
                    .listFiles { file -> file.extension == "json" && file.name != "opencc_config.schema.json" }
                    ?.sortedBy(File::getName)
                    .orEmpty()

            dictionaries.size shouldBe 22
            resources
                .listFiles { file -> file.extension == "txt" }
                .orEmpty()
                .size shouldBe 0

            val referencedDictionaries =
                configs
                    .flatMap { config ->
                        Regex("\"file\"\\s*:\\s*\"([^\"]+\\.ocd2)\"")
                            .findAll(config.readText())
                            .map { match -> match.groupValues[1] }
                            .toList()
                    }.distinct()

            dictionaries shouldContainAll referencedDictionaries
            File(resources, "PROVENANCE.md").readText() shouldContain
                "81223ed87ae53283ef518e2deac34b7971f8a39e"
        }

        "release manifest verification does not use unpatched eddsa-java" {
            val versionCatalog = File(repositoryRoot, "gradle/libs.versions.toml").readText()
            val verifier =
                File(
                    repositoryRoot,
                    "app/src/main/java/com/osfans/trime/langou/update/ReleaseSignatureVerifier.kt",
                ).readText()

            versionCatalog shouldNotContain "net.i2p.crypto:eddsa"
            versionCatalog shouldContain "org.bouncycastle:bcprov-jdk18on"
            verifier shouldNotContain "net.i2p.crypto.eddsa"
            verifier shouldContain "Ed25519Signer"
        }
    })
