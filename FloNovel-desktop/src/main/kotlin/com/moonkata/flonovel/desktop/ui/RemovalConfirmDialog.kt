package com.moonkata.flonovel.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.i18n.Strings
import com.moonkata.flonovel.desktop.i18n.stringResource
import com.moonkata.flonovel.desktop.library.DeleteAction
import com.moonkata.flonovel.desktop.library.DeleteSettings
import com.moonkata.flonovel.desktop.library.RemovalOutcome
import com.moonkata.flonovel.desktop.library.RemovalRefusal
import java.nio.file.Path

/** A Delete-key press waiting for the user to confirm. */
data class RemovalRequest(val path: Path, val isFolder: Boolean)

@Composable
fun RemovalConfirmDialog(
    request: RemovalRequest,
    deleteSettings: DeleteSettings,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val name = request.path.fileName?.toString() ?: request.path.toString()
    val (title, message, button) = when {
        request.isFolder -> Triple(
            stringResource("removal_confirm_folder_title"),
            stringResource("removal_confirm_folder_message", name),
            stringResource("removal_confirm_delete_button"),
        )
        deleteSettings.action == DeleteAction.MOVE -> Triple(
            stringResource("removal_confirm_move_title"),
            stringResource("removal_confirm_move_message", name, deleteSettings.moveFolder),
            stringResource("removal_confirm_move_button"),
        )
        else -> Triple(
            stringResource("removal_confirm_trash_title"),
            stringResource("removal_confirm_trash_message", name),
            stringResource("removal_confirm_trash_button"),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(440.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF232326),
            elevation = 16.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF374151),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(stringResource("common_cancel"), fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFDC2626),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(button, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

fun removalRefusalMessage(reason: RemovalRefusal): String = when (reason) {
    RemovalRefusal.NOT_FOUND -> Strings.get("removal_refused_not_found")
    RemovalRefusal.FOLDER_NOT_EMPTY -> Strings.get("removal_refused_folder_not_empty")
    RemovalRefusal.TRASH_UNSUPPORTED -> Strings.get("removal_refused_trash_unsupported")
    RemovalRefusal.TRASH_FAILED -> Strings.get("removal_refused_trash_failed")
    RemovalRefusal.MOVE_FOLDER_NOT_SET -> Strings.get("removal_refused_move_folder_not_set")
    RemovalRefusal.MOVE_FOLDER_MISSING -> Strings.get("removal_refused_move_folder_missing")
    RemovalRefusal.MOVE_FOLDER_INSIDE_LIBRARY -> Strings.get("removal_refused_move_folder_inside_library")
}

fun removalOutcomeMessage(outcome: RemovalOutcome): String = when (outcome) {
    is RemovalOutcome.Trashed -> Strings.get("removal_done_trashed", outcome.path.fileName.toString())
    // Show the destination name: it differs from the source when a _N suffix was added.
    is RemovalOutcome.Moved -> Strings.get("removal_done_moved", outcome.to.fileName.toString())
    is RemovalOutcome.FolderRemoved -> Strings.get("removal_done_folder", outcome.path.fileName.toString())
    is RemovalOutcome.Refused -> removalRefusalMessage(outcome.reason)
    is RemovalOutcome.Failed -> Strings.get("removal_failed", outcome.message)
}
