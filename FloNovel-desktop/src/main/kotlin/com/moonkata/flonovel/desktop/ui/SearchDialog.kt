package com.moonkata.flonovel.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.moonkata.flonovel.desktop.i18n.stringResource
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderLayout
import com.moonkata.flonovel.desktop.text.Search
import com.moonkata.flonovel.desktop.text.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Key actions resolved in SearchDialog.
 */
enum class SearchDialogKeyAction {
    DISMISS,
    NAVIGATE_DOWN,
    NAVIGATE_UP,
    EXECUTE_SEARCH,
    SELECT_RESULT,
    NONE,
}

fun resolveSearchKeyAction(
    key: Key,
    hasResults: Boolean,
    isQueryChanged: Boolean,
): SearchDialogKeyAction = when (key) {
    Key.Escape -> SearchDialogKeyAction.DISMISS
    Key.DirectionDown -> if (hasResults) SearchDialogKeyAction.NAVIGATE_DOWN else SearchDialogKeyAction.NONE
    Key.DirectionUp -> if (hasResults) SearchDialogKeyAction.NAVIGATE_UP else SearchDialogKeyAction.NONE
    Key.Enter, Key.NumPadEnter -> {
        if (!hasResults || isQueryChanged) {
            SearchDialogKeyAction.EXECUTE_SEARCH
        } else {
            SearchDialogKeyAction.SELECT_RESULT
        }
    }
    else -> SearchDialogKeyAction.NONE
}

/**
 * Preserved search state across dialog opens within the same book session.
 */
data class SearchDialogState(
    val queryText: String = "",
    val executedQuery: String? = null,
    val results: List<SearchResult>? = null,
    val selectedIndex: Int = 0,
)

/**
 * Search Dialog Overlay for reader text.
 *
 * Strict Requirements:
 * - Search executes ONLY ON SUBMISSION (Enter pressed or Search button clicked).
 *   Typing in the input field does NOT execute search (explicit contract).
 * - No upper limit on search result count.
 * - Keyboard navigation: Down/Up to move selection, Enter to jump, Esc to dismiss.
 * - Preserves previous query and search results when reopened so user can navigate directly.
 */
