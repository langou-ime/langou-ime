/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.release

import com.osfans.trime.BuildConfig
import com.osfans.trime.util.Const
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class BrandIdentityTest :
    StringSpec({
        "publishes only Langou-owned product links" {
            BuildConfig.BUILD_GIT_REPO shouldBe "https://github.com/langou-ime/android"
            Const.PRIVACY_POLICY_URL shouldBe
                "https://github.com/langou-ime/android/blob/main/PRIVACY.md"
        }

        "does not invite users into upstream Trime communities" {
            val repositoryRoot = locateAndroidRepositoryRoot()
            val about =
                File(
                    repositoryRoot,
                    "app/src/main/java/com/osfans/trime/ui/main/AboutFragment.kt",
                ).readText()
            val defaultStrings =
                File(repositoryRoot, "app/src/main/res/values/strings.xml").readText()
            val simplifiedChinese =
                File(repositoryRoot, "app/src/main/res/values-zh-rCN/strings.xml").readText()

            about shouldNotContain "QQ_GROUP"
            about shouldNotContain "TELEGRAM"
            defaultStrings shouldNotContain "Trime QQ"
            defaultStrings shouldNotContain "Currently Trime"
            simplifiedChinese shouldNotContain "同文 QQ"
            simplifiedChinese shouldNotContain "当前同文"
        }

        "production build does not publish raw input events to logcat" {
            val repositoryRoot = locateAndroidRepositoryRoot()
            val application =
                File(
                    repositoryRoot,
                    "app/src/main/java/com/osfans/trime/TrimeApplication.kt",
                ).readText()
            val activity =
                File(
                    repositoryRoot,
                    "app/src/main/java/com/osfans/trime/ui/main/MainActivity.kt",
                ).readText()

            application shouldNotContain "Log.println"
            activity shouldNotContain "menu.item(R.string.developer)"
        }
    })

private fun locateAndroidRepositoryRoot(): File {
    val workingDirectory = System.getProperty("user.dir") ?: error("user.dir is unavailable")
    var directory = File(workingDirectory).absoluteFile
    while (!File(directory, "app/src/main").isDirectory) {
        directory = directory.parentFile ?: error("Android repository root was not found")
    }
    return directory
}
