package com.moonkata.flonovel.android.ui.reader

import com.moonkata.flonovel.android.data.datastore.PageGestureAction
import com.moonkata.flonovel.android.data.datastore.ReaderSettings
import com.moonkata.flonovel.android.data.datastore.TouchZoneMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSmartNavigationTest {

    @Test
    fun smartForwardAction_mappingsToPageGestureAction_areCorrect() {
        assertEquals(PageGestureAction.NEXT_PAGE, SmartForwardAction.NEXT_PAGE.toPageGestureAction())
        assertEquals(PageGestureAction.NEXT_CHAPTER, SmartForwardAction.NEXT_CHAPTER.toPageGestureAction())
        assertEquals(PageGestureAction.NEXT_CHAPTER_JUMP, SmartForwardAction.NEXT_CHAPTER_JUMP.toPageGestureAction())
    }

    @Test
    fun smartForwardAction_reverseAction_mapsToCorrespondingPreviousAction() {
        // Next page reverse -> Previous page
        assertEquals(PageGestureAction.PREVIOUS_PAGE, SmartForwardAction.NEXT_PAGE.reverseAction())
        // Next chapter reverse -> Previous chapter
        assertEquals(PageGestureAction.PREVIOUS_CHAPTER, SmartForwardAction.NEXT_CHAPTER.reverseAction())
        // Next chapter jump reverse -> Previous chapter jump
        assertEquals(PageGestureAction.PREVIOUS_CHAPTER_JUMP, SmartForwardAction.NEXT_CHAPTER_JUMP.reverseAction())
    }

    @Test
    fun resolveTapAction_underSmartGestureMode_preservesTop20PercentMenuAndLowerRepeat() {
        val width = 1080f
        val height = 2400f
        val defaultGrid = ReaderSettings.defaultGridTouchActions

        // Top 20% is y < 480f
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(0f, 0f, width, height, TouchZoneMode.SMART_GESTURE, defaultGrid),
        )
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(540f, 240f, width, height, TouchZoneMode.SMART_GESTURE, defaultGrid),
        )
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(1080f, 479.9f, width, height, TouchZoneMode.SMART_GESTURE, defaultGrid),
        )

        // Exact boundary and below is SMART_REPEAT
        assertEquals(
            PageGestureAction.SMART_REPEAT,
            resolveTapAction(0f, 480f, width, height, TouchZoneMode.SMART_GESTURE, defaultGrid),
        )
        assertEquals(
            PageGestureAction.SMART_REPEAT,
            resolveTapAction(540f, 1200f, width, height, TouchZoneMode.SMART_GESTURE, defaultGrid),
        )
        assertEquals(
            PageGestureAction.SMART_REPEAT,
            resolveTapAction(1080f, 2399f, width, height, TouchZoneMode.SMART_GESTURE, defaultGrid),
        )
    }

    @Test
    fun resolveTapAction_zeroOrNegativeDimensions_safelyDefaultsToShowMenu() {
        val defaultGrid = ReaderSettings.defaultGridTouchActions
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(100f, 500f, 0f, 1000f, TouchZoneMode.SMART_GESTURE, defaultGrid),
        )
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(100f, 500f, 1000f, 0f, TouchZoneMode.SMART_GESTURE, defaultGrid),
        )
        assertEquals(
            PageGestureAction.SHOW_MENU,
            resolveTapAction(100f, 500f, -100f, 1000f, TouchZoneMode.SMART_GESTURE, defaultGrid),
        )
    }

    @Test
    fun resolveSmartSwipeZone_usesWiderMiddleZone() {
        val height = 1000f

        // Top 27.5% (y < 275f): Next Chapter
        assertEquals(SmartSwipeZone.TOP, resolveSmartSwipeZone(0f, height))
        assertEquals(SmartSwipeZone.TOP, resolveSmartSwipeZone(150f, height))
        assertEquals(SmartSwipeZone.TOP, resolveSmartSwipeZone(274.9f, height))

        // Middle 45% (275f <= y < 725f): Next Page
        assertEquals(SmartSwipeZone.MIDDLE, resolveSmartSwipeZone(275f, height))
        assertEquals(SmartSwipeZone.MIDDLE, resolveSmartSwipeZone(450f, height))
        assertEquals(SmartSwipeZone.MIDDLE, resolveSmartSwipeZone(724.9f, height))

        // Bottom 27.5% (y >= 725f): Chapter Jump
        assertEquals(SmartSwipeZone.BOTTOM, resolveSmartSwipeZone(725f, height))
        assertEquals(SmartSwipeZone.BOTTOM, resolveSmartSwipeZone(750f, height))
        assertEquals(SmartSwipeZone.BOTTOM, resolveSmartSwipeZone(999.9f, height))
    }

    @Test
    fun resolveSmartSwipeZone_invalidOrZeroHeight_defaultsSafelyToMiddle() {
        assertEquals(SmartSwipeZone.MIDDLE, resolveSmartSwipeZone(100f, 0f))
        assertEquals(SmartSwipeZone.MIDDLE, resolveSmartSwipeZone(100f, -500f))
    }
}
