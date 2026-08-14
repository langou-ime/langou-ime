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
            val traditionalChinese = File("src/main/res/values-zh-rTW/strings.xml").readText()
            chinese shouldContain "AI 回复已默认开启"
            chinese shouldContain "开启聊天理解"
            chinese shouldContain "未授权时不会根据草稿猜测"
            chinese shouldContain "正在理解当前聊天"
            chinese shouldContain "点我重试"

            traditionalChinese shouldContain "AI 回覆已預設開啟"
            traditionalChinese shouldContain "開啟聊天理解"
            traditionalChinese shouldContain "未授權時不會根據草稿猜測"
            traditionalChinese shouldContain "正在理解目前聊天"
            traditionalChinese shouldContain "點我重試"
        }
    })
