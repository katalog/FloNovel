package com.moonkata.flonovel.android.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonkata.flonovel.android.R
import kotlinx.coroutines.delay

/**
 * Modern floating pill badge displayed at the bottom-right of the library screen to reflect
 * sync progress, success, failure, or prompt the user to link Dropbox. Tapping it opens the
 * detailed [DropboxSyncSheet].
 */
@Composable
fun SyncStatusFloatingBadge(
    state: DropboxUiState,
    showUnlinkedNotice: Boolean,
    onOpenSyncSheet: () -> Unit,
    onDismissResult: () -> Unit,
    onDismissUnlinkedNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVisible = state.isSyncing || state.result != null || state.errorMessage != null || showUnlinkedNotice

    // Auto-dismiss timers for transient states
    LaunchedEffect(state.result) {
        if (state.result != null) {
            delay(3500L)
            onDismissResult()
        }
    }

    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            delay(4000L)
            onDismissResult()
        }
    }

    LaunchedEffect(showUnlinkedNotice) {
        if (showUnlinkedNotice) {
            delay(3500L)
            onDismissUnlinkedNotice()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            onClick = onOpenSyncSheet,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .padding(16.dp)
                .widthIn(min = 120.dp, max = 280.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        state.isSyncing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val syncText = state.progress?.let { progress ->
                                stringResource(R.string.dropbox_sync_status_syncing_progress, progress.completed + 1, progress.total)
                            } ?: stringResource(R.string.dropbox_sync_status_syncing)

                            Text(
                                text = syncText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        state.result != null -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(18.dp),
                            )
                            val result = state.result
                            val changed = result.downloaded + result.updated
                            val summary = if (changed > 0) {
                                "${stringResource(R.string.dropbox_sync_status_completed)} · ${stringResource(R.string.dropbox_sync_status_downloaded, changed)}"
                            } else {
                                "${stringResource(R.string.dropbox_sync_status_completed)} (${stringResource(R.string.dropbox_sync_status_up_to_date)})"
                            }
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        state.errorMessage != null -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.dropbox_sync_status_failed),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        showUnlinkedNotice -> {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.dropbox_sync_status_not_linked),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // If multi-file sync is in progress, show subtle progress bar below the line
                state.progress?.let { progress ->
                    if (progress.total > 1) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress.completed.toFloat() / progress.total },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                        )
                    }
                }
            }
        }
    }
}
