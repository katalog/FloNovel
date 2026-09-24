package com.moonkata.flonovel.android.data.preprocess

/**
 * The Desktop app's text preprocessor, ported. Both apps must turn the same input into the same
 * bytes (AGENTS.md §1); the shared fixtures in `fixtures/parity/` hold both to that.
 *
 * Every pattern spells out its character classes instead of using `\d`, `\s`, `.` or
 * `\p{IsHangul}`, and matches Latin letters case by case instead of with IGNORE_CASE. The Desktop
 * app runs on the JVM's regex engine, this one on Android's ICU-backed one, and the shorthands mean
 * different things there: ICU's `\d` also matches full-width digits ("제１２화" would become a
 * heading on the phone only), its `\s` and `.` treat more characters as spaces and line breaks,
 * and its case folding matches letters the JVM's ASCII-only folding does not.
 */
object TextPreprocessor {

    const val MARKER_FILE_START = "## 파일 시작"
    const val MARKER_FILE_END = "## 파일 끝"
    const val MAX_FILE_NAME_LENGTH = 50

    // What the JVM's `\s` and `.` mean without flags.
    private const val SPACE = """[ \t\n\u000B\f\r]"""
    private const val ANY = """[^\n\r\u0085\u2028\u2029]"""
    private const val DIGIT = "[0-9]"

    private val reSpaces = Regex("$SPACE+")

    /**
     * Same list, order and meaning as the Desktop preprocessor's `reHeadingPatterns`, where each is
     * explained. 회/回 ("제1회") is deliberately not a heading marker in either app.
     */
    private val reHeadingPatterns = listOf(
        Regex("""[제第*]?$SPACE*($DIGIT+)$SPACE*[장화章話]"""),
        Regex("""(?<!#)#(?!#)$SPACE*($DIGIT+)"""),
        Regex("""^\* """),
        Regex("""^$ANY{0,2}$DIGIT+$SPACE*/$SPACE*$DIGIT+$SPACE*"""),
        Regex("""^$ANY{0,2}$DIGIT$DIGIT$DIGIT+$SPACE+"""),
        Regex("""(?:${ascii("chapter")}|${ascii("episode")}|${ascii("ep")}|${ascii("ch")})$SPACE*[.:#]?$SPACE*($DIGIT+)"""),
    )
    private val reThreeOrMoreNewlines = Regex("""\n{3,}""")
    private val reStartMarker = Regex("""^##$SPACE*파일$SPACE*시작""")
    private val reEndMarker = Regex("""^##$SPACE*파일$SPACE*끝""")

    /** `[cC][hH]...`: the JVM's case-insensitive matching is ASCII-only unless asked otherwise. */
    private fun ascii(word: String): String = word.map { c -> "[${c.lowercaseChar()}${c.uppercaseChar()}]" }.joinToString("")

    private fun hasScript(text: String, script: Character.UnicodeScript): Boolean =
        text.codePoints().anyMatch { Character.UnicodeScript.of(it) == script }

    private fun removeScript(text: String, script: Character.UnicodeScript): String {
        val out = StringBuilder(text.length)
        text.codePoints().forEach { if (Character.UnicodeScript.of(it) != script) out.appendCodePoint(it) }
        return out.toString()
    }

    /**
     * Hangul and Hanja together: drop the Hanja and collapse whitespace. Then cut to [maxRunes]
     * code points. Pure.
     */
    fun cleanFileName(nameWithoutExt: String, maxRunes: Int = MAX_FILE_NAME_LENGTH): String {
        var name = nameWithoutExt
        if (hasScript(name, Character.UnicodeScript.HANGUL) && hasScript(name, Character.UnicodeScript.HAN)) {
            val collapsed = reSpaces.replace(removeScript(name, Character.UnicodeScript.HAN), " ").trim()
            if (collapsed.isNotEmpty()) name = collapsed
        }
        val codePoints = name.codePoints().toArray()
        if (codePoints.size > maxRunes) {
            val truncated = String(codePoints, 0, maxRunes).trim()
            if (truncated.isNotEmpty()) name = truncated
        }
        return name
    }

    /** Prefixes "## " to heading lines not already marked. Pure, idempotent. */
    fun normalizeHeadings(content: String): String =
        content.split('\n').joinToString("\n") { line ->
            if (reHeadingPatterns.any { it.containsMatchIn(line) }) {
                val trimmed = line.trimStart(' ', '\t')
                if (!trimmed.startsWith("##")) "## $trimmed" else line
            } else {
                line
            }
        }

    /**
     * Line endings unified, leading whitespace stripped, adjacent duplicate lines dropped, one
     * blank line between paragraphs, headings marked, start/end markers ensured. Pure, idempotent.
     */
    fun normalizeContent(text: String): String {
        if (text.isEmpty()) return ""

        val lines = text.replace("\r\n", "\n").replace("\r", "\n").split('\n')
        val result = ArrayList<String>(lines.size * 2)
        var prevContent: String? = null
        for (line in lines) {
            val trimmed = line.trimStart { it.isWhitespace() }
            if (trimmed.isEmpty()) {
                result.add("")
                continue
            }
            if (trimmed == prevContent) continue
            if (result.isNotEmpty() && result.last().isNotEmpty()) result.add("")
            result.add(trimmed)
            prevContent = trimmed
        }

        val joined = reThreeOrMoreNewlines.replace(result.joinToString("\n"), "\n\n")
        val headed = normalizeHeadings(joined)
        if (headed.isBlank()) return ""

        val headedLines = headed.split('\n')
        val hasStart = headedLines.any { reStartMarker.containsMatchIn(it.trimStart()) }
        val hasEnd = headedLines.any { reEndMarker.containsMatchIn(it.trimStart()) }
        val withStart = if (!hasStart) "$MARKER_FILE_START\n\n${headed.trimStart('\n')}" else headed
        return if (!hasEnd) "${withStart.trimEnd('\n')}\n\n$MARKER_FILE_END" else withStart
    }
}
