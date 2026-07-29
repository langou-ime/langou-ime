/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.ai

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ChatApplicationMapperTest :
    StringSpec({
        "maps the supported chat applications and rejects unrelated editors" {
            ChatApplicationMapper.map("com.tencent.mm") shouldBe "wechat"
            ChatApplicationMapper.map("com.tencent.mobileqq") shouldBe "qq"
            ChatApplicationMapper.map("com.tencent.wework") shouldBe "wecom"
            ChatApplicationMapper.map("com.alibaba.android.rimet") shouldBe "dingtalk"
            ChatApplicationMapper.map("com.ss.android.lark") shouldBe "feishu"
            ChatApplicationMapper.map("com.whatsapp") shouldBe "whatsapp"
            ChatApplicationMapper.map("org.telegram.messenger") shouldBe "telegram"
            ChatApplicationMapper.map("com.discord") shouldBe "discord"
            ChatApplicationMapper.map("com.example.notes").shouldBe(null)
        }
    })
