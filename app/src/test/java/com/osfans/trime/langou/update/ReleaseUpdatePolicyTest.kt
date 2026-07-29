/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.update

import com.osfans.trime.langou.network.ReleaseManifest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ReleaseUpdatePolicyTest :
    StringSpec({
        "classifies current optional mandatory and malformed releases" {
            ReleaseUpdatePolicy.evaluate("1.0.0", manifest("1.0.0")) shouldBe
                ReleaseDecision.Current
            ReleaseUpdatePolicy.evaluate("1.0.0", manifest("1.1.0")) shouldBe
                ReleaseDecision.Optional
            ReleaseUpdatePolicy.evaluate(
                "1.0.0",
                manifest(version = "2.0.0", minimum = "1.5.0"),
            ) shouldBe ReleaseDecision.Mandatory
            ReleaseUpdatePolicy.evaluate("not-a-version", manifest("1.1.0")) shouldBe
                ReleaseDecision.Rejected
        }
    })

private fun manifest(
    version: String,
    minimum: String = "1.0.0",
) = ReleaseManifest(
    platform = "android",
    version = version,
    minimumSupportedVersion = minimum,
    mandatory = false,
    url = "https://download.langou.tech/langou.apk",
    size = 42,
    sha256 = "a".repeat(64),
    signature = "signature",
    publishedAt = "2026-07-26T12:00:00Z",
)
