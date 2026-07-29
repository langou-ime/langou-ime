/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.release

import com.osfans.trime.BuildConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ReleaseIdentityTest :
    StringSpec({
        "uses the Langou v1 release identity instead of the upstream git tag" {
            val expectedApplicationId =
                if (BuildConfig.DEBUG) "tech.langou.ime.debug" else "tech.langou.ime"
            BuildConfig.APPLICATION_ID shouldBe expectedApplicationId
            BuildConfig.VERSION_NAME shouldBe "1.0.0"
            BuildConfig.BUILD_VERSION_NAME shouldBe "1.0.0"
        }
    })