@Composable
fun SearchDialog(
    fullText: String,
    onResultSelected: (SearchResult) -> Unit,
    onDismiss: () -> Unit,
    readerLayout: ReaderLayout? = null,
    initialState: SearchDialogState = SearchDialogState(),
    onStateChanged: ((SearchDialogState) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialState.queryText,
                selection = TextRange(initialState.queryText.length),
            )
        )
    }
    var queryText by remember { mutableStateOf(initialState.queryText) }
    var executedQuery by remember { mutableStateOf(initialState.executedQuery) }
    var results by remember { mutableStateOf(initialState.results) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedResultIndex by remember {
        val count = initialState.results?.size ?: 0
        mutableStateOf(if (count > 0) initialState.selectedIndex.coerceIn(0, count - 1) else 0)
    }

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    fun notifyStateChanged(
        newQuery: String = queryText,
        newExecutedQuery: String? = executedQuery,
        newResults: List<SearchResult>? = results,
        newIndex: Int = selectedResultIndex,
    ) {
        onStateChanged?.invoke(
            SearchDialogState(
                queryText = newQuery,
                executedQuery = newExecutedQuery,
                results = newResults,
                selectedIndex = newIndex,
            )
        )
    }

    // Persist latest state to parent when dialog closes or unmounts
    DisposableEffect(Unit) {
        onDispose {
            notifyStateChanged()
        }
    }

    // Scroll to restored selected item on open if results exist
    LaunchedEffect(Unit) {
        if (selectedResultIndex > 0) {
            listState.scrollToItem((selectedResultIndex - 2).coerceAtLeast(0))
        }
    }

    fun executeSearch() {
        val trimmed = queryText.trim()
        if (trimmed.isEmpty()) return
        isSearching = true
        executedQuery = trimmed
        selectedResultIndex = 0
        coroutineScope.launch {
            val searchResults = withContext(Dispatchers.Default) {
                Search.search(fullText, trimmed)
            }
            results = searchResults
            isSearching = false
            if (searchResults.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
    }

    val handleKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
        if (event.type == KeyEventType.KeyDown) {
            val isQueryChanged = results == null || executedQuery != queryText.trim()
            val hasResults = results != null && results!!.isNotEmpty()
            when (resolveSearchKeyAction(event.key, hasResults, isQueryChanged)) {
                SearchDialogKeyAction.DISMISS -> {
                    onDismiss()
                    true
                }
                SearchDialogKeyAction.NAVIGATE_DOWN -> {
                    if (hasResults) {
                        selectedResultIndex = (selectedResultIndex + 1).coerceAtMost(results!!.size - 1)
                        coroutineScope.launch { listState.animateScrollToItem(selectedResultIndex) }
                    }
                    true
                }
                SearchDialogKeyAction.NAVIGATE_UP -> {
                    if (hasResults) {
                        selectedResultIndex = (selectedResultIndex - 1).coerceAtLeast(0)
                        coroutineScope.launch { listState.animateScrollToItem(selectedResultIndex) }
                    }
                    true
                }
                SearchDialogKeyAction.EXECUTE_SEARCH -> {
                    executeSearch()
                    true
                }
                SearchDialogKeyAction.SELECT_RESULT -> {
                    if (hasResults && selectedResultIndex in results!!.indices) {
                        onResultSelected(results!![selectedResultIndex])
                    }
                    true
                }
                SearchDialogKeyAction.NONE -> false
            }
        } else if (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Escape) {
            // Consume key up so Enter/Escape never bubbles up to the modal backdrop
            true
        } else {
            false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Modal backdrop
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(580.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* swallow clicks within dialog area */ }
                )
                .onPreviewKeyEvent(handleKeyEvent),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF232326),
            elevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource("search_title"),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "✕",
                        color = Color(0xFFAAAAAA),
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Input Box + Submit Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF18181A), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF444448), RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = stringResource("search_placeholder"),
                                color = Color(0xFF777777),
                                fontSize = 14.sp,
                            )
                        }
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                                queryText = it.text
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent(handleKeyEvent),
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color(0xFF60A5FA)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { executeSearch() }),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = { executeSearch() },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF2563EB),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(6.dp),
                        enabled = !isSearching && queryText.isNotBlank(),
                    ) {
                        Text(
                            text = if (isSearching) stringResource("common_searching") else stringResource("common_search"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = Color(0xFF38383C))
                Spacer(modifier = Modifier.height(10.dp))

                // Status & Results
                val currentResults = results
                when {
                    isSearching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource("search_status_executing"),
                                color = Color(0xFFAAAAAA),
                                fontSize = 14.sp,
                            )
                        }
                    }

                    currentResults == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource("search_empty_prompt"),
                                color = Color(0xFF888888),
                                fontSize = 13.sp,
                            )
                        }
                    }

                    currentResults.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource("search_no_results_title", executedQuery),
                                    color = Color(0xFFDDDDDD),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource("search_no_results_hint"),
                                    color = Color(0xFF888888),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    else -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource("search_result_count", currentResults.size),
                                color = Color(0xFF93C5FD),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource("search_navigation_hint"),
                                color = Color(0xFF888888),
                                fontSize = 11.sp,
                            )
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                        ) {
                            itemsIndexed(currentResults) { index, item ->
                                val isSelected = index == selectedResultIndex
                                val percentage = if (fullText.isNotEmpty()) {
                                    ((item.charOffset.toDouble() / fullText.length) * 100).toInt()
                                } else 0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.25f) else Color.Transparent,
                                            RoundedCornerShape(6.dp),
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) Color(0xFF60A5FA).copy(alpha = 0.7f) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                        .clickable {
                                            selectedResultIndex = index
                                            onResultSelected(item)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.snippet,
                                        color = if (isSelected) Color(0xFF93C5FD) else Color(0xFFE5E7EB),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    )

                                    val paneLabel = if (readerLayout != null && readerLayout.paneMode == PaneMode.TWO) {
                                        val left = readerLayout.leftPane
                                        val right = readerLayout.rightPane
                                        when {
                                            item.charOffset >= left.startOffset && item.charOffset < left.endOffset -> stringResource("search_left_pane")
                                            right != null && item.charOffset >= right.startOffset && item.charOffset < right.endOffset -> stringResource("search_right_pane")
                                            else -> null
                                        }
                                    } else null

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (paneLabel != null) {
                                            Text(
                                                text = "[$paneLabel]",
                                                color = Color(0xFF60A5FA),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(end = 6.dp),
                                            )
                                        }
                                        Text(
                                            text = "$percentage%",
                                            color = if (isSelected) Color(0xFF93C5FD) else Color(0xFF9CA3AF),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
}
