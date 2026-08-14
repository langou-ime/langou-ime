/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.privacy

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ClientRedactorTest :
    StringSpec({
        "redacts identifiers before a request leaves the device" {
            val redacted =
                ClientRedactor.redact(
                    "电话13800138000 邮箱girl@example.com " +
                        "身份证11010519491231002X 卡号6222021234567890",
                )

            redacted shouldNotContain "13800138000"
            redacted shouldNotContain "girl@example.com"
            redacted shouldNotContain "11010519491231002X"
            redacted shouldNotContain "6222021234567890"
            redacted shouldContain "[手机号]"
            redacted shouldContain "[邮箱]"
            redacted shouldContain "[身份证]"
            redacted shouldContain "[银行卡]"
        }
    })
