package com.moonkata.flonovel.android.ui.reader

import com.moonkata.flonovel.android.model.Chapter

data class ChapterProgress(val title: String?, val fraction: Float)

fun compactChapterTitle(title: String): String {
    val trimmed = title.trim()
    val prefix = if (trimmed.startsWith("##")) "## " else ""
    val heading = if (prefix.isNotEmpty()) trimmed.removePrefix("##").trimStart() else trimmed
    val length = heading.codePointCount(0, heading.length)
    return prefix + if (length <= 7) heading else heading.substring(0, heading.offsetByCodePoints(0, 6)) + "…"
}

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
