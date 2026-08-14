/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.account

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PhoneNormalizerTest :
    StringSpec({
        "normalizes mainland mobile numbers to E164" {
            PhoneNormalizer.toE164("138 0013 8000") shouldBe "+8613800138000"
            PhoneNormalizer.toE164("+86 13800138000") shouldBe "+8613800138000"
        }

        "rejects malformed phone numbers and SMS codes" {
            PhoneNormalizer.toE164("12345") shouldBe null
            PhoneNormalizer.toE164("+0013800138000") shouldBe null
            PhoneNormalizer.validCode("123456") shouldBe true
            PhoneNormalizer.validCode("12 3456") shouldBe false
        }
    })
