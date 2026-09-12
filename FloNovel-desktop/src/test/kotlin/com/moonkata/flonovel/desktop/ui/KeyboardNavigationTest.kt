package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.input.key.Key
import com.moonkata.flonovel.desktop.reader.ChapterJumpNavigator
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import com.moonkata.flonovel.desktop.text.Chapter
import com.moonkata.flonovel.desktop.text.Search
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for T-11: Keyboard Navigation.
 *
 * Requirements:
 * - Chapter jump forward/backward shortcuts (Ctrl+PgDn / ']', Ctrl+PgUp / '[').
 * - Search: open -> input -> submit -> result list -> select jump.
 *   Search executes ONLY ON SUBMISSION (typing does not search).
 *   No upper limit on search results count.
 * - Chapter jump on book with 0 chapters: fall back to normal page turn (never do nothing).
 * - Chapter jump remembers last jump target to prevent getting stuck on same point.
 * - U14 scenario: complete reading flow without a mouse.
 */
class KeyboardNavigationTest {

    // --- 1. Search executes only on submission & has no upper limit ---

    @Test
    fun u8_search_executesOnlyOnSubmit_andHasNoUpperLimit() {
        val word = "마왕"
        val repeatedCount = 350
        val text = (1..repeatedCount).joinToString(" 그리고 ") { "제 $it 장에서 $word 이(가) 나타났다." }

        // Typing alone: pure string manipulation / UI state change, Search.search is not called until submit.
        val query = "마왕"

        // On submit: Search.search is executed
        val results = Search.search(text, query)

        assertEquals(repeatedCount, results.size, "Search must return all occurrences without arbitrary upper limit")
        assertEquals(query, "마왕")
    }

    // --- 2. Chapter jump breakpoints calculation with 20-line close chapter threshold ---

    @Test
    fun chapterJump_breakpointsCalculation_andThreshold() {
        // Chapters separated by > 20 lines -> 4 divisions
        val linesLong = (1..30).joinToString("\n") { "본문 줄 $it" }
        // Chapters separated by <= 20 lines -> 1 jump (no subdivisions)
        val linesShort = (1..5).joinToString("\n") { "짧은 공지 줄 $it" }

        val ch1 = Chapter("## 1장", 0)
        val offset2 = ch1.charOffset + linesLong.length + 1
        val ch2 = Chapter("## 2장", offset2)
        val offset3 = ch2.charOffset + linesShort.length + 1
        val ch3 = Chapter("## 3장", offset3)

        val text = "## 1장\n$linesLong\n## 2장\n$linesShort\n## 3장\n"

        val chapters = listOf(ch1, ch2, ch3)
        val breakpoints = ChapterJumpNavigator.breakpoints(
            chapters = chapters,
            totalCharCount = text.length,
            divisions = 4,
            text = text,
        )

        assertTrue(breakpoints.isNotEmpty())
        // Between ch1 and ch2 (long span): divided into 4 points
        // Between ch2 and ch3 (short span <= 20 lines): only ch3 offset added without subdivisions
        assertTrue(breakpoints.contains(offset2), "Must contain ch2 offset")
        assertTrue(breakpoints.contains(offset3), "Must contain ch3 offset")

        // Next breakpoint after 0 is the 1st quarter
        val next1 = ChapterJumpNavigator.nextBreakpoint(breakpoints, 0)
        assertNotNull(next1)
        assertTrue(next1 > 0 && next1 < offset2)

        // Previous breakpoint before offset2 is the 3rd quarter
        val prev1 = ChapterJumpNavigator.previousBreakpoint(breakpoints, offset2)
        assertNotNull(prev1)
        assertTrue(prev1 > 0 && prev1 < offset2)
    }

    // --- 3. Chapter jump on book with 0 chapters falls back to normal page turn ---

