/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Sync

val langouReleasePublicKey =
    providers.environmentVariable("LANGOU_RELEASE_PUBLIC_KEY_BASE64").orElse("").get()

plugins {
    id("com.osfans.trime.app-convention")
    id("com.osfans.trime.native-app-convention")
    id("com.osfans.trime.data-checksums")
    id("com.osfans.trime.native-cache-hash")
    id("com.osfans.trime.opencc-data")
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.osfans.trime"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "tech.langou.ime"
        minSdk = 26
        targetSdk = 36
        versionCode = 10000
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        multiDexEnabled = true
        buildConfigField("String", "LANGOU_API_BASE_URL", "\"https://api.langou.tech/\"")
        buildConfigField(
            "String",
            "LANGOU_RELEASE_PUBLIC_KEY",
            "\"$langouReleasePublicKey\"",
        )
        buildConfigField("String", "BUILDER", "\"${project.builder}\"")
        buildConfigField("long", "BUILD_TIMESTAMP", project.buildTimestamp)
        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${project.buildCommitHash}\"")
        buildConfigField("String", "BUILD_GIT_REPO", "\"${project.buildGitRepo}\"")
        buildConfigField("String", "BUILD_VERSION_NAME", "\"${project.buildVersionName}\"")
    }

    base {
        // https://www.norio.be/blog/archivesBaseName-removed-from-gradle9.html
        archivesName = "${android.defaultConfig.applicationId}-$buildVersionName"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                project.signKeyFile?.let {
                    signingConfigs.create("release") {
                        storeFile = it
                        storePassword = project.signKeyStorePwd
                        keyAlias = project.signKeyAlias
                        keyPassword = project.signKeyPwd
                    }
                }

            resValue("string", "trime_app_name", "@string/app_name_release")
        }
        debug {
            applicationIdSuffix = ".debug"

            resValue("string", "trime_app_name", "@string/app_name_debug")
        }
        all {
            // remove META-INF/version-control-info.textproto
            @Suppress("UnstableApiUsage")
            vcsInfo.include = false
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            // https://youtrack.jetbrains.com/issue/KT-55947
            jvmTarget.set(JvmTarget.JVM_11)
            // https://youtrack.jetbrains.com/issue/KT-73255/Change-defaulting-rule-for-annotations
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    // hack workaround lint gradle 8.0.2
    lint {
        checkReleaseBuilds = false
        // Compose lint bundled with this AGP cannot read Kotlin 2.1 metadata yet.
        disable +=
            setOf(
                "FlowOperatorInvokedInComposition",
                "CoroutineCreationDuringComposition",
            )
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/*.version",
                    "/META-INF/*.kotlin_module", // cannot be excluded actually
                    "/META-INF/androidx/**",
                    "/DebugProbesKt.bin",
                    "/kotlin-tooling-metadata.json",
                )
        }
    }

    sourceSets {
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/rimeAssets"))
    }
}

aboutLibraries {
    collect {
        configPath.set(file("licenses").takeIf { it.exists() })
        fetchRemoteLicense.set(false)
        fetchRemoteFunding.set(false)
        includePlatform.set(false)
    }
    export {
        excludeFields.set(
            setOf("generated", "developers", "organization", "scm", "funding", "content"),
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val syncRimeData =
    tasks.register<Sync>("syncRimeData") {
        val sharedDirectory = "shared"
        into(layout.buildDirectory.dir("generated/rimeAssets"))
        from("../third_party/rime-prelude") {
            into(sharedDirectory)
            include("key_bindings.yaml", "punctuation.yaml", "symbols.yaml")
        }
        from("../third_party/rime-luna-pinyin") {
            into(sharedDirectory)
            include("*.yaml")
        }
        from("../third_party/rime-essay/essay.txt") {
            into(sharedDirectory)
        }
        from("../third_party/rime-emoji/emoji_suggestion.yaml") {
            into(sharedDirectory)
        }
        from("../third_party/rime-emoji/opencc") {
            into("$sharedDirectory/opencc")
        }
        from("src/main/rime") {
            into(sharedDirectory)
        }
        from("../third_party/rime-prelude/LICENSE") {
            into("$sharedDirectory/licenses")
            rename { "rime-prelude-LICENSE" }
        }
        from("../third_party/rime-luna-pinyin/LICENSE") {
            into("$sharedDirectory/licenses")
            rename { "rime-luna-pinyin-LICENSE" }
        }
        from("../third_party/rime-essay/LICENSE") {
            into("$sharedDirectory/licenses")
            rename { "rime-essay-LICENSE" }
        }
        from("../third_party/rime-emoji/LICENSE") {
            into("$sharedDirectory/licenses")
            rename { "rime-emoji-LICENSE" }
        }
    }

tasks.named("preBuild").configure {
    dependsOn(syncRimeData)
}

tasks.matching { it.name.startsWith("test") && it.name.endsWith("UnitTest") }.configureEach {
    dependsOn("generateDataChecksums")
}

val validateLangouReleaseKey =
    tasks.register("validateLangouReleaseKey") {
        doLast {
            require(langouReleasePublicKey.isNotBlank()) {
                "LANGOU_RELEASE_PUBLIC_KEY_BASE64 is required for release builds"
            }
        }
    }

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateLangouReleaseKey)
}

android.applicationVariants.all {
    val variantName = name.replaceFirstChar { it.uppercase() }
    tasks.findByName("generateDataChecksums")?.also {
        it.dependsOn(syncRimeData)
        tasks.getByName("merge${variantName}Assets").dependsOn(it)
    }
}

tasks
    .matching {
        (it.name.startsWith("generate") && it.name.endsWith("LintReportModel")) ||
            it.name.startsWith("lintAnalyze")
    }.configureEach {
        dependsOn("generateDataChecksums")
    }

dependencies {
    ksp(project(":codegen"))
    implementation(project(":ppocr-sdk"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.flexbox)
    implementation(libs.bravh)
    implementation(libs.timber)
    implementation(libs.xxpermissions)
    implementation(libs.kodein.di)
    implementation(libs.snakeyaml)
    implementation(libs.bcprov)
    implementation(libs.splitties.bitflags)
    implementation(libs.splitties.systemservices)
    implementation(libs.splitties.views.dsl)
    implementation(libs.splitties.views.dsl.constraintlayout)
    implementation(libs.splitties.views.dsl.coordinatorlayout)
    implementation(libs.splitties.views.dsl.recyclerview)
    implementation(libs.splitties.views.recyclerview)
    implementation(libs.aboutlibraries.core)
    implementation(libs.iconics.core)
    implementation(libs.community.material.typeface) {
        artifact { type = "aar" }
    }

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

configurations {
    all {
        // remove Baseline Profile Installer or whatever it is...
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
    }
}
