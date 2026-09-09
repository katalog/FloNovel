package com.moonkata.flonovel.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.audio.RadioPlayer
import com.moonkata.flonovel.desktop.audio.RadioStreamCatalog
import com.moonkata.flonovel.desktop.audio.RadioStreamItem
import com.moonkata.flonovel.desktop.i18n.stringResource

/**
 * Dialog overlay allowing users to pick an internet radio stream and set a countdown timer.
 */
@Composable
fun RadioDialog(
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val streams = remember { RadioStreamCatalog.loadStreams() }
    val playbackState by RadioPlayer.state

    var selectedStream by remember {
        mutableStateOf(
            streams.firstOrNull { it.name == playbackState.streamName } ?: streams.firstOrNull()
        )
    }
    var durationMinutes by remember { mutableStateOf(60) }

    // Dimmed background overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF252528),
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
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MediaControlIcon(
                                shape = MediaControlShape.PLAY,
                                size = 18.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource("radio_dialog_title"),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = stringResource("radio_dialog_subtitle"),
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }

                    Text(
                        text = "✕",
                        color = Color(0xFFAAAAAA),
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFF3E3E42))
                Spacer(modifier = Modifier.height(16.dp))

                // Stream list section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource("radio_stream_selection"),
                        color = Color(0xFFDDDDDD),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "⚙ ${stringResource("radio_edit_streams")}",
                        color = Color(0xFF60A5FA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { RadioStreamCatalog.openConfigFile() }
                            .padding(4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    streams.forEach { stream ->
                        val isSelected = (stream.name == selectedStream?.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.16f) else Color(0xFF1E1E22),
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF333336),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { selectedStream = stream }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Radio selection indicator
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(16.dp)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF6B7280),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height(8.dp)
                                            .background(Color(0xFF3B82F6), CircleShape),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = stream.name,
                                color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Playback duration section
                Text(
                    text = stringResource("radio_duration_label"),
                    color = Color(0xFFDDDDDD),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E22), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF333336), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Decrement buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdjustButton("-10") {
                            durationMinutes = (durationMinutes - 10).coerceAtLeast(1)
                        }
                        AdjustButton("-1") {
                            durationMinutes = (durationMinutes - 1).coerceAtLeast(1)
                        }
                    }

                    // Current minutes display
                    Text(
                        text = stringResource("radio_minutes_unit", durationMinutes),
                        color = Color(0xFF60A5FA),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    // Increment buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdjustButton("+1") {
                            durationMinutes = (durationMinutes + 1).coerceAtMost(1440)
                        }
                        AdjustButton("+10") {
                            durationMinutes = (durationMinutes + 10).coerceAtMost(1440)
                        }
                    }
                }

                // Preset chips
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(30, 60, 90, 120).forEach { preset ->
                        val isCurrent = (durationMinutes == preset)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isCurrent) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color(0xFF2A2A2E),
                                    RoundedCornerShape(6.dp),
                                )
                                .border(
                                    1.dp,
                                    if (isCurrent) Color(0xFF3B82F6) else Color(0xFF3E3E44),
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable { durationMinutes = preset }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource("radio_minutes_unit", preset),
                                color = if (isCurrent) Color(0xFF93C5FD) else Color(0xFFAAAAAA),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playbackState.isPlaying) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .clickable {
                                    RadioPlayer.stop()
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MediaControlIcon(
                                    shape = MediaControlShape.STOP,
                                    size = 14.dp,
                                    circleColor = Color(0xFFEF4444),
                                    iconColor = Color.White,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource("radio_stop_playback"),
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF333338), RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource("common_cancel"),
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp,
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    val target = selectedStream
                    Box(
                        modifier = Modifier
                            .background(
                                if (target != null) Color(0xFF3B82F6) else Color(0xFF3B82F6).copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable(enabled = target != null) {
                                if (target != null) {
                                    RadioPlayer.play(
                                        stream = target,
                                        durationMinutes = durationMinutes,
                                        onError = { error ->
                                            onToast(error)
                                        },
                                    )
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MediaControlIcon(
                                shape = MediaControlShape.PLAY,
                                size = 14.dp,
                                circleColor = Color.White.copy(alpha = 0.25f),
                                iconColor = Color.White,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource("radio_start_playback"),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(Color(0xFF2A2A2E), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF44444A), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color(0xFFDDDDDD),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
