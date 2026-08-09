/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.release

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

private fun locateRepositoryRoot(): File {
    val workingDirectory = System.getProperty("user.dir") ?: error("user.dir is unavailable")
    var directory = File(workingDirectory).absoluteFile
    while (!File(directory, ".github/workflows").isDirectory) {
        directory = directory.parentFile ?: error("Android repository root was not found")
    }
    return directory
}

class AndroidReleaseWorkflowContractTest :
    StringSpec({
        val repositoryRoot = locateRepositoryRoot()

        "internal CI creates clearly non-production artifacts" {
            val workflow = File(repositoryRoot, ".github/workflows/langou-android-ci.yml").readText()
            workflow shouldContain "DEBUG-SIGNED-INTERNAL"
            workflow shouldContain "./gradlew testDebugUnitTest assembleDebug"
            workflow shouldNotContain "release-action"
        }

        "internal CI executes build logic regression tests" {
            val workflow = File(repositoryRoot, ".github/workflows/langou-android-ci.yml").readText()

            workflow shouldContain "./gradlew -p build-logic :convention:test"
        }

        "internal CI resolves apksigner from the pinned Android build tools" {
            val workflow = File(repositoryRoot, ".github/workflows/langou-android-ci.yml").readText()

            workflow shouldContain "ANDROID_BUILD_TOOLS_VERSION: \"36.0.0\""
            workflow shouldContain "build-tools;${'$'}ANDROID_BUILD_TOOLS_VERSION"
            workflow shouldContain
                "\"${'$'}ANDROID_HOME/build-tools/${'$'}ANDROID_BUILD_TOOLS_VERSION/apksigner\" verify"
            workflow shouldNotContain "run: apksigner verify"
        }

        "signed build requires all signing material without publishing by itself" {
            val workflow = File(repositoryRoot, ".github/workflows/langou-android-release.yml").readText()
            workflow shouldContain "ANDROID_KEYSTORE_BASE64"
            workflow shouldContain "ANDROID_KEYSTORE_PASSWORD"
            workflow shouldContain "ANDROID_KEY_ALIAS"
            workflow shouldContain "ANDROID_KEY_PASSWORD"
            workflow shouldContain
                "LANGOU_RELEASE_PUBLIC_KEY_BASE64: \"1uFuGlWZWeHpckhp2MTF6+5yCGIZYgBd5ghWEVQjx/k=\""
            workflow shouldContain "./gradlew testReleaseUnitTest lintRelease assembleRelease"
            workflow shouldContain "SHA256SUMS"
            workflow shouldContain "langou-ime-android-v1.0.0.apk"
            workflow shouldContain
                "sha256sum langou-ime-android-v1.0.0.apk > SHA256SUMS"
            workflow shouldContain "contents: read"
            workflow shouldNotContain "SIGNING_KEY"
            workflow shouldNotContain "Build Trime"
            workflow shouldNotContain "gh release"
            workflow shouldNotContain "contents: write"
        }

        "lint report models wait for generated assets" {
            val buildScript = File(repositoryRoot, "app/build.gradle.kts").readText()
            buildScript shouldContain
                """it.name.startsWith("generate") && it.name.endsWith("LintReportModel")"""
            buildScript shouldContain """it.name.startsWith("lintAnalyze")"""
            buildScript shouldContain """dependsOn("generateDataChecksums")"""
        }

        "CI and release workflows pin every third party action by commit" {
            val workflows =
                listOf(
                    File(repositoryRoot, ".github/workflows/langou-android-ci.yml"),
                    File(repositoryRoot, ".github/workflows/langou-android-release.yml"),
                ).joinToString("\n") { it.readText() }

            workflows shouldContain
                "actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803"
            workflows shouldContain
                "actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95"
            workflows shouldContain
                "android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699"
            workflows shouldContain
                "actions/upload-artifact@b7c566a772e6b6bfb58ed0dc250532a479d7789f"
            workflows shouldNotContain "actions/checkout@v"
            workflows shouldNotContain "actions/setup-java@v"
            workflows shouldNotContain "android-actions/setup-android@v"
            workflows shouldNotContain "actions/upload-artifact@v"
        }

        "CI runs input method instrumentation on the required Android API matrix" {
            val workflow = File(repositoryRoot, ".github/workflows/langou-android-ci.yml").readText()

            workflow shouldContain "api-level: [26, 29, 30, 34, 36]"
            workflow shouldContain
                "reactivecircus/android-emulator-runner@0a638108440efd5c7f980e6ba145dbcdd8f32009"
            workflow shouldContain "./gradlew connectedDebugAndroidTest"

            val smokeTest =
                File(
                    repositoryRoot,
                    "app/src/androidTest/java/com/osfans/trime/langou/InputMethodSmokeTest.kt",
                )
            smokeTest.isFile shouldBe true
            smokeTest.readText() shouldContain "ime enable"
            smokeTest.readText() shouldContain "DEFAULT_INPUT_METHOD"
        }

        "instrumentation host activity belongs to the debug target APK" {
            val hostActivity =
                File(
                    repositoryRoot,
                    "app/src/debug/java/com/osfans/trime/langou/ImeHostActivity.kt",
                )
            val debugManifest = File(repositoryRoot, "app/src/debug/AndroidManifest.xml")
            val testManifest = File(repositoryRoot, "app/src/androidTest/AndroidManifest.xml").readText()

            hostActivity.isFile shouldBe true
            debugManifest.readText() shouldContain "ImeHostActivity"
            testManifest shouldNotContain "ImeHostActivity"
        }
    })
