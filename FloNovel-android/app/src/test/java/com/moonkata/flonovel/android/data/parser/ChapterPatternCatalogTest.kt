package com.moonkata.flonovel.android.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Preset self-validation (each preset's example actually matches its own pattern, no duplicate ids)
 * plus buildPatterns's trusted/user-defined grouping logic. Uniqueness is trivially true today since there's only
 * one preset, but it's a regression guard against copy-paste mistakes when a preset gets added later.
 * (Custom-regex error handling and duplicate-chapter-counting prevention are already covered by
 * ChapterDetectorEdgeCaseTest.)
 *
 * [knownExamples] duplicates the English example text from strings.xml as a plain string, since a
 * pure-JVM unit test can't resolve Android string resources — [presets] only carries `@StringRes` ids
 * now. `getValue` throwing for a preset with no entry here is itself the regression guard mentioned
 * above.
 */
class ChapterPatternCatalogTest {

    private val knownExamples = mapOf(
        "hash" to "## Chapter 1: An Utterly Ordinary Beginning",
    )

    @Test
    fun everyPreset_ownExampleMatchesItsOwnPattern() {
        for (preset in ChapterPatternCatalog.presets) {
            val example = knownExamples.getValue(preset.id)
            assertTrue(
                "preset '${preset.id}'s example (\"$example\") should match its own pattern",
                preset.pattern.matches(example),
            )
        }
    }

    @Test
    fun everyPreset_hasAUniqueId() {
        val ids = ChapterPatternCatalog.presets.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun buildPatterns_withNothingEnabledAndNoCustomPatterns_isEmpty() {
        val patterns = ChapterPatternCatalog.buildPatterns(enabledIds = emptySet(), customPatterns = emptySet())

        assertTrue(patterns.isEmpty)
    }

    @Test
    fun buildPatterns_disablingAPreset_excludesItsPattern() {
        val allDisabled = ChapterPatternCatalog.buildPatterns(enabledIds = emptySet(), customPatterns = emptySet())
        val allEnabled = ChapterPatternCatalog.buildPatterns(
            enabledIds = ChapterPatternCatalog.defaultEnabledIds,
            customPatterns = emptySet(),
        )

        assertEquals(0, allDisabled.trusted.size)
        assertEquals(ChapterPatternCatalog.presets.size, allEnabled.trusted.size)
    }

    /**
     * The two groups must stay separate: [ChapterDetector] applies its length
     * guard only to user-defined patterns. Flattening them into one list is what
     * made the `##` preset inherit the 60-character limit and drop a fifth of the
     * library's chapters.
     */
    @Test
    fun buildPatterns_keepsPresetsAndCustomPatternsInSeparateGroups() {
        val patterns = ChapterPatternCatalog.buildPatterns(
            enabledIds = ChapterPatternCatalog.defaultEnabledIds,
            customPatterns = setOf("""^Chapter \d+$"""),
        )

        assertEquals(ChapterPatternCatalog.presets.size, patterns.trusted.size)
        assertEquals(1, patterns.userDefined.size)
        assertEquals(
            ChapterPatternCatalog.presets.map { it.pattern.pattern },
            patterns.trusted.map { it.pattern },
        )
    }
}
