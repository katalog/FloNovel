package com.moonkata.flonovel.android.data.parser

import androidx.annotation.StringRes
import com.moonkata.flonovel.android.R

/** One regex pattern used for automatic table-of-contents (chapter) detection — a built-in preset. */
data class ChapterPatternPreset(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val exampleRes: Int,
    val pattern: Regex,
)

object ChapterPatternCatalog {

    val presets: List<ChapterPatternPreset> = listOf(
        ChapterPatternPreset(
            id = "hash",
            labelRes = R.string.chapter_pattern_preset_hash_label,
            exampleRes = R.string.chapter_pattern_preset_hash_example,
            pattern = Regex("""^##.*$"""),
        ),
    )

    val defaultEnabledIds: Set<String> = presets.map { it.id }.toSet()

    /**
     * Combines the enabled built-in presets with the user's custom regexes.
     * Invalid regexes are silently filtered out.
     *
     * The two groups stay separate on purpose: [ChapterDetector] applies its
     * line-length guard only to user-defined patterns. Flattening them into one
     * list is what previously made the `##` preset inherit the 60-character
     * limit and silently drop a fifth of the library's chapters.
     */
    fun buildPatterns(enabledIds: Set<String>, customPatterns: Set<String>): ChapterPatterns {
        val builtins = presets.filter { it.id in enabledIds }.map { it.pattern }
        val customs = customPatterns.mapNotNull { runCatching { Regex(it) }.getOrNull() }
        return ChapterPatterns(trusted = builtins, userDefined = customs)
    }
}
