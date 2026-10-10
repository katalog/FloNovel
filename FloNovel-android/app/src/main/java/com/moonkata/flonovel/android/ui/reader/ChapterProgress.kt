package com.moonkata.flonovel.android.ui.reader

import com.moonkata.flonovel.android.model.Chapter

data class ChapterProgress(val title: String?, val fraction: Float)

fun compactChapterTitle(title: String): String {
    val length = title.codePointCount(0, title.length)
    return if (length <= 7) title else title.substring(0, title.offsetByCodePoints(0, 6)) + "…"
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
