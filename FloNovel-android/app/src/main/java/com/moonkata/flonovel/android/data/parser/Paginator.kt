package com.moonkata.flonovel.android.data.parser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.moonkata.flonovel.android.model.PageBreak
import com.moonkata.flonovel.android.model.Paragraph

data class PaginationParams(
    val fontFamily: FontFamily,
    val fontSizeSp: TextUnit,
    val lineHeightMultiplier: Float,
    val letterSpacingSp: TextUnit,
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val textColor: Color,
)

val READER_LINE_BREAK = androidx.compose.ui.text.style.LineBreak(
    strategy = androidx.compose.ui.text.style.LineBreak.Strategy.Simple,
    strictness = androidx.compose.ui.text.style.LineBreak.Strictness.Normal,
    wordBreak = androidx.compose.ui.text.style.LineBreak.WordBreak.Phrase,
)

/**
 * Determines page boundaries by measuring the exact source span (a substring of fullText) that
 * will go on the page. It does not measure each paragraph separately and sum their heights —
 * the actual rendering (ReaderPagerContent) draws the whole page as a single Text, and the sum
 * of individually-measured line heights doesn't always equal the height of measuring them all at
 * once (line-spacing rounding differs), so the per-paragraph-sum approach used to underestimate
 * how much fits and push the last line(s) off the page entirely.
 */
object Paginator {

    /**
     * Computes at most [maxPages] pages, moving forward only from [fromOffset].
     * The new page starts at the paragraph containing the offset, so the resume position can be
     * shown on screen immediately without scanning the whole book from the start — this is a
     * temporary display used until the full page set is recomputed in the background via
     * [paginate] and swapped in.
     */
    fun paginateFrom(
        fullText: String,
        paragraphs: List<Paragraph>,
        fromOffset: Int,
        textMeasurer: TextMeasurer,
        params: PaginationParams,
        maxPages: Int,
    ): List<PageBreak> {
        if (paragraphs.isEmpty()) return emptyList()
        val startIndex = findParagraphIndex(paragraphs, fromOffset)
        val slice = paragraphs.subList(startIndex, paragraphs.size)
        val initialStart = fromOffset.coerceIn(slice.first().startOffset, slice.first().endOffset)
        return paginateCore(fullText, slice, textMeasurer, params, maxPages, initialPageStart = initialStart)
    }

    private fun paginateCore(
        fullText: String,
        paragraphs: List<Paragraph>,
        textMeasurer: TextMeasurer,
        params: PaginationParams,
        maxPages: Int,
        initialPageStart: Int? = null,
    ): List<PageBreak> {
        if (params.contentWidthPx <= 0 || params.contentHeightPx <= 0 || paragraphs.isEmpty()) return emptyList()

        val style = TextStyle(
            fontFamily = params.fontFamily,
            fontSize = params.fontSizeSp,
            lineHeight = params.fontSizeSp * params.lineHeightMultiplier,
            letterSpacing = params.letterSpacingSp,
            color = params.textColor,
            lineBreak = READER_LINE_BREAK,
        )
        val constraints = Constraints(maxWidth = params.contentWidthPx)
        val contentHeightPx = params.contentHeightPx.toFloat()

        val pageBreaks = mutableListOf<PageBreak>()
        var pageStart = skipLeadingBlankLines(fullText, initialPageStart ?: paragraphs.first().startOffset)
        var index = 0

        while (index < paragraphs.size) {
            val candidateEnd = paragraphs[index].endOffset
            if (candidateEnd <= pageStart) {
                // A paragraph that already ends before this page's start (e.g. a blank line) — skip it.
                index++
                continue
            }

            val candidateText = fullText.substring(pageStart, candidateEnd)
            val layout = textMeasurer.measure(
                text = AnnotatedString(candidateText),
                style = style,
                constraints = constraints,
            )

            if (layout.size.height.toFloat() <= contentHeightPx) {
                // Still fits on the page even with this paragraph included — try extending with the next one.
                index++
                if (index == paragraphs.size) {
                    pageBreaks += PageBreak(pageStart, candidateEnd)
                }
            } else {
                // Including this paragraph overflows — figure out how many lines actually fit and close the page there.
                var fitLines = 0
                for (lineIndex in 0 until layout.lineCount) {
                    if (layout.getLineBottom(lineIndex) > contentHeightPx) break
                    fitLines = lineIndex + 1
                }
                if (fitLines == 0) fitLines = 1 // Guard against the extreme case where not even one line fits (avoids an infinite loop)
                val splitAt = layout.getLineEnd(fitLines - 1, visibleEnd = true).coerceAtLeast(1)
                val newPageEnd = (pageStart + splitAt).coerceAtMost(candidateEnd)
                pageBreaks += PageBreak(pageStart, newPageEnd)
                if (pageBreaks.size >= maxPages) return pageBreaks
                // If a blank line (the newline separating paragraphs) carries over to the top of the next
                // page, it looks like a stray blank line floating at the top even though it isn't really
                // there — always skip newlines so a new page starts at actual text.
                pageStart = skipLeadingBlankLines(fullText, newPageEnd)
                // index is left as-is — the rest of the same paragraph is retried on the next page (any
                // empty paragraphs in between get auto-skipped by the "candidateEnd <= pageStart" check
                // at the top of the loop, thanks to the skip above).
            }
        }

        return pageBreaks
    }

