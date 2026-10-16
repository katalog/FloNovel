package com.moonkata.flonovel.desktop.reader

import com.moonkata.flonovel.desktop.text.Chapter

/**
 * Chapter jump navigator: provides direct jumping between chapters,
 * as well as multi-part breakpoint calculations.
 */
object ChapterJumpNavigator {

    /**
     * If two chapter patterns are separated by fewer lines than this (e.g. a one- or two-line
     * notice immediately followed by the real chapter title), don't subdivide that span.
     */
    const val CLOSE_CHAPTER_LINE_THRESHOLD = 20

    /**
     * Returns the sorted unique starting character offsets of the given [chapters].
     */
    fun chapterOffsets(chapters: List<Chapter>): List<Int> {
        return chapters.map { it.charOffset }.distinct().sorted()
    }

    /**
     * Finds the next chapter offset strictly greater than [currentOffset].
     */
    fun nextChapter(chapters: List<Chapter>, currentOffset: Int): Int? {
        val offsets = chapterOffsets(chapters)
        return nextBreakpoint(offsets, currentOffset)
    }

    /**
     * Finds the previous chapter offset relative to [currentOffset]:
     * - If [currentOffset] is in the middle of a chapter (strictly greater than its start offset),
     *   returns the start offset of that current chapter.
     * - If [currentOffset] is already at or before the chapter start offset,
     *   returns the start offset of the preceding chapter.
     */
    fun previousChapter(chapters: List<Chapter>, currentOffset: Int): Int? {
        val offsets = chapterOffsets(chapters)
        return previousBreakpoint(offsets, currentOffset)
    }

    /**
     * Computes the breakpoint offsets across all chapters.
     *
     * @param chapters Detected chapters list with character offsets.
     * @param totalCharCount Total text length.
     * @param divisions Number of equal divisions per chapter (default 4).
     * @param text Full text content, used to count lines between close chapters.
     */
    fun breakpoints(
        chapters: List<Chapter>,
        totalCharCount: Int,
        divisions: Int = 4,
        text: String,
    ): List<Int> {
        if (chapters.isEmpty() || divisions < 1 || totalCharCount <= 0) return emptyList()
        val sorted = chapters.sortedBy { it.charOffset }
        val result = mutableListOf<Int>()
        for (idx in sorted.indices) {
            val start = sorted[idx].charOffset
            val hasNextChapter = idx + 1 < sorted.size
            val end = if (hasNextChapter) sorted[idx + 1].charOffset else totalCharCount
            val length = end - start
            if (length <= 0) continue
            if (hasNextChapter && lineCountBetween(text, start, end) <= CLOSE_CHAPTER_LINE_THRESHOLD) {
                result += end.coerceAtMost(totalCharCount)
                continue
            }
            for (i in 1..divisions) {
                val point = start + ((length.toLong() * i) / divisions).toInt()
                result += point.coerceAtMost(totalCharCount)
            }
        }
        return result.distinct().sorted()
    }

    /**
     * Counts newline characters in text between [start] and [end].
     */
    fun lineCountBetween(text: String, start: Int, end: Int): Int {
        var count = 0
        val safeEnd = end.coerceAtMost(text.length)
        val safeStart = start.coerceIn(0, safeEnd)
        for (i in safeStart until safeEnd) {
            if (text[i] == '\n') count++
        }
        return count
    }

    /**
     * Finds the next breakpoint strictly greater than [currentOffset].
     */
    fun nextBreakpoint(breakpoints: List<Int>, currentOffset: Int): Int? {
        if (breakpoints.isEmpty()) return null
        val idx = breakpoints.binarySearch(currentOffset)
        val nextIdx = if (idx >= 0) idx + 1 else -idx - 1
        return if (nextIdx in breakpoints.indices) breakpoints[nextIdx] else null
    }

    /**
     * Finds the previous breakpoint strictly less than [currentOffset].
     */
    fun previousBreakpoint(breakpoints: List<Int>, currentOffset: Int): Int? {
        if (breakpoints.isEmpty()) return null
        val idx = breakpoints.binarySearch(currentOffset)
        val prevIdx = if (idx >= 0) idx - 1 else -idx - 2
        return if (prevIdx in breakpoints.indices) breakpoints[prevIdx] else null
    }

    /**
     * Returns the character offset positioned [lines] lines before [targetOffset].
     *
     * Counts newline characters backwards from [targetOffset].
     * If [maxCharsBefore] is exceeded before finding [lines] lines, the search is clamped
     * to ensure the target chapter heading never gets pushed below the screen by huge paragraphs.
     */
    fun rewindLinesBefore(
        text: String,
        targetOffset: Int,
        lines: Int = 10,
        maxCharsBefore: Int = 400,
    ): Int {
        if (targetOffset <= 0 || lines <= 0) return 0
        val safeTarget = targetOffset.coerceIn(0, text.length)
        val minOffset = maxOf(0, safeTarget - maxCharsBefore)

        var pos = safeTarget - 1

        // Skip any newlines immediately preceding targetOffset so the delimiter
        // between the preceding line and targetOffset is not counted as a line of text.
        while (pos >= minOffset && text[pos] == '\n') {
            pos--
        }

        var lineCount = 0
        while (pos >= minOffset && lineCount < lines) {
            if (text[pos] == '\n') {
                lineCount++
                if (lineCount == lines) {
                    return pos + 1
                }
            }
            pos--
        }

        return maxOf(0, pos + 1)
    }
}

