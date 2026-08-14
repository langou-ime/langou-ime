/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class ThemeDeployReentryContractTest :
    StringSpec({
        "a completed rime deployment reloads the theme without starting a nested native deployment" {
            val service =
                File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt").readText()
            val themeManager =
                File("src/main/java/com/osfans/trime/data/theme/ThemeManager.kt").readText()
            val deployHandler =
                service
                    .substringAfter("is RimeMessage.DeployMessage ->")
                    .substringBefore("else ->")

            deployHandler shouldContain "ThemeManager.reloadSelectedThemeAfterDeployment()"
            deployHandler shouldNotContain "ThemeManager.selectTheme("
            themeManager shouldContain "fun reloadSelectedThemeAfterDeployment()"
            themeManager shouldContain "resolveTheme(configId, deployConfig = false)"
        }
    })
