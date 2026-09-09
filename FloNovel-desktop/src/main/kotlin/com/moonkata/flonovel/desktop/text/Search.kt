package com.moonkata.flonovel.desktop.text

data class SearchResult(
    val charOffset: Int,
    val snippet: String,
)

object Search {

    const val DEFAULT_SNIPPET_RADIUS = 20

    /**
     * Performs a case-insensitive substring search for [query] across [text].
     * Result count has NO upper limit (all matches in the file are returned).
     *
     * When snippets span line breaks, consecutive whitespaces are collapsed into
     * a single space so preview rows do not overflow the UI layout.
     */
    fun search(
        text: String,
        query: String,
        snippetRadius: Int = DEFAULT_SNIPPET_RADIUS,
    ): List<SearchResult> {
        if (query.isBlank() || text.isEmpty()) return emptyList()

        val results = mutableListOf<SearchResult>()
        var idx = text.indexOf(query, 0, ignoreCase = true)
        while (idx >= 0) {
            val snippetStart = (idx - snippetRadius).coerceAtLeast(0)
            val snippetEnd = (idx + query.length + snippetRadius).coerceAtMost(text.length)
            // Collapse raw newlines and whitespace into a single space so preview stays on a single line.
            val snippet = text.substring(snippetStart, snippetEnd).replace(Regex("\\s+"), " ").trim()
            results += SearchResult(charOffset = idx, snippet = snippet)
            idx = text.indexOf(query, idx + query.length, ignoreCase = true)
        }
        return results
    }
}
