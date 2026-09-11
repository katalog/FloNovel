package com.moonkata.flonovel.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.i18n.stringResource
import com.moonkata.flonovel.desktop.text.Chapter
import kotlinx.coroutines.launch

/**
 * Key actions resolved in TocDialog.
 */
enum class TocDialogKeyAction {
    DISMISS,
    NAVIGATE_DOWN,
    NAVIGATE_UP,
    SELECT_CHAPTER,
    NONE,
}

fun resolveTocKeyAction(key: Key, hasChapters: Boolean): TocDialogKeyAction = when (key) {
    Key.Escape -> TocDialogKeyAction.DISMISS
    Key.DirectionDown -> if (hasChapters) TocDialogKeyAction.NAVIGATE_DOWN else TocDialogKeyAction.NONE
    Key.DirectionUp -> if (hasChapters) TocDialogKeyAction.NAVIGATE_UP else TocDialogKeyAction.NONE
    Key.Enter, Key.NumPadEnter -> if (hasChapters) TocDialogKeyAction.SELECT_CHAPTER else TocDialogKeyAction.NONE
    else -> TocDialogKeyAction.NONE
}

/**
 * Finds the currently active chapter based on the reading position [anchor].
 * Returns the latest chapter whose [Chapter.charOffset] <= [anchor].
 */
fun findCurrentChapter(chapters: List<Chapter>, anchor: Int): Chapter? {
    var active: Chapter? = null
    for (ch in chapters) {
        if (ch.charOffset <= anchor) {
            active = ch
        } else {
            break
        }
    }
    return active
}

/**
 * Table of Contents dialog overlay.
 *
 * Requirements (T-10):
 * - Displays detected chapters, jumping to selected chapter offset on click or Enter.
 * - Keyboard navigation: Up/Down moves selected chapter highlight, Enter jumps to selected chapter.
 * - '##' preset chapters without length limit (titles exceeding 60 characters are included).
 * - Ultra-long titles (even 10,000+ characters) are cleanly truncated to a single line with ellipsis.
 * - 0 chapters detected is a valid normal state: displays clean empty content without error or warning.
 * - Highlights the currently active chapter and keyboard-selected chapter.
 * - Progress column shows integer percentage (e.g. 14%) matching Android TOC format.
 */
@Composable
fun TocDialog(
    chapters: List<Chapter>?,
    currentAnchor: Int,
    totalCharCount: Int,
    onChapterSelected: (Chapter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeChapter = remember(chapters, currentAnchor) {
        if (chapters != null) findCurrentChapter(chapters, currentAnchor) else null
    }

    val activeIndex = remember(chapters, activeChapter) {
        if (chapters != null && activeChapter != null) chapters.indexOf(activeChapter) else -1
    }

    var selectedIndex by remember(chapters) {
        mutableStateOf(if (activeIndex >= 0) activeIndex else 0)
    }

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            selectedIndex = activeIndex
            listState.scrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val handleKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
        if (event.type == KeyEventType.KeyDown) {
            val hasChapters = !chapters.isNullOrEmpty()
            when (resolveTocKeyAction(event.key, hasChapters)) {
                TocDialogKeyAction.DISMISS -> {
                    onDismiss()
                    true
                }
                TocDialogKeyAction.NAVIGATE_DOWN -> {
                    if (hasChapters) {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(chapters!!.size - 1)
                        coroutineScope.launch { listState.animateScrollToItem(selectedIndex) }
                    }
                    true
                }
                TocDialogKeyAction.NAVIGATE_UP -> {
                    if (hasChapters) {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        coroutineScope.launch { listState.animateScrollToItem(selectedIndex) }
                    }
                    true
                }
                TocDialogKeyAction.SELECT_CHAPTER -> {
                    if (chapters != null && selectedIndex in chapters.indices) {
                        onChapterSelected(chapters[selectedIndex])
                    }
                    true
                }
                TocDialogKeyAction.NONE -> false
            }
        } else if (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Escape) {
            true
        } else {
            false
        }
    }

    // Dimmed background overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // Modal Card
        Surface(
            modifier = Modifier
                .width(520.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent(handleKeyEvent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* swallow clicks within dialog area */ }
                ),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF252528),
            elevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                // Header: Title, Count, Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource("toc_title"),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        val subtitleText = when {
                            chapters == null -> stringResource("common_scan_in_progress")
                            chapters.isEmpty() -> stringResource("toc_empty")
                            else -> stringResource("toc_chapter_count", chapters.size)
                        }
                        Text(
                            text = subtitleText,
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    Text(
                        text = "✕",
                        color = Color(0xFFAAAAAA),
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFF3E3E42))
                Spacer(modifier = Modifier.height(12.dp))

                // Body: Loading, Empty, or List
                when {
                    chapters == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource("toc_scanning"),
                                color = Color(0xFFAAAAAA),
                                fontSize = 14.sp,
                            )
                        }
                    }

                    chapters.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource("toc_no_chapters_title"),
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource("toc_no_chapters_desc"),
                                    color = Color(0xFF888888),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp),
                        ) {
                            itemsIndexed(chapters) { index, chapter ->
                                val isActive = chapter == activeChapter
                                val isSelected = index == selectedIndex
                                val percentage = if (totalCharCount > 0) {
                                    ((chapter.charOffset.toDouble() / totalCharCount) * 100).toInt()
                                } else 0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            when {
                                                isSelected -> Color(0xFF3B82F6).copy(alpha = 0.25f)
                                                isActive -> Color(0xFF3B82F6).copy(alpha = 0.12f)
                                                else -> Color.Transparent
                                            },
                                            RoundedCornerShape(6.dp),
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else if (isActive) 1.dp else 0.dp,
                                            color = when {
                                                isSelected -> Color(0xFF60A5FA).copy(alpha = 0.8f)
                                                isActive -> Color(0xFF3B82F6).copy(alpha = 0.4f)
                                                else -> Color.Transparent
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                        .clickable {
                                            selectedIndex = index
                                            onChapterSelected(chapter)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Title with single-line ellipsis for long titles
                                    Text(
                                        text = chapter.displayTitle,
                                        color = when {
                                            isSelected -> Color(0xFF93C5FD)
                                            isActive -> Color(0xFF60A5FA)
                                            else -> Color.White
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected || isActive) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    )

                                    // Progress percentage (integer % format matching Android TOC)
                                    Text(
                                        text = "$percentage%",
                                        color = when {
                                            isSelected -> Color(0xFF93C5FD)
                                            isActive -> Color(0xFF60A5FA)
                                            else -> Color(0xFFAAAAAA)
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isActive) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
