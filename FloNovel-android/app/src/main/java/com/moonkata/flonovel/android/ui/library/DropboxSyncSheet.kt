package com.moonkata.flonovel.android.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonkata.flonovel.android.R
import com.moonkata.flonovel.android.data.datastore.ReaderSettings
import com.moonkata.flonovel.android.data.sync.DropboxAuthSession
import com.moonkata.flonovel.android.data.sync.DropboxConfig
import com.moonkata.flonovel.android.data.sync.SecretResult

/**
 * Dropbox file sync — sign in, then sync both ways. Replaces the PC tray-server sheet.
 *
 * There is nothing to type: Dropbox's own login *is* the pairing step, which is why the QR flow went
 * away with it. The sheet therefore only ever shows one of two
 * states — signed out, or signed in with a "sync now" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropboxSyncSheet(viewModel: LibraryViewModel, settings: ReaderSettings, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.dropboxState.collectAsState()
    val redirect by DropboxAuthSession.redirect.collectAsState()
    val isLinked = settings.dropboxRefreshToken.isNotBlank()

    // The browser hands the authorization code back through a separate Activity, so the sheet picks
    // it up here rather than from a button callback.
    LaunchedEffect(redirect) {
        redirect?.let { viewModel.completeDropboxSignIn(it) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.dropbox_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.dropbox_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            if (isLinked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32))
                    Text(
                        settings.dropboxAccountEmail.ifBlank { stringResource(R.string.dropbox_connected) },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.syncFromDropbox() },
                    enabled = !state.isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.dropbox_sync_now))
                    }
                }
                // Linked before two-way sync asked for write access: downloads still work, but this
                // phone's changes wait. One tap re-runs sign-in with the new permission.
                if (!DropboxConfig.canWrite(settings.dropboxGrantedScopes)) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.dropbox_reconnect_for_write),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = { viewModel.startDropboxSignIn() },
                        enabled = !state.isConnecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.dropbox_reconnect))
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Position sync rides on a secret only the Desktop app can create, so its state is
                // shown next to the account: "connected to Dropbox" alone does not mean positions
                // are syncing, and without this line that difference is invisible.
                PositionSyncRow(
                    isVerified = settings.supabaseVerifiedSecret.isNotBlank() &&
                        settings.supabaseVerifiedSecret == settings.supabaseSharedSecret,
                    secretState = state.secretState,
                    onRetry = { viewModel.refreshSharedSecret() },
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.signOutOfDropbox() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dropbox_sign_out))
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.startDropboxSignIn() },
                    enabled = !state.isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isConnecting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.dropbox_sign_in))
                    }
                }
            }

            state.progress?.let { progress ->
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.dropbox_progress, progress.completed + 1, progress.total),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    progress.currentRelativePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.result?.let { result ->
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(
                        R.string.dropbox_result_two_way,
                        result.downloaded,
                        result.uploaded,
                        result.deletedLocal + result.deletedRemote,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (result.failed > 0) {
                    Text(
                        stringResource(R.string.dropbox_result_failed_suffix, result.failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    // Which books, so a failure that keeps coming back can be looked into.
                    result.failedPaths.take(5).forEach { path ->
                        Text(
                            "• $path",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (result.deferred > 0) {
                    Text(
                        stringResource(R.string.dropbox_result_deferred, result.deferred),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.conflicts > 0) {
                    Text(
                        stringResource(R.string.dropbox_result_conflicts, result.conflicts),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                // Each of these means the sync deliberately did less than it could. Saying nothing
                // would leave the user believing the two sides now match, when they do not.
                if (result.waitingForWriteAccess > 0) {
                    Text(
                        stringResource(R.string.dropbox_result_waiting_for_write, result.waitingForWriteAccess),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (isLinked) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                // Deleting here now reaches the PC too; say so where the user syncs, since it used to
                // be the other way round.
                Text(
                    stringResource(R.string.dropbox_two_way_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One line saying whether reading-position sync is actually live, plus a retry.
 *
 * [SecretResult.NotFound] is the case worth spelling out: it means the Dropbox account is fine but
 * the Desktop app has never run, so it has never written `/.flonovel/secret.json`. "Failed" would
 * send the user looking in the wrong place entirely.
 */
@Composable
private fun PositionSyncRow(
    isVerified: Boolean,
    secretState: SecretResult?,
    onRetry: () -> Unit,
) {
    Column {
        Text(stringResource(R.string.dropbox_position_sync_label), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(2.dp))
        when {
            isVerified -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32))
                Text(
                    stringResource(R.string.dropbox_position_sync_on),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                )
            }

            else -> {
                val messageRes = when (secretState) {
                    is SecretResult.NotFound -> R.string.dropbox_secret_not_found
                    is SecretResult.Failed -> R.string.dropbox_secret_failed
                    // NotLinked cannot reach here (the row only renders when linked); null means
                    // the fetch has not run yet this session.
                    else -> R.string.dropbox_secret_pending
                }
                Text(
                    stringResource(messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.dropbox_secret_retry))
                }
            }
        }
    }
}
