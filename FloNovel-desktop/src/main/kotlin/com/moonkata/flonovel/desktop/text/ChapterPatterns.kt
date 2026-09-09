package com.moonkata.flonovel.desktop.text

/**
 * Represents a compiled rule for chapter detection.
 *
 * Distinguishes preset patterns (e.g. "##") where no length limit is applied,
 * from custom user patterns where a safety-net length limit (<= 60 chars) is enforced.
 */
data class ChapterPatternRule(
    val id: String,
    val regex: Regex,
    val maxLineLength: Int? = null,
) {
    fun matches(line: String): Boolean {
        if (maxLineLength != null && line.length > maxLineLength) return false
        return regex.matches(line)
    }
}

object ChapterPatterns {
    const val PRESET_HASH = "hash"
    const val CUSTOM_MAX_LINE_LENGTH = 60

    /**
     * Built-in preset definitions.
     * "hash" matches lines starting with "##". No length limit is applied to presets.
     */
    val presetHashRegex = Regex("""^##.*$""")

    val defaultEnabledPresetIds: Set<String> = setOf(PRESET_HASH)

    /**
     * Compiles enabled built-in presets and user custom regexes while preserving
     * the distinction between unlimited presets and length-limited custom patterns.
     *
     * Invalid custom regex strings are quietly excluded without crashing or affecting valid patterns.
     */
    fun buildRules(
        enabledPresetIds: Set<String> = defaultEnabledPresetIds,
        customPatterns: Collection<String> = emptyList(),
    ): List<ChapterPatternRule> {
        val rules = mutableListOf<ChapterPatternRule>()

        if (PRESET_HASH in enabledPresetIds) {
            // Preset "##" has NO length limit. Enforcing a 60-char limit dropped ~19.6%
            // of valid Korean web novel chapters in production.
            rules += ChapterPatternRule(
                id = PRESET_HASH,
                regex = presetHashRegex,
                maxLineLength = null,
            )
        }

        for (custom in customPatterns) {
            val compiled = runCatching { Regex(custom) }.getOrNull() ?: continue
            rules += ChapterPatternRule(
                id = "custom:$custom",
                regex = compiled,
                maxLineLength = CUSTOM_MAX_LINE_LENGTH,
            )
        }

        return rules
    }
}
