package com.moonkata.flonovel.android.ui.reader

import com.moonkata.flonovel.android.data.datastore.PageGestureAction
import com.moonkata.flonovel.android.data.datastore.ReaderSettings
import com.moonkata.flonovel.android.data.datastore.TouchZoneMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTouchZoneTest {

    private val width = 1000f
    private val height = 900f
    private val defaultGridActions = ReaderSettings.defaultGridTouchActions

    @Test
    fun standard3Column_resolvesActionsWith424Ratio() {
        // 4:2:4 ratio means:
        // Left 40% (0 .. <400): PREVIOUS_PAGE
        // Center 20% (400 .. 600): SHOW_MENU
        // Right 40% (>600 .. 1000): NEXT_PAGE

        // Left region
        assertEquals(
            PageGestureAction.PREVIOUS_PAGE,
            resolveTapAction(0f, 450f, width, height, TouchZoneMode.STANDARD_3_COLUMN, defaultGridActions),
        )
        assertEquals(
            PageGestureAction.PREVIOUS_PAGE,
            resolveTapAction(200f, 450f, width, height, TouchZoneMode.STANDARD_3_COLUMN, defaultGridActions),
        )
        assertEquals(
            PageGestureAction.PREVIOUS_PAGE,
            resolveTapAction(399f, 450f, width, height, TouchZoneMode.STANDARD_3_COLUMN, defaultGridActions),
        )

        // Center region (Menu)
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(400f, 450f, width, height, TouchZoneMode.STANDARD_3_COLUMN, defaultGridActions),
        )
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(500f, 450f, width, height, TouchZoneMode.STANDARD_3_COLUMN, defaultGridActions),
        )
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(600f, 450f, width, height, TouchZoneMode.STANDARD_3_COLUMN, defaultGridActions),
        )

        // Right region
        assertEquals(
            PageGestureAction.NEXT_PAGE,
            resolveTapAction(601f, 450f, width, height, TouchZoneMode.STANDARD_3_COLUMN, defaultGridActions),
        )
        assertEquals(
            PageGestureAction.NEXT_PAGE,
            resolveTapAction(800f, 450f, width, height, TouchZoneMode.STANDARD_3_COLUMN, defaultGridActions),
        )
        assertEquals(
            PageGestureAction.NEXT_PAGE,
            resolveTapAction(1000f, 450f, width, height, TouchZoneMode.STANDARD_3_COLUMN, defaultGridActions),
        )
    }

    @Test
    fun grid3x3_resolvesColumnsWith424RatioAndRowsWith13Ratio() {
        // Custom actions mapping each index to a distinct action where possible
        val customActions = listOf(
            PageGestureAction.PREVIOUS_PAGE,          // 0: row 0, col 0
            PageGestureAction.SHOW_MENU,              // 1: row 0, col 1
            PageGestureAction.PREVIOUS_CHAPTER_JUMP,  // 2: row 0, col 2
            PageGestureAction.NEXT_CHAPTER_JUMP,      // 3: row 1, col 0
            PageGestureAction.PREVIOUS_CHAPTER,       // 4: row 1, col 1
            PageGestureAction.NEXT_CHAPTER,           // 5: row 1, col 2
            PageGestureAction.NONE,                   // 6: row 2, col 0
            PageGestureAction.NEXT_PAGE,              // 7: row 2, col 1
            PageGestureAction.PREVIOUS_PAGE,          // 8: row 2, col 2
        )

        // Row 0 (y = 100 < 300)
        assertEquals(PageGestureAction.PREVIOUS_PAGE, resolveTapAction(200f, 100f, width, height, TouchZoneMode.GRID_3X3, customActions))
        assertEquals(PageGestureAction.SHOW_MENU, resolveTapAction(500f, 100f, width, height, TouchZoneMode.GRID_3X3, customActions))
        assertEquals(PageGestureAction.PREVIOUS_CHAPTER_JUMP, resolveTapAction(800f, 100f, width, height, TouchZoneMode.GRID_3X3, customActions))

        // Row 1 (y = 450 in 300..600)
        assertEquals(PageGestureAction.NEXT_CHAPTER_JUMP, resolveTapAction(200f, 450f, width, height, TouchZoneMode.GRID_3X3, customActions))
        assertEquals(PageGestureAction.PREVIOUS_CHAPTER, resolveTapAction(500f, 450f, width, height, TouchZoneMode.GRID_3X3, customActions))
        assertEquals(PageGestureAction.NEXT_CHAPTER, resolveTapAction(800f, 450f, width, height, TouchZoneMode.GRID_3X3, customActions))

        // Row 2 (y = 750 in 600..900)
        assertEquals(PageGestureAction.NONE, resolveTapAction(200f, 750f, width, height, TouchZoneMode.GRID_3X3, customActions))
        assertEquals(PageGestureAction.NEXT_PAGE, resolveTapAction(500f, 750f, width, height, TouchZoneMode.GRID_3X3, customActions))
        assertEquals(PageGestureAction.PREVIOUS_PAGE, resolveTapAction(800f, 750f, width, height, TouchZoneMode.GRID_3X3, customActions))
    }

    @Test
    fun grid3x3_columnBoundariesMatch424Ratio() {
        // x < 400 is col 0, 400..600 is col 1, >600 is col 2
        // Default grid row 0: col 0 -> PREVIOUS_PAGE, col 1 -> SHOW_MENU, col 2 -> PREVIOUS_PAGE
        // Default grid row 1: col 0 -> NEXT_PAGE, col 1 -> NEXT_PAGE, col 2 -> NEXT_PAGE
        val yInRow0 = 150f

        assertEquals(PageGestureAction.PREVIOUS_PAGE, resolveTapAction(399f, yInRow0, width, height, TouchZoneMode.GRID_3X3, defaultGridActions))
        assertEquals(PageGestureAction.SHOW_MENU, resolveTapAction(400f, yInRow0, width, height, TouchZoneMode.GRID_3X3, defaultGridActions))
        assertEquals(PageGestureAction.SHOW_MENU, resolveTapAction(600f, yInRow0, width, height, TouchZoneMode.GRID_3X3, defaultGridActions))
        assertEquals(PageGestureAction.PREVIOUS_PAGE, resolveTapAction(601f, yInRow0, width, height, TouchZoneMode.GRID_3X3, defaultGridActions))
    }

    @Test
    fun invalidViewportDimensions_defaultsSafelyToShowMenu() {
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(10f, 10f, 0f, 100f, TouchZoneMode.STANDARD_3_COLUMN, defaultGridActions),
        )
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(10f, 10f, 100f, 0f, TouchZoneMode.GRID_3X3, defaultGridActions),
        )
    }
}
