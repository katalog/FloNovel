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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.i18n.stringResource

/**
 * Asks before a sync deletes more than usual. Skipping is the highlighted action (Enter and Esc
 * both skip): this dialog exists for the case where the deletions are a mistake, such as another
 * Dropbox account or a reset app folder, so going ahead must be a deliberate click.
 */
@Composable
fun MassDeletionDialog(
    paths: List<String>,
    onDelete: () -> Unit,
    onSkip: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onSkip),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF232326),
            elevation = 16.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource("sync_mass_delete_title", paths.size),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource("sync_mass_delete_message"),
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                for (path in paths.take(PREVIEW_COUNT)) {
                    Text(
                        text = "• $path",
                        color = Color(0xFFD1D5DB),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (paths.size > PREVIEW_COUNT) {
                    Text(
                        text = stringResource("sync_mass_delete_more", paths.size - PREVIEW_COUNT),
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF7F1D1D),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(stringResource("sync_mass_delete_confirm"), fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onSkip,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF2563EB),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(stringResource("sync_mass_delete_skip"), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private const val PREVIEW_COUNT = 8