    /** Returns the position after skipping over consecutive newline characters starting at [offset] —
     * so a paragraph-separator blank line never carries over to the top of a page. */
    private fun skipLeadingBlankLines(fullText: String, offset: Int): Int {
        var i = offset
        while (i < fullText.length && (fullText[i] == '\n' || fullText[i] == '\r')) i++
        return i
    }

    private fun findParagraphIndex(paragraphs: List<Paragraph>, offset: Int): Int {
        var lo = 0
        var hi = paragraphs.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (paragraphs[mid].endOffset <= offset) lo = mid + 1 else hi = mid
        }
        return lo.coerceIn(0, paragraphs.size - 1)
    }

    private const val BACKWARD_SEARCH_MAX_ATTEMPTS = 4
    private const val BACKWARD_SEARCH_MIN_SPAN = 1000

    /**
     * Estimates the "previous page" ending at [endOffset] when there's no visit history to fall back on
     * (e.g. the first "previous" tap right after a jump/TOC navigation).
     *
     * Unlike forward batch guessing which drops gap paragraphs when page boundaries do not align with
     * [endOffset], this performs bottom-up line-based measurement directly before [endOffset]:
     * It measures the preceding text block using Compose [TextMeasurer], and determines the highest
     * line whose bottom edge is within [PaginationParams.contentHeightPx] from the bottom line ending
     * at [endOffset]. This ensures 100% continuous text without any dropped characters or paragraphs.
     */
    fun onePageEndingAt(
        fullText: String,
        paragraphs: List<Paragraph>,
        endOffset: Int,
        textMeasurer: TextMeasurer,
        params: PaginationParams,
        referenceSpanChars: Int,
    ): PageBreak? {
        if (endOffset <= 0 || paragraphs.isEmpty() || fullText.isEmpty()) return null
        if (params.contentWidthPx <= 0 || params.contentHeightPx <= 0) return null

        val targetEnd = endOffset.coerceIn(1, fullText.length)
        val style = TextStyle(
            fontFamily = params.fontFamily,
            fontSize = params.fontSizeSp,
            lineHeight = params.fontSizeSp * params.lineHeightMultiplier,
            letterSpacing = params.letterSpacingSp,
            color = params.textColor,
            lineBreak = READER_LINE_BREAK,
        )
        val constraints = Constraints(maxWidth = params.contentWidthPx)
        val contentHeightPx = params.contentHeightPx.toFloat()

        var span = (referenceSpanChars * 2).coerceAtLeast(BACKWARD_SEARCH_MIN_SPAN)
        var bestPage: PageBreak? = null

        repeat(BACKWARD_SEARCH_MAX_ATTEMPTS) {
            val candidateStart = (targetEnd - span).coerceAtLeast(0)
            val candidateText = fullText.substring(candidateStart, targetEnd)
            val layout = textMeasurer.measure(
                text = AnnotatedString(candidateText),
                style = style,
                constraints = constraints,
            )

            val lineCount = layout.lineCount
            if (lineCount > 0) {
                val lastLineIndex = lineCount - 1
                val bottomPx = layout.getLineBottom(lastLineIndex)

                var topFitLine = lastLineIndex
                for (line in (lastLineIndex - 1) downTo 0) {
                    if (bottomPx - layout.getLineTop(line) > contentHeightPx) {
                        break
                    }
                    topFitLine = line
                }

                val startInCandidate = layout.getLineStart(topFitLine)
                val rawStart = candidateStart + startInCandidate
                val pageStart = skipLeadingBlankLines(fullText, rawStart).coerceAtMost(targetEnd)
                bestPage = PageBreak(pageStart, targetEnd)

                // If candidateStart == 0 or the layout height already exceeded contentHeightPx,
                // we have found the true highest line that fits before targetEnd.
                if (candidateStart == 0 || layout.size.height.toFloat() >= contentHeightPx) {
                    return bestPage
                }
            }

            if (candidateStart == 0) return bestPage
            span *= 2
        }

        return bestPage
    }
}
