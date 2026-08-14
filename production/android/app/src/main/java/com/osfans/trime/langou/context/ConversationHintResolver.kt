/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import com.osfans.trime.langou.memory.IdentityConfidence
import java.util.Locale

data class ConversationHint(
    val text: String?,
    val confidence: IdentityConfidence,
)

object ConversationHintResolver {
    private val ignoredLabels =
        setOf(
            "微信",
            "wechat",
            "qq",
            "企业微信",
            "钉钉",
            "飞书",
            "whatsapp",
            "telegram",
            "discord",
            "返回",
            "更多",
            "搜索",
            "聊天信息",
            "语音通话",
            "视频通话",
        )
    private val timeOrCount = Regex("^(?:\\d{1,2}:\\d{2}|\\d+|\\(\\d+\\))$")

    fun resolve(
        items: List<VisibleText>,
        screenHeight: Int,
    ): ConversationHint {
        if (screenHeight <= 0) return ConversationHint(null, IdentityConfidence.Low)
        val title =
            items
                .asSequence()
                .filterNot(VisibleText::editable)
                .filterNot(VisibleText::password)
                .filter { it.centerY in 1 until (screenHeight * MAX_TITLE_HEIGHT_RATIO).toInt() }
                .filter {
                    it.centerX in
                        (it.screenWidth * MIN_TITLE_CENTER_RATIO).toInt()..
                        (it.screenWidth * MAX_TITLE_CENTER_RATIO).toInt()
                }.map { it.text.trim().replace(WHITESPACE, " ") }
                .filter { it.length in 1..MAX_TITLE_CHARACTERS }
                .filterNot { it.lowercase(Locale.ROOT) in ignoredLabels }
                .filterNot(timeOrCount::matches)
                .minByOrNull(String::length)
        return if (title == null) {
            ConversationHint(null, IdentityConfidence.Low)
        } else {
            ConversationHint(title, IdentityConfidence.High)
        }
    }

    private const val MAX_TITLE_HEIGHT_RATIO = 0.24
    private const val MIN_TITLE_CENTER_RATIO = 0.20
    private const val MAX_TITLE_CENTER_RATIO = 0.80
    private const val MAX_TITLE_CHARACTERS = 64
    private val WHITESPACE = Regex("\\s+")
}
