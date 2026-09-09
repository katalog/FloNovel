package com.moonkata.flonovel.desktop.text

data class Chapter(
    val title: String,
    val charOffset: Int,
) {
    /** Shortened title for UI display in TOC when a title is excessively long. */
    val displayTitle: String
        get() = if (title.length > 80) title.take(80) + "..." else title
}

object ChapterDetector {

    /**
     * Scans lines in [text] against [rules] using full-line regex matching.
     * Zero matches is a normal, valid state ("no chapters found"), never an error.
     */
    fun detect(
        text: String,
        rules: List<ChapterPatternRule> = ChapterPatterns.buildRules(),
    ): List<Chapter> {
        if (rules.isEmpty() || text.isEmpty()) return emptyList()

        val chapters = mutableListOf<Chapter>()
        val n = text.length
        var start = 0
        var i = 0
        while (i <= n) {
            if (i == n || text[i] == '\n') {
                var end = i
                if (end > start && text[end - 1] == '\r') end -= 1
                val line = text.substring(start, end).trim()
                if (line.isNotEmpty() && rules.any { it.matches(line) }) {
                    chapters += Chapter(title = line, charOffset = start)
                }
                start = i + 1
            }
            i++
        }
        return chapters
    }
}
