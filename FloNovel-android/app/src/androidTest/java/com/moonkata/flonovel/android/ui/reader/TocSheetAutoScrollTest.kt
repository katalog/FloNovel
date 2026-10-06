package com.moonkata.flonovel.android.ui.reader

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moonkata.flonovel.android.R
import com.moonkata.flonovel.android.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opening the table-of-contents sheet must show the chapter currently being read immediately,
 * without manual scrolling (auto-scroll) — a regression test for the issue where, in a real novel
 * with many chapters, the TOC had to be scrolled from the top every time it was opened.
 */
@RunWith(AndroidJUnit4::class)
class TocSheetAutoScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun openingToc_scrollsToCurrentChapterWithoutManualScroll_andJumpsOnClick() {
        val chapters = (1..100).map { Chapter(title = "제${it}장", charOffset = it * 1000) }
        // The current position is in the middle of chapter 60 — without auto-scroll, it would show
        // starting from the top (chapter 1), so "Chapter 60" should normally not be visible at all
        // unless the LazyColumn happens to compose off-screen items too.
        val currentOffset = chapters[59].charOffset + 500
        var jumpedTo: Int? = null

        composeTestRule.setContent {
            MaterialTheme {
                TocSheet(
                    chapters = chapters,
                    currentOffset = currentOffset,
                    fullTextLength = 200_000,
                    onJump = { jumpedTo = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("제60장").assertExists()

        composeTestRule.onNodeWithText("제60장").performClick()
        assertEquals(chapters[59].charOffset, jumpedTo)
    }

    @Test
    fun scrollingAndFlingingNeverDismissOrSelectAChapter() {
        var dismissals = 0
        var jumps = 0
        composeTestRule.setContent {
            MaterialTheme {
                TocSheet(
                    chapters = (1..100).map { Chapter("Chapter $it", it * 1000) },
                    currentOffset = 50_000,
                    fullTextLength = 100_000,
                    onJump = { jumps++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        repeat(3) {
            composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeDown() }
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNode(hasScrollAction()).assertExists()
        assertEquals(0, dismissals)
        assertEquals(0, jumps)
        composeTestRule.onNodeWithContentDescription(application.getString(R.string.action_close)).performClick()
        assertEquals(1, dismissals)
    }

    @Test
    fun tappingOutsidePanelDismissesTheToc() {
        var dismissals = 0
        composeTestRule.setContent {
            MaterialTheme {
                TocSheet(emptyList(), 0, 0, {}, { dismissals++ })
            }
        }
        composeTestRule.onNodeWithTag("toc_scrim").performTouchInput { click(Offset(center.x, 8f)) }
        composeTestRule.waitForIdle()
        assertEquals(1, dismissals)
    }

    @Test
    fun emptyChapterList_showsNoTocMessage() {
        composeTestRule.setContent {
            MaterialTheme {
                TocSheet(chapters = emptyList(), currentOffset = 0, fullTextLength = 0, onJump = {}, onDismiss = {})
            }
        }

        composeTestRule.onNodeWithText(application.getString(R.string.toc_empty)).assertExists()
    }

    @Test
    fun chapterRow_showsItsPositionAsPercentOfTheBook() {
        val chapters = listOf(Chapter(title = "제1장", charOffset = 50_000))

        composeTestRule.setContent {
            MaterialTheme {
                TocSheet(chapters = chapters, currentOffset = 0, fullTextLength = 100_000, onJump = {}, onDismiss = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("50%").assertExists()
    }
}