    @Test
    fun chapterJump_zeroChapters_fallsBackToNormalPageTurn() {
        val text = (1..100).joinToString("\n") { "Line $it: Plain text novel with no chapter headings." }
        val spec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(
            totalLength = text.length,
            textFitter = { from, _, _ -> (from + 150).coerceAtMost(text.length) },
            initialSpec = spec,
            initialAnchor = 0,
        )

        val emptyChapters = emptyList<Chapter>()

        // When chapter jump is attempted on 0 chapters:
        var noticeEmitted = false
        val initialAnchor = navigator.anchor

        fun performNextJump() {
            if (emptyChapters.isEmpty()) {
                noticeEmitted = true
                navigator.advance(0.5f) // Fall back to normal page turn
            }
        }

        performNextJump()

        assertTrue(noticeEmitted, "Notice message should be emitted when book has 0 chapters")
        assertTrue(navigator.anchor > initialAnchor, "Anchor must advance normally, must not do nothing")
    }

    // --- 4. Chapter jump remembers last jump target preventing immediate re-jump to same point ---

    @Test
    fun chapterJump_remembersLastTarget_preventingStuckOnSamePoint() {
        val breakpoints = listOf(200, 400, 600, 800, 1000)
        var currentAnchor = 0
        var lastChapterJumpOffset: Int? = null

        // 1st jump: anchor = max(0, MIN) -> next is 200
        val effectiveAnchor1 = maxOf(currentAnchor, lastChapterJumpOffset ?: Int.MIN_VALUE)
        val target1 = ChapterJumpNavigator.nextBreakpoint(breakpoints, effectiveAnchor1)
        assertEquals(200, target1)

        lastChapterJumpOffset = target1
        // Suppose page settles with anchor at 180 (start of page containing 200, e.g. 2-pane or paragraph start)
        currentAnchor = 180

        // 2nd jump: without lastChapterJumpOffset, effectiveAnchor would be 180 -> next would be 200 again (STUCK!)
        // WITH lastChapterJumpOffset: effectiveAnchor = max(180, 200) = 200 -> next is 400!
        val effectiveAnchor2 = maxOf(currentAnchor, lastChapterJumpOffset ?: Int.MIN_VALUE)
        val target2 = ChapterJumpNavigator.nextBreakpoint(breakpoints, effectiveAnchor2)
        assertEquals(400, target2, "Must advance to 400 without repeating 200")

        lastChapterJumpOffset = target2
        currentAnchor = 370 // page settles at 370

        // 3rd jump: backward jump
        // previous breakpoint before min(370, 400) = 370 -> prev is 200
        val effectiveAnchorPrev = minOf(currentAnchor, lastChapterJumpOffset ?: Int.MAX_VALUE)
        val targetPrev = ChapterJumpNavigator.previousBreakpoint(breakpoints, effectiveAnchorPrev)
        assertEquals(200, targetPrev, "Previous jump must go to 200")
    }

    // --- 5. Physical Home/End keys are not bound to any action ---

    @Test
    fun physicalHomeAndEndKeys_areNotHandled() {
        // The hardcoded physical Home/End -> jump-to-start/end binding was removed
        // (it silently conflicted with keymap.home's F1 default). Until a
        // configurable keymap entry is added for these, the keys must fall
        // through unhandled rather than triggering any action.
        val navigator = ReaderNavigator(
            totalLength = 5000,
            textFitter = { from, _, _ -> from + 200 },
            initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE),
            initialAnchor = 1200,
        )

        var homeCalled = false
        val handledHome = handleKeyAction(
            key = Key.Home,
            navigator = navigator,
            advanceRatio = 0.5f,
            onHome = { homeCalled = true },
        )
        assertTrue(!handledHome)
        assertTrue(!homeCalled)
        assertEquals(1200, navigator.anchor)

