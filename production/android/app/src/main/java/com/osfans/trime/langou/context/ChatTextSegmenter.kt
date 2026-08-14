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
            "已读",
            "未读",
            "置顶",
            "收藏",
            "转发",
            "撤回",
        )
    private val timeOnly =
        Regex("""^(?:今天|昨天|星期[一二三四五六日天]\s*)?\d{1,2}:\d{2}$""")
    private val statusOnly =
        Regex("""^(?:已读|未读|[\d.]+\s*(?:KB|MB)|\d+/\d+)$""")

    fun segment(items: List<VisibleText>): List<ChatTurn> {
        val normalizedItems =
            items.mapNotNull { item ->
                val text = item.text.trim().replace(Regex("""\s+"""), " ")
                if (
                    item.editable ||
                    item.password ||
                    text.isEmpty() ||
                    text in ignoredControlLabels ||
                    timeOnly.matches(text) ||
                    statusOnly.matches(text)
                ) {
                    null
                } else {
                    item.copy(text = text.take(MAX_TURN_CHARACTERS))
                }
            }.sortedWith(
                compareBy<VisibleText> { normalizedVerticalOrder(it.centerY) }
                    .thenBy(VisibleText::centerX),
            )

        val mergedItems = mutableListOf<VisibleText>()
        normalizedItems.forEach { item ->
            val previous = mergedItems.lastOrNull()
            if (
                previous != null &&
                sameRole(previous, item) &&
                previous.centerY != Int.MAX_VALUE &&
                item.centerY != Int.MAX_VALUE &&
                kotlin.math.abs(previous.centerY - item.centerY) <= MERGE_VERTICAL_DISTANCE &&
                kotlin.math.abs(previous.centerX - item.centerX) <= MERGE_HORIZONTAL_DISTANCE
            ) {
                mergedItems[mergedItems.lastIndex] =
                    previous.copy(
                        text = "${previous.text}\n${item.text}".take(MAX_TURN_CHARACTERS),
                        centerY = maxOf(previous.centerY, item.centerY),
                    )
            } else {
                mergedItems += item
            }
        }

        val uniqueTurns = mutableListOf<ChatTurn>()
        val seen = linkedSetOf<String>()
        mergedItems.forEach { item ->
            val boundedText = item.text.take(MAX_TURN_CHARACTERS)
            val dedupeKey = boundedText.lowercase()
            if (!seen.add(dedupeKey)) return@forEach
            val role = roleOf(item)
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

    private fun sameRole(
        left: VisibleText,
        right: VisibleText,
    ): Boolean = roleOf(left) == roleOf(right)

    private fun normalizedVerticalOrder(centerY: Int): Int =
        if (centerY == Int.MAX_VALUE) Int.MAX_VALUE else centerY

    private fun roleOf(item: VisibleText): String =
        if (item.centerX > item.screenWidth * SELF_ALIGNMENT_THRESHOLD) {
            "self"
        } else {
            "other"
        }

    private const val SELF_ALIGNMENT_THRESHOLD = 0.55
    private const val MAX_TURNS = 12
    private const val MAX_TURN_CHARACTERS = 400
    private const val MAX_TOTAL_CHARACTERS = 1_600
    private const val MERGE_VERTICAL_DISTANCE = 180
    private const val MERGE_HORIZONTAL_DISTANCE = 220
}
