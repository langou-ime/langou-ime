/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import android.graphics.Bitmap
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LegacyScreenshotBrokerTest :
    StringSpec({
        "exposes capture only while the projection service is alive" {
            var requests = 0
            val provider =
                object : LegacyScreenshotProvider {
                    override fun request(callback: (Bitmap) -> Unit) {
                        requests += 1
                    }
                }

            LegacyScreenshotBroker.install(provider)
            LegacyScreenshotBroker.isAvailable() shouldBe true
            LegacyScreenshotBroker.request { } shouldBe true
            requests shouldBe 1

            LegacyScreenshotBroker.uninstall(provider)
            LegacyScreenshotBroker.isAvailable() shouldBe false
            LegacyScreenshotBroker.request { } shouldBe false
        }
    })