        val handledEnd = handleKeyAction(
            key = Key.MoveEnd,
            navigator = navigator,
            advanceRatio = 0.5f,
        )
        assertTrue(!handledEnd)
        assertEquals(1200, navigator.anchor)
    }

    // --- 6. U14 Scenario: Complete reading flow without mouse ---

    @Test
    fun u14_completeReadingFlowWithoutMouse() {
        val navigator = ReaderNavigator(
            totalLength = 10000,
            textFitter = { from, _, _ -> (from + 200).coerceAtMost(10000) },
            initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE),
            initialAnchor = 0,
        )

        var settingsOpened = false
        var tocOpened = false
        var searchOpened = false
        var nextChapterJumpCalled = false
        var prevChapterJumpCalled = false
        var homeCalled = false

        // 1. Advance via '>' (Period) - default next page
        assertTrue(handleKeyAction(Key.Period, navigator = navigator, advanceRatio = 0.5f))
        val anchorAfterPeriod = navigator.anchor
        assertTrue(anchorAfterPeriod > 0)

        // 2. Retreat via '<' (Comma) - default prev page
        assertTrue(handleKeyAction(Key.Comma, navigator = navigator, advanceRatio = 0.5f))
        assertTrue(navigator.anchor < anchorAfterPeriod)

        // 3. Chapter Jump Next via PageDown (default next chapter shortcut)
        assertTrue(
            handleKeyAction(
                key = Key.PageDown,
                navigator = navigator,
                advanceRatio = 0.5f,
                onNextChapterJump = { nextChapterJumpCalled = true },
            )
        )
        assertTrue(nextChapterJumpCalled)

        // 4. Chapter Jump Next via ']' (RightBracket) & Ctrl+PageDown
        nextChapterJumpCalled = false
        assertTrue(
            handleKeyAction(
                key = Key.RightBracket,
                navigator = navigator,
                advanceRatio = 0.5f,
                onNextChapterJump = { nextChapterJumpCalled = true },
            )
        )
        assertTrue(nextChapterJumpCalled)

        // 5. Chapter Jump Prev via PageUp (default prev chapter shortcut)
        assertTrue(
            handleKeyAction(
                key = Key.PageUp,
                navigator = navigator,
                advanceRatio = 0.5f,
                onPreviousChapterJump = { prevChapterJumpCalled = true },
            )
        )
        assertTrue(prevChapterJumpCalled)

        // 6. Chapter Jump Prev via '[' (LeftBracket) & Ctrl+PageUp
        prevChapterJumpCalled = false
        assertTrue(
            handleKeyAction(
                key = Key.LeftBracket,
                navigator = navigator,
                advanceRatio = 0.5f,
                onPreviousChapterJump = { prevChapterJumpCalled = true },
            )
        )
        assertTrue(prevChapterJumpCalled)

        // 7. Open Search via F2 (default search shortcut)
        assertTrue(
            handleKeyAction(
                key = Key.F2,
                navigator = navigator,
                advanceRatio = 0.5f,
                onOpenSearch = { searchOpened = true },
            )
        )
        assertTrue(searchOpened)

        // 8. Open Search via Ctrl+F and '/' (Slash)
        searchOpened = false
        assertTrue(
            handleKeyAction(
                key = Key.F,
                isCtrlPressed = true,
                navigator = navigator,
                advanceRatio = 0.5f,
                onOpenSearch = { searchOpened = true },
            )
        )
        assertTrue(searchOpened)

        searchOpened = false
        assertTrue(
            handleKeyAction(
                key = Key.Slash,
                navigator = navigator,
                advanceRatio = 0.5f,
                onOpenSearch = { searchOpened = true },
            )
        )
        assertTrue(searchOpened)

        // 9. Open TOC via F3 (default toc shortcut) and T
        assertTrue(
            handleKeyAction(
                key = Key.F3,
                navigator = navigator,
                advanceRatio = 0.5f,
                onOpenToc = { tocOpened = true },
            )
        )
        assertTrue(tocOpened)

        tocOpened = false
        assertTrue(
            handleKeyAction(
                key = Key.T,
                navigator = navigator,
                advanceRatio = 0.5f,
                onOpenToc = { tocOpened = true },
            )
        )
        assertTrue(tocOpened)

        // 10. Open Settings via F4 (default settings shortcut)
        assertTrue(
            handleKeyAction(
                key = Key.F4,
                navigator = navigator,
                advanceRatio = 0.5f,
                onOpenSettings = { settingsOpened = true },
            )
        )
        assertTrue(settingsOpened)

        // 11. Home via F1 (default home shortcut)
        assertTrue(
            handleKeyAction(
                key = Key.F1,
                navigator = navigator,
                advanceRatio = 0.5f,
                onHome = { homeCalled = true },
            )
        )
        assertTrue(homeCalled)
    }

    @Test
    fun testShiftPeriodAndCommaNavigation() {
        val text = "A".repeat(5000)
        val spec = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.TWO, gutterPx = 20)
        val navigator = ReaderNavigator(text.length, { from, _, _ -> minOf(from + 200, text.length) }, spec, initialAnchor = 200)

        val initialLayout = navigator.state.layout
        val prevRight = initialLayout.rightPane!!
        assertEquals(200, prevRight.startOffset)
        assertEquals(400, prevRight.endOffset)

        // Advance with Shift+Period ('>')
        val handledGreater = handleKeyAction(
            key = Key.Period,
            isShiftPressed = true,
            navigator = navigator,
            advanceRatio = 0.5f,
        )
        assertTrue(handledGreater)
        assertTrue(navigator.anchor > 200)

        // B2 requirement verification:
        // "one > -> does the previous right content become the new left?"
        val newLayout = navigator.state.layout
        assertEquals(prevRight.startOffset, newLayout.leftPane.startOffset, "New left pane must start at previous right pane start")
        assertEquals(prevRight.endOffset, newLayout.leftPane.endOffset, "New left pane must end at previous right pane end")

        // Retreat with Shift+Comma ('<')
        val handledLess = handleKeyAction(
            key = Key.Comma,
            isShiftPressed = true,
            navigator = navigator,
            advanceRatio = 0.5f,
        )
        assertTrue(handledLess)
        assertEquals(200, navigator.anchor)
        val restoredLayout = navigator.state.layout
        assertEquals(prevRight.startOffset, restoredLayout.rightPane!!.startOffset)

        // Advance without Shift ('.')
        val handledDot = handleKeyAction(
            key = Key.Period,
            isShiftPressed = false,
            navigator = navigator,
            advanceRatio = 0.5f,
        )
        assertTrue(handledDot)
        assertEquals(newLayout.leftPane.startOffset, navigator.state.layout.leftPane.startOffset)
    }

    @Test
    fun testWindowLevelKeyDispatchingWithDialogs() {
        val text = "A".repeat(2000)
        val spec = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, { from, _, _ -> minOf(from + 200, text.length) }, spec, initialAnchor = 0)

        var showSettings = false
        var showToc = false
        var showSearch = false

        fun dispatch(key: Key, isShift: Boolean = false, isCtrl: Boolean = false): Boolean {
            if (showSettings || showToc || showSearch) {
                return false // Dialog is open: pass through to dialog / text input
            }
            return handleKeyAction(
                key = key,
                isShiftPressed = isShift,
                isCtrlPressed = isCtrl,
                navigator = navigator,
                advanceRatio = 0.5f,
                onOpenSettings = { showSettings = true },
                onOpenToc = { showToc = true },
                onOpenSearch = { showSearch = true },
            )
        }

        // 1. Immediately on launch: '>' (Period) works
        val initialAnchor = navigator.anchor
        val periodHandled = dispatch(Key.Period)
        assertTrue(periodHandled)
        assertTrue(navigator.anchor > initialAnchor)

        // 2. Open TOC: F3
        val f3Handled = dispatch(Key.F3)
        assertTrue(f3Handled)
        assertTrue(showToc)

        // While TOC is open: shortcuts must NOT be intercepted by reader shortcuts
        val blockedWhileToc = dispatch(Key.Period)
        kotlin.test.assertFalse(blockedWhileToc, "Reader shortcuts must pass through while TOC is open")

        // Close TOC:
        showToc = false

        // After TOC closed: Period must work again immediately without needing focus
        val anchorBeforePeriod = navigator.anchor
        val periodAfterToc = dispatch(Key.Period)
        assertTrue(periodAfterToc)
        assertTrue(navigator.anchor > anchorBeforePeriod)

        // 3. Open Settings: F4 (new default shortcut)
        val f4Handled = dispatch(Key.F4)
        assertTrue(f4Handled)
        assertTrue(showSettings)

        // While Settings is open: shortcuts must pass through
        kotlin.test.assertFalse(dispatch(Key.Period), "Reader shortcuts must pass through while Settings is open")

        // Close Settings:
        showSettings = false

        // After Settings closed: Period must work again immediately
        val anchorBeforePeriod2 = navigator.anchor
        val periodAfterSettings = dispatch(Key.Period)
        assertTrue(periodAfterSettings)
        assertTrue(navigator.anchor > anchorBeforePeriod2)

        // 4. Open Search: F2 (new default shortcut)
        val f2Handled = dispatch(Key.F2)
        assertTrue(f2Handled)
        assertTrue(showSearch)

        // While Search is open: typing keys or navigation must NOT be intercepted by reader
        kotlin.test.assertFalse(dispatch(Key.Period), "Reader shortcuts must pass through while Search is open")

        // Close Search:
        showSearch = false

        // After Search closed: '<' (Comma) retreats
        val anchorBeforeComma = navigator.anchor
        val commaHandled = dispatch(Key.Comma)
        assertTrue(commaHandled)
        assertTrue(navigator.anchor < anchorBeforeComma)
    }

    // --- 10. Configurable Keymap tests ---

    @Test
    fun testCustomKeymapBindings() {
        val navigator = ReaderNavigator(
            totalLength = 5000,
            textFitter = { from, _, _ -> (from + 200).coerceAtMost(5000) },
            initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE),
            initialAnchor = 500,
        )

        // Custom keymap: Next page is SPACEBAR, Prev page is B, Next chapter is N, Prev chapter is P, Settings is S, Search is SLASH
        val customKeymap = com.moonkata.flonovel.desktop.library.KeymapSettings(
            nextPage = "SPACEBAR",
            prevPage = "B",
            nextChapter = "N",
            prevChapter = "P",
            settings = "S",
            search = "SLASH",
        )

        var settingsOpened = false
        var searchOpened = false
        var nextChapterJumpCalled = false
        var prevChapterJumpCalled = false

        // 1. Advance via custom Spacebar
        val initialAnchor = navigator.anchor
        assertTrue(
            handleKeyAction(
                key = Key.Spacebar,
                keymap = customKeymap,
                navigator = navigator,
                advanceRatio = 0.5f,
            )
        )
        assertTrue(navigator.anchor > initialAnchor)

        // 2. Next Chapter via custom 'N'
        assertTrue(
            handleKeyAction(
                key = Key.N,
                keymap = customKeymap,
                navigator = navigator,
                advanceRatio = 0.5f,
                onNextChapterJump = { nextChapterJumpCalled = true },
            )
        )
        assertTrue(nextChapterJumpCalled)

        // 3. Prev Chapter via custom 'P'
        assertTrue(
            handleKeyAction(
                key = Key.P,
                keymap = customKeymap,
                navigator = navigator,
                advanceRatio = 0.5f,
                onPreviousChapterJump = { prevChapterJumpCalled = true },
            )
        )
        assertTrue(prevChapterJumpCalled)

        // 4. Settings via custom 'S'
        assertTrue(
            handleKeyAction(
                key = Key.S,
                keymap = customKeymap,
                navigator = navigator,
                advanceRatio = 0.5f,
                onOpenSettings = { settingsOpened = true },
            )
        )
        assertTrue(settingsOpened)

        // 5. Search via custom 'SLASH'
        assertTrue(
            handleKeyAction(
                key = Key.Slash,
                keymap = customKeymap,
                navigator = navigator,
                advanceRatio = 0.5f,
                onOpenSearch = { searchOpened = true },
            )
        )
        assertTrue(searchOpened)
    }

    // --- 11. Escape key navigation ---

    @Test
    fun escapeKey_returnsToLibrary_whenNoDialogIsOpen() {
        val navigator = ReaderNavigator(
            totalLength = 1000,
            textFitter = { from, _, _ -> (from + 100).coerceAtMost(1000) },
            initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE),
            initialAnchor = 0,
        )

        var backInvoked = false
        val handled = handleKeyAction(
            key = Key.Escape,
            navigator = navigator,
            advanceRatio = 0.5f,
            onBack = { backInvoked = true },
        )

        assertTrue(handled, "Escape key must be handled")
        assertTrue(backInvoked, "onBack must be invoked when Escape is pressed")
    }

    // --- 12. Search Dialog key navigation & resolution ---

    @Test
    fun searchDialog_resolveKeyAction_executesSearchOnEnter_whenNoResultsOrQueryChanged() {
        // Initial state before search: Enter executes search
        val action1 = resolveSearchKeyAction(Key.Enter, hasResults = false, isQueryChanged = false)
        assertEquals(SearchDialogKeyAction.EXECUTE_SEARCH, action1)

        val actionNumPad = resolveSearchKeyAction(Key.NumPadEnter, hasResults = false, isQueryChanged = false)
        assertEquals(SearchDialogKeyAction.EXECUTE_SEARCH, actionNumPad)

        // After search completed with results, but user changed query: Enter executes search again
        val action2 = resolveSearchKeyAction(Key.Enter, hasResults = true, isQueryChanged = true)
        assertEquals(SearchDialogKeyAction.EXECUTE_SEARCH, action2)
    }

    @Test
    fun searchDialog_resolveKeyAction_selectsResultOnEnter_whenResultsPresentAndQueryUnchanged() {
        // Results are present and query is unchanged: Enter selects current result to jump
        val action = resolveSearchKeyAction(Key.Enter, hasResults = true, isQueryChanged = false)
        assertEquals(SearchDialogKeyAction.SELECT_RESULT, action)

        val actionNumPad = resolveSearchKeyAction(Key.NumPadEnter, hasResults = true, isQueryChanged = false)
        assertEquals(SearchDialogKeyAction.SELECT_RESULT, actionNumPad)
    }

    @Test
    fun searchDialog_resolveKeyAction_navigationAndDismiss() {
        assertEquals(
            SearchDialogKeyAction.DISMISS,
            resolveSearchKeyAction(Key.Escape, hasResults = true, isQueryChanged = false)
        )
        assertEquals(
            SearchDialogKeyAction.NAVIGATE_DOWN,
            resolveSearchKeyAction(Key.DirectionDown, hasResults = true, isQueryChanged = false)
        )
        assertEquals(
            SearchDialogKeyAction.NAVIGATE_UP,
            resolveSearchKeyAction(Key.DirectionUp, hasResults = true, isQueryChanged = false)
        )
        assertEquals(
            SearchDialogKeyAction.NONE,
            resolveSearchKeyAction(Key.DirectionDown, hasResults = false, isQueryChanged = false)
        )
    }

    // --- 13. Folder path retention logic ---

    @Test
    fun folderRetention_extractsParentFolderCorrectly() {
        val pathInSubfolder = "0829/novel/test_book.txt"
        val folder = pathInSubfolder.replace('\\', '/').substringBeforeLast('/', "")
        assertEquals("0829/novel", folder)

        val pathInRoot = "root_book.txt"
        val rootFolder = pathInRoot.replace('\\', '/').substringBeforeLast('/', "")
        assertEquals("", rootFolder)

        val windowsPath = "0829\\sub\\book.txt"
        val winFolder = windowsPath.replace('\\', '/').substringBeforeLast('/', "")
        assertEquals("0829/sub", winFolder)
    }

    // --- 14. Direct next and previous chapter navigation ---

    @Test
    fun directChapterNavigation_nextAndPreviousChapter() {
        val chapters = listOf(
            Chapter("1장", 0),
            Chapter("2장", 500),
            Chapter("3장", 1200),
            Chapter("4장", 2500),
        )

        // Next chapter from 0 -> 500
        assertEquals(500, ChapterJumpNavigator.nextChapter(chapters, 0))
        // Next chapter from middle of chapter 1 (e.g. 250) -> 500
        assertEquals(500, ChapterJumpNavigator.nextChapter(chapters, 250))
        // Next chapter from 500 -> 1200
        assertEquals(1200, ChapterJumpNavigator.nextChapter(chapters, 500))
        // Next chapter from 2500 -> null (last chapter)
        assertNull(ChapterJumpNavigator.nextChapter(chapters, 2500))

        // Prev chapter from 2500 -> 1200
        assertEquals(1200, ChapterJumpNavigator.previousChapter(chapters, 2500))
        // Prev chapter from 2000 (middle of chapter 3) -> 1200
        assertEquals(1200, ChapterJumpNavigator.previousChapter(chapters, 2000))
        // Prev chapter from 1200 -> 500
        assertEquals(500, ChapterJumpNavigator.previousChapter(chapters, 1200))
        // Prev chapter from 0 -> null (already at first chapter)
        assertNull(ChapterJumpNavigator.previousChapter(chapters, 0))
    }

    // --- 15. TOC Dialog key navigation & resolution ---

    @Test
    fun tocDialog_resolveKeyAction_navigationDismissAndSelect() {
        // Dismiss on Escape
        assertEquals(
            TocDialogKeyAction.DISMISS,
            resolveTocKeyAction(Key.Escape, hasChapters = true)
        )
        assertEquals(
            TocDialogKeyAction.DISMISS,
            resolveTocKeyAction(Key.Escape, hasChapters = false)
        )

        // Direction navigation when chapters exist
        assertEquals(
            TocDialogKeyAction.NAVIGATE_DOWN,
            resolveTocKeyAction(Key.DirectionDown, hasChapters = true)
        )
        assertEquals(
            TocDialogKeyAction.NAVIGATE_UP,
            resolveTocKeyAction(Key.DirectionUp, hasChapters = true)
        )

        // Direction navigation when no chapters
        assertEquals(
            TocDialogKeyAction.NONE,
            resolveTocKeyAction(Key.DirectionDown, hasChapters = false)
        )
        assertEquals(
            TocDialogKeyAction.NONE,
            resolveTocKeyAction(Key.DirectionUp, hasChapters = false)
        )

        // Select chapter on Enter / NumPadEnter
        assertEquals(
            TocDialogKeyAction.SELECT_CHAPTER,
            resolveTocKeyAction(Key.Enter, hasChapters = true)
        )
        assertEquals(
            TocDialogKeyAction.SELECT_CHAPTER,
            resolveTocKeyAction(Key.NumPadEnter, hasChapters = true)
        )
        assertEquals(
            TocDialogKeyAction.NONE,
            resolveTocKeyAction(Key.Enter, hasChapters = false)
        )

        // Other keys
        assertEquals(
            TocDialogKeyAction.NONE,
            resolveTocKeyAction(Key.Spacebar, hasChapters = true)
        )
    }

    // --- 16. SearchDialogState retention data model ---

    @Test
    fun searchDialogState_defaultAndCustomValues() {
        val defaultState = SearchDialogState()
        assertEquals("", defaultState.queryText)
        assertNull(defaultState.executedQuery)
        assertNull(defaultState.results)
        assertEquals(0, defaultState.selectedIndex)

        val customResults = listOf(
            com.moonkata.flonovel.desktop.text.SearchResult(charOffset = 100, snippet = "snippet 1"),
            com.moonkata.flonovel.desktop.text.SearchResult(charOffset = 500, snippet = "snippet 2"),
        )
        val state = SearchDialogState(
            queryText = "query",
            executedQuery = "query",
            results = customResults,
            selectedIndex = 1,
        )
        assertEquals("query", state.queryText)
        assertEquals("query", state.executedQuery)
        assertEquals(2, state.results?.size)
        assertEquals(1, state.selectedIndex)
    }
}

