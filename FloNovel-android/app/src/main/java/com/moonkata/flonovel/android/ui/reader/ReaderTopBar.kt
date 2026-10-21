package com.moonkata.flonovel.android.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonkata.flonovel.android.R
import com.moonkata.flonovel.android.data.datastore.TouchZoneMode
import com.moonkata.flonovel.android.ui.theme.ReaderColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    readerColors: ReaderColors,
    onBack: () -> Unit,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    touchZoneMode: TouchZoneMode = TouchZoneMode.STANDARD_3_COLUMN,
    smartForwardAction: SmartForwardAction = SmartForwardAction.NEXT_PAGE,
) {
    Surface(color = readerColors.background, contentColor = readerColors.text, shadowElevation = 4.dp) {
        Column {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back_desc))
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.reader_settings_desc)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = readerColors.background,
                    titleContentColor = readerColors.text,
                    navigationIconContentColor = readerColors.text,
                    actionIconContentColor = readerColors.text,
                ),
            )
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToc) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.reader_toc_desc)) }
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.reader_search_desc)) }
            }
            if (touchZoneMode == TouchZoneMode.SMART_GESTURE) {
                SmartGestureGuideBar(
                    smartForwardAction = smartForwardAction,
                    readerColors = readerColors,
                )
            }
        }
    }
}

@Composable
private fun SmartGestureGuideBar(
    smartForwardAction: SmartForwardAction,
    readerColors: ReaderColors,
) {
    Surface(
        color = readerColors.background.copy(alpha = 0.95f),
        contentColor = readerColors.text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, readerColors.text.copy(alpha = 0.15f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${stringResource(R.string.reader_smart_guide_title)} · ${stringResource(R.string.reader_smart_guide_tap)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = readerColors.text.copy(alpha = 0.8f),
                )
                val actionLabel = when (smartForwardAction) {
                    SmartForwardAction.NEXT_PAGE -> stringResource(R.string.reader_smart_action_next_page)
                    SmartForwardAction.NEXT_CHAPTER -> stringResource(R.string.reader_smart_action_next_chapter)
                    SmartForwardAction.NEXT_CHAPTER_JUMP -> stringResource(R.string.reader_smart_action_next_chapter_jump)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = stringResource(R.string.reader_smart_guide_current_action, actionLabel),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.reader_smart_guide_swipe_up),
                    style = MaterialTheme.typography.bodySmall,
                    color = readerColors.text.copy(alpha = 0.9f),
                )
                Text(
                    text = stringResource(R.string.reader_smart_guide_swipe_down),
                    style = MaterialTheme.typography.bodySmall,
                    color = readerColors.text.copy(alpha = 0.9f),
                )
                Text(
                    text = stringResource(R.string.reader_smart_guide_swipe_left),
                    style = MaterialTheme.typography.bodySmall,
                    color = readerColors.text.copy(alpha = 0.9f),
                )
                Text(
                    text = stringResource(R.string.reader_smart_guide_swipe_right),
                    style = MaterialTheme.typography.bodySmall,
                    color = readerColors.text.copy(alpha = 0.9f),
                )
            }
        }
    }
}
