package com.moonkata.flonovel.desktop.reader

import com.moonkata.flonovel.desktop.text.Chapter

data class ChapterProgress(val title: String?, val fraction: Float)

/**
 * Truncates a chapter title to [maxCodePoints] while preserving any leading "## " marker.
 *
 * Ellipsis counts towards [maxCodePoints], so an overly long title ends up with
 * [maxCodePoints] code points in total (excluding the "## " prefix).
 */
fun compactChapterTitle(title: String, maxCodePoints: Int = 20): String {
    val trimmed = title.trim()
    val prefix = if (trimmed.startsWith("##")) "## " else ""
    val heading = if (prefix.isNotEmpty()) trimmed.removePrefix("##").trimStart() else trimmed
    val length = heading.codePointCount(0, heading.length)
    if (length <= maxCodePoints) return prefix + heading
    val take = (maxCodePoints - 1).coerceAtLeast(0)
    return prefix + heading.substring(0, heading.offsetByCodePoints(0, take)) + "…"
}

/**
 * Calculates reading progress within the active chapter at [currentOffset].
 *
 * Returns null if [chapters] is empty or [textLength] is not positive.
 * If [currentOffset] is prior to the first chapter, returns a [ChapterProgress]
 * with a null [ChapterProgress.title] representing the introductory segment.
 */
fun chapterProgress(chapters: List<Chapter>, currentOffset: Int, textLength: Int): ChapterProgress? {
    if (chapters.isEmpty() || textLength <= 0) return null
    val offset = currentOffset.coerceIn(0, textLength)
    val index = chapters.binarySearch { it.charOffset.compareTo(offset) }
        .let { if (it >= 0) it else -it - 2 }
    val start = chapters.getOrNull(index)?.charOffset ?: 0
    val end = chapters.getOrNull(index + 1)?.charOffset ?: textLength
    val fraction = if (end > start) (offset - start).toFloat() / (end - start) else 0f
    return ChapterProgress(chapters.getOrNull(index)?.title, fraction.coerceIn(0f, 1f))
}
