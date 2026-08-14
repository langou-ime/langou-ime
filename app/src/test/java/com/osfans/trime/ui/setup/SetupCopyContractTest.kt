/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class SetupCopyContractTest :
    StringSpec({
        "all shipped locales explain that setup minimizes taps and AI needs authorized chat context" {
            val zhCn = File("src/main/res/values-zh-rCN/strings.xml").readText()
            val zhTw = File("src/main/res/values-zh-rTW/strings.xml").readText()
            val en = File("src/main/res/values/strings.xml").readText()

            zhCn shouldContain "点击一次开始设置"
            zhCn shouldContain "AI 回复已默认开启"
            zhCn shouldContain "未授权时不会根据草稿猜测"

            zhTw shouldContain "點一下開始設定"
            zhTw shouldContain "AI 回覆已預設開啟"
            zhTw shouldContain "未授權時不會根據草稿猜測"

            en shouldContain "Tap once to begin"
            en shouldContain "AI replies are on by default"
            en shouldContain "never guesses from your draft without permission"
        }
    })
