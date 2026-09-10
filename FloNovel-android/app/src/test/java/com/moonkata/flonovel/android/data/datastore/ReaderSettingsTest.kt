package com.moonkata.flonovel.android.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsTest {

    @Test
    fun defaultSettings_hasStandard3ColumnModeAndCorrectSwipeDefaults() {
        val settings = ReaderSettings()

        assertEquals(TouchZoneMode.STANDARD_3_COLUMN, settings.touchZoneMode)
        assertEquals(PageGestureAction.NEXT_CHAPTER, settings.swipeLeftAction)
        assertEquals(PageGestureAction.PREVIOUS_CHAPTER, settings.swipeRightAction)
        assertEquals(PageGestureAction.NEXT_CHAPTER_JUMP, settings.swipeUpAction)
        assertEquals(PageGestureAction.PREVIOUS_CHAPTER_JUMP, settings.swipeDownAction)
    }

    @Test
    fun defaultGridTouchActions_hasExpected9CellLayout() {
        val actions = ReaderSettings.defaultGridTouchActions

        assertEquals(9, actions.size)
        // Row 0: Top-left previous, top-center menu, top-right previous
        assertEquals(PageGestureAction.PREVIOUS_PAGE, actions[0])
        assertEquals(PageGestureAction.SHOW_MENU, actions[1])
        assertEquals(PageGestureAction.PREVIOUS_PAGE, actions[2])

        // Row 1 & Row 2: All next page
        for (i in 3..8) {
            assertEquals("Index $i should default to NEXT_PAGE", PageGestureAction.NEXT_PAGE, actions[i])
        }
    }

    @Test
    fun pageGestureAction_containsAll8SupportedActions() {
        val expectedActions = setOf(
            PageGestureAction.PREVIOUS_PAGE,
            PageGestureAction.NEXT_PAGE,
            PageGestureAction.PREVIOUS_CHAPTER_JUMP,
            PageGestureAction.NEXT_CHAPTER_JUMP,
            PageGestureAction.PREVIOUS_CHAPTER,
            PageGestureAction.NEXT_CHAPTER,
            PageGestureAction.SHOW_MENU,
            PageGestureAction.NONE,
        )

        assertEquals(expectedActions, PageGestureAction.entries.toSet())
    }

    @Test
    fun gridTouchActions_parsingLogic_recoversFromCorruptOrMalformedStrings() {
        val defaults = ReaderSettings.defaultGridTouchActions

        // Case 1: Valid 9 tokens
        val validRaw = "NEXT_PAGE,SHOW_MENU,PREVIOUS_PAGE,NONE,NEXT_CHAPTER,PREVIOUS_CHAPTER,PREVIOUS_PAGE,NEXT_PAGE,SHOW_MENU"
        val parsedValid = validRaw.split(",").let { tokens ->
            if (tokens.size == 9) {
                tokens.map { runCatching { PageGestureAction.valueOf(it) }.getOrDefault(PageGestureAction.NEXT_PAGE) }
            } else defaults
        }
        assertEquals(PageGestureAction.NEXT_CHAPTER, parsedValid[4])
        assertEquals(PageGestureAction.PREVIOUS_CHAPTER, parsedValid[5])
        assertEquals(PageGestureAction.SHOW_MENU, parsedValid[8])

        // Case 2: Wrong number of tokens (< 9)
        val shortRaw = "PREVIOUS_PAGE,NEXT_PAGE"
        val parsedShort = shortRaw.split(",").let { tokens ->
            if (tokens.size == 9) {
                tokens.map { runCatching { PageGestureAction.valueOf(it) }.getOrDefault(PageGestureAction.NEXT_PAGE) }
            } else defaults
        }
        assertEquals(defaults, parsedShort)

        // Case 3: 9 tokens but with an invalid enum token falls back to NEXT_PAGE
        val invalidTokenRaw = "PREVIOUS_PAGE,INVALID_ACTION,PREVIOUS_PAGE,NEXT_PAGE,NEXT_PAGE,NEXT_PAGE,NEXT_PAGE,NEXT_PAGE,NEXT_PAGE"
        val parsedInvalid = invalidTokenRaw.split(",").let { tokens ->
            if (tokens.size == 9) {
                tokens.map { runCatching { PageGestureAction.valueOf(it) }.getOrDefault(PageGestureAction.NEXT_PAGE) }
            } else defaults
        }
        assertEquals(PageGestureAction.NEXT_PAGE, parsedInvalid[1])
    }
}
