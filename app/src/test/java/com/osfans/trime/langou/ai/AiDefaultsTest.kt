/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.ai

import com.osfans.trime.langou.network.ClientSettings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class AiDefaultsTest :
    StringSpec({
        "enables AI by default and explains that context permission is required" {
            ClientSettings().autoSuggest shouldBe true

            val chinese = File("src/main/res/values-zh-rCN/strings.xml").readText()
            chinese shouldContain "AI 回复已默认开启"
            chinese shouldContain "开启聊天理解"
            chinese shouldContain "未授权时不会根据草稿猜测"
        }
    })
