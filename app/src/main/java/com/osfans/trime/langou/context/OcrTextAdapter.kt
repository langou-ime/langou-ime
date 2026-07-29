package com.osfans.trime.langou.context

data class OcrLine(
    val text: String,
    val confidence: Float,
    val centerX: Int,
)

object OcrTextAdapter {
    fun toVisibleText(
        lines: List<OcrLine>,
        screenWidth: Int,
    ): List<VisibleText> =
        lines
            .asSequence()
            .filter { it.confidence >= MIN_CONFIDENCE }
            .filter { it.text.isNotBlank() }
            .map {
                VisibleText(
                    text = it.text,
                    centerX = it.centerX,
                    screenWidth = screenWidth,
                )
            }.toList()

    private const val MIN_CONFIDENCE = 0.60f
}
