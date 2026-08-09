package com.osfans.trime.langou.context

data class VisibleText(
    val text: String,
    val centerX: Int,
    val screenWidth: Int,
    val centerY: Int = Int.MAX_VALUE,
    val editable: Boolean = false,
    val password: Boolean = false,
)

data class ChatTurn(
    val role: String,
    val text: String,
)

object ChatTextSegmenter {
    private val ignoredControlLabels =
        setOf(
            "发送",
            "按住说话",
            "语音",
            "表情",
            "更多",
            "图片",
            "相册",
            "拍摄",
            "视频通话",
            "返回",
        )
    private val timeOnly =
        Regex("""^(?:今天|昨天|星期[一二三四五六日天]\s*)?\d{1,2}:\d{2}$""")

    fun segment(items: List<VisibleText>): List<ChatTurn> {
        val uniqueTurns = mutableListOf<ChatTurn>()
        val seen = linkedSetOf<String>()
        items.forEach { item ->
            val text = item.text.trim().replace(Regex("""\s+"""), " ")
            if (
                item.editable ||
                item.password ||
                text.isEmpty() ||
                text in ignoredControlLabels ||
                timeOnly.matches(text)
            ) {
                return@forEach
            }
            val boundedText = text.take(MAX_TURN_CHARACTERS)
            val dedupeKey = boundedText.lowercase()
            if (!seen.add(dedupeKey)) return@forEach
            val role =
                if (item.centerX > item.screenWidth * SELF_ALIGNMENT_THRESHOLD) {
                    "self"
                } else {
                    "other"
                }
            uniqueTurns += ChatTurn(role, boundedText)
        }

        val bounded = ArrayDeque<ChatTurn>()
        var characters = 0
        uniqueTurns.asReversed().forEach { turn ->
            if (bounded.size >= MAX_TURNS) return@forEach
            if (characters + turn.text.length > MAX_TOTAL_CHARACTERS) return@forEach
            bounded.addFirst(turn)
            characters += turn.text.length
        }
        return bounded.toList()
    }

    private const val SELF_ALIGNMENT_THRESHOLD = 0.55
    private const val MAX_TURNS = 12
    private const val MAX_TURN_CHARACTERS = 400
    private const val MAX_TOTAL_CHARACTERS = 1_600
}
