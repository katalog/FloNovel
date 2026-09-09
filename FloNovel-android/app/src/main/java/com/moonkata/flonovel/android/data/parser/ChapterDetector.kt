package com.moonkata.flonovel.android.data.parser

import com.moonkata.flonovel.android.model.Chapter

/**
 * Chapter-detection patterns, split by how much we trust them.
 *
 * [trusted] are the built-in presets. The `##` marker is written by the
 * preprocessor (go-text-pretty / the Desktop app), not typed by a person, so the
 * decision "this line is a heading" was already made upstream and the reader has
 * no reason to second-guess it by length.
 *
 * [userDefined] are arbitrary regexes the user entered. A careless one (`.*`)
 * would turn every line of the book into a chapter, so the line-length guard
 * still applies to those.
 */
data class ChapterPatterns(
    val trusted: List<Regex> = emptyList(),
    val userDefined: List<Regex> = emptyList(),
) {
    val isEmpty: Boolean get() = trusted.isEmpty() && userDefined.isEmpty()
}

object ChapterDetector {

    /**
     * Maximum heading length for [ChapterPatterns.userDefined] patterns.
     *
     * This guard used to apply to every pattern, the `##` preset included, which
     * threw away 19.6% of the chapters in the real library — 24,700 of 126,008
     * `## ` lines are longer than 60 characters and one file lost every chapter
     * it had. Machine-translated novels routinely carry long or malformed
     * headings, so length is a poor proxy for "not a heading" once the marker
     * itself is machine-generated.
     */
    const val USER_PATTERN_MAX_LINE_LENGTH = 60

    /** Zero matches is a normal ("no table of contents") state, not an error. */
    fun detect(text: String, patterns: ChapterPatterns): List<Chapter> {
        if (patterns.isEmpty) return emptyList()
        val chapters = mutableListOf<Chapter>()
        val n = text.length
        var start = 0
        var i = 0
        while (i <= n) {
            if (i == n || text[i] == '\n') {
                var end = i
                if (end > start && text[end - 1] == '\r') end -= 1
                val line = text.substring(start, end).trim()
                if (line.isNotEmpty() && isHeading(line, patterns)) {
                    chapters += Chapter(line, start)
                }
                start = i + 1
            }
            i++
        }
        return chapters
    }

    private fun isHeading(line: String, patterns: ChapterPatterns): Boolean {
        // Trusted presets: no length guard — see USER_PATTERN_MAX_LINE_LENGTH.
        if (patterns.trusted.any { it.matches(line) }) return true

        // User regexes: keep the guard so a runaway pattern cannot flood the TOC.
        if (line.length > USER_PATTERN_MAX_LINE_LENGTH) return false
        return patterns.userDefined.any { it.matches(line) }
    }
}
