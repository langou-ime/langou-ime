package com.osfans.trime.langou.ai

class AiSuggestionSelection(
    private val insertText: (String) -> Unit,
) {
    var items: List<String> = emptyList()
        private set

    fun update(values: List<String>) {
        items =
            values
                .asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .take(MAX_SUGGESTIONS)
                .toList()
    }

    fun select(index: Int): Boolean {
        val value = items.getOrNull(index) ?: return false
        insertText(value)
        return true
    }

    fun selectDisplayed(value: CharSequence): Boolean {
        val displayed = value.toString().trim()
        if (displayed.isEmpty()) return false
        insertText(displayed)
        return true
    }

    private companion object {
        const val MAX_SUGGESTIONS = 3
    }
}
