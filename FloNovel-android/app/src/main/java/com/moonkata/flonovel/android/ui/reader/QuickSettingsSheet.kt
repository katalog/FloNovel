package com.moonkata.flonovel.android.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moonkata.flonovel.android.R
import com.moonkata.flonovel.android.data.datastore.AutoAdvanceMode
import com.moonkata.flonovel.android.data.datastore.OrientationLock
import com.moonkata.flonovel.android.data.datastore.PageGestureAction
import com.moonkata.flonovel.android.data.datastore.PageTransitionAnimation
import com.moonkata.flonovel.android.data.datastore.PageTurnMode
import com.moonkata.flonovel.android.data.datastore.ReaderSettings
import com.moonkata.flonovel.android.data.datastore.ThemePreset
import com.moonkata.flonovel.android.data.datastore.TouchZoneMode
import com.moonkata.flonovel.android.ui.SettingsController
import com.moonkata.flonovel.android.ui.theme.ReaderColors
import com.moonkata.flonovel.android.ui.theme.ReaderThemePresets

enum class SettingsTab {
    VIEW,
    CONTROLS,
    CHAPTERS,
    SYNC,
}

/**
 * Full-screen settings dialog divided into category tabs matching the desktop app's organization.
 * Supports back-button dismiss and replaces the legacy sliding bottom sheet.
 * [homeFolderName] and [onChangeHomeFolder] are the library screen's additions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
    viewModel: SettingsController,
    settings: ReaderSettings,
    onDismiss: () -> Unit,
    homeFolderName: String? = null,
    onChangeHomeFolder: (() -> Unit)? = null,
    initialTab: SettingsTab = SettingsTab.VIEW,
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    var showFontPicker by remember { mutableStateOf(false) }
    var showChapterPatterns by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_close),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Tab(
                        selected = selectedTab == SettingsTab.VIEW,
                        onClick = { selectedTab = SettingsTab.VIEW },
                        text = { Text(stringResource(R.string.settings_tab_view), maxLines = 1) },
                    )
                    Tab(
                        selected = selectedTab == SettingsTab.CONTROLS,
                        onClick = { selectedTab = SettingsTab.CONTROLS },
                        text = { Text(stringResource(R.string.settings_tab_controls), maxLines = 1) },
                    )
                    Tab(
                        selected = selectedTab == SettingsTab.CHAPTERS,
                        onClick = { selectedTab = SettingsTab.CHAPTERS },
                        text = { Text(stringResource(R.string.settings_tab_chapters), maxLines = 1) },
                    )
                    Tab(
                        selected = selectedTab == SettingsTab.SYNC,
                        onClick = { selectedTab = SettingsTab.SYNC },
                        text = { Text(stringResource(R.string.settings_tab_sync), maxLines = 1) },
                    )
                }

                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    when (selectedTab) {
                        SettingsTab.VIEW -> ViewSettingsTab(
                            viewModel = viewModel,
                            settings = settings,
                            onOpenFontPicker = { showFontPicker = true },
                        )
                        SettingsTab.CONTROLS -> ControlsSettingsTab(
                            viewModel = viewModel,
                            settings = settings,
                        )
                        SettingsTab.CHAPTERS -> ChaptersSettingsTab(
                            viewModel = viewModel,
                            settings = settings,
                            onOpenChapterPatterns = { showChapterPatterns = true },
                        )
                        SettingsTab.SYNC -> SyncSettingsTab(
                            settings = settings,
                            homeFolderName = homeFolderName,
                            onChangeHomeFolder = onChangeHomeFolder,
                        )
                    }
                }
            }
        }
    }

    if (showFontPicker) {
        FontPickerSheet(viewModel = viewModel, settings = settings, onDismiss = { showFontPicker = false })
    }
    if (showChapterPatterns) {
        ChapterPatternSheet(viewModel = viewModel, settings = settings, onDismiss = { showChapterPatterns = false })
    }
}

@Composable
private fun ViewSettingsTab(
    viewModel: SettingsController,
    settings: ReaderSettings,
    onOpenFontPicker: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.settings_section_font), style = MaterialTheme.typography.titleMedium)
        LabeledStepper(stringResource(R.string.settings_font_size), settings.fontSizeSp, 1f, 12f..32f, format = { "${it.toInt()}sp" }) { viewModel.setFontSizeSp(it) }
        LabeledStepper(stringResource(R.string.settings_line_height), settings.lineHeightMultiplier, 0.1f, 1.0f..2.5f, format = { "%.1f".format(it) }) { viewModel.setLineHeightMultiplier(it) }
        LabeledStepper(stringResource(R.string.settings_letter_spacing), settings.letterSpacingSp, 0.5f, -1f..3f, format = { "%.1f".format(it) }) { viewModel.setLetterSpacingSp(it) }
        OutlinedButton(onClick = onOpenFontPicker, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_font_picker_button))
        }

        SectionDivider()
        Text(stringResource(R.string.settings_section_margins), style = MaterialTheme.typography.titleMedium)
        LabeledStepper(stringResource(R.string.settings_margin_horizontal), settings.marginHorizontalDp, 4f, 0f..80f, format = { "${it.toInt()}dp" }) { viewModel.setMarginHorizontalDp(it) }
        LabeledStepper(stringResource(R.string.settings_margin_top), settings.marginTopDp, 4f, 0f..80f, format = { "${it.toInt()}dp" }) { viewModel.setMarginTopDp(it) }
        LabeledStepper(stringResource(R.string.settings_margin_bottom), settings.marginBottomDp, 4f, 0f..80f, format = { "${it.toInt()}dp" }) { viewModel.setMarginBottomDp(it) }

        SectionDivider()
        Text(stringResource(R.string.settings_section_theme), style = MaterialTheme.typography.titleMedium)
        val themeItems = listOf(
            Triple(ThemePreset.WARM_IVORY, R.string.settings_theme_warm_ivory, ReaderThemePresets.WARM_IVORY),
            Triple(ThemePreset.SEPIA_CREAM, R.string.settings_theme_sepia_cream, ReaderThemePresets.SEPIA_CREAM),
            Triple(ThemePreset.DARK_NAVY, R.string.settings_theme_dark_navy, ReaderThemePresets.DARK_NAVY),
            Triple(ThemePreset.SOFT_GRAY, R.string.settings_theme_soft_gray, ReaderThemePresets.SOFT_GRAY),
            Triple(ThemePreset.COOL_LIGHT, R.string.settings_theme_cool_light, ReaderThemePresets.COOL_LIGHT),
            Triple(ThemePreset.SOFT_DARK_BROWN, R.string.settings_theme_soft_dark_brown, ReaderThemePresets.SOFT_DARK_BROWN),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            themeItems.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { (preset, labelRes, colors) ->
                        ThemePreviewButton(
                            name = stringResource(labelRes),
                            colors = colors,
                            selected = settings.themePreset == preset,
                            onClick = { viewModel.setThemePreset(preset) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        SectionDivider()
        Text(stringResource(R.string.settings_section_screen), style = MaterialTheme.typography.titleMedium)
        SwitchRow(stringResource(R.string.settings_keep_screen_on), settings.keepScreenOnEnabled) { viewModel.setKeepScreenOnEnabled(it) }
        SwitchRow(stringResource(R.string.settings_brightness_override), settings.brightnessOverrideEnabled) { viewModel.setBrightnessOverrideEnabled(it) }
        if (settings.brightnessOverrideEnabled) {
            LabeledStepper(stringResource(R.string.settings_brightness), settings.brightnessValue, 0.05f, 0.05f..1f, format = { "${(it * 100).toInt()}%" }) {
                viewModel.setBrightnessValue(it)
            }
        }
        Text(stringResource(R.string.settings_orientation), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                OrientationLock.AUTO to R.string.settings_orientation_auto,
                OrientationLock.PORTRAIT to R.string.settings_orientation_portrait,
                OrientationLock.LANDSCAPE to R.string.settings_orientation_landscape,
            ).forEach { (lock, labelRes) ->
                FilterChip(
                    selected = settings.orientationLock == lock,
                    onClick = { viewModel.setOrientationLock(lock) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun ControlsSettingsTab(
    viewModel: SettingsController,
    settings: ReaderSettings,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.settings_section_page_turn_mode), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.pageTurnMode == PageTurnMode.HORIZONTAL_PAGE,
                onClick = { viewModel.setPageTurnMode(PageTurnMode.HORIZONTAL_PAGE) },
                label = { Text(stringResource(R.string.settings_page_turn_paged)) },
            )
            FilterChip(
                selected = settings.pageTurnMode == PageTurnMode.VERTICAL_SCROLL,
                onClick = { viewModel.setPageTurnMode(PageTurnMode.VERTICAL_SCROLL) },
                label = { Text(stringResource(R.string.settings_page_turn_scroll)) },
            )
        }

        SectionDivider()
        Text(stringResource(R.string.settings_section_transition_animation), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PageTransitionAnimation.NONE to R.string.settings_transition_none,
                PageTransitionAnimation.SLIDE to R.string.settings_transition_slide,
                PageTransitionAnimation.COVER to R.string.settings_transition_cover,
            ).forEach { (animation, labelRes) ->
                FilterChip(
                    selected = settings.pageTransitionAnimation == animation,
                    onClick = { viewModel.setPageTransitionAnimation(animation) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        SectionDivider()
        Text(stringResource(R.string.settings_section_page_turn_options), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_touch_zone_mode), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TouchZoneMode.STANDARD_3_COLUMN to R.string.settings_touch_zone_standard,
                TouchZoneMode.GRID_3X3 to R.string.settings_touch_zone_grid,
            ).forEach { (mode, labelRes) ->
                FilterChip(
                    selected = settings.touchZoneMode == mode,
                    onClick = { viewModel.setTouchZoneMode(mode) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        if (settings.touchZoneMode == TouchZoneMode.STANDARD_3_COLUMN) {
            Text(
                stringResource(R.string.settings_touch_zone_standard_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Standard3ColumnDiagram()
        } else {
            Text(
                stringResource(R.string.settings_touch_zone_grid_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Grid3x3Customizer(
                actions = settings.gridTouchActions,
                onSelectAction = { index, action -> viewModel.setGridTouchAction(index, action) },
            )
        }

        Spacer(Modifier.height(8.dp))
        GestureActionRow(stringResource(R.string.settings_swipe_up_detailed), settings.swipeUpAction) { viewModel.setSwipeUpAction(it) }
        GestureActionRow(stringResource(R.string.settings_swipe_down_detailed), settings.swipeDownAction) { viewModel.setSwipeDownAction(it) }
        GestureActionRow(stringResource(R.string.settings_swipe_left_detailed), settings.swipeLeftAction) { viewModel.setSwipeLeftAction(it) }
        GestureActionRow(stringResource(R.string.settings_swipe_right_detailed), settings.swipeRightAction) { viewModel.setSwipeRightAction(it) }
        Text(
            stringResource(R.string.settings_swipe_vertical_scroll_mode_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionDivider()
        Text(stringResource(R.string.settings_section_screen), style = MaterialTheme.typography.titleMedium)
        SwitchRow(stringResource(R.string.settings_volume_key_paging), settings.volumeKeyPagingEnabled) { viewModel.setVolumeKeyPagingEnabled(it) }

        SectionDivider()
        Text(stringResource(R.string.settings_section_auto_advance), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AutoAdvanceMode.OFF to R.string.settings_auto_advance_off,
                AutoAdvanceMode.TIMER to R.string.settings_auto_advance_timer,
            ).forEach { (mode, labelRes) ->
                FilterChip(
                    selected = settings.autoAdvanceMode == mode,
                    onClick = { viewModel.setAutoAdvanceMode(mode) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        if (settings.autoAdvanceMode == AutoAdvanceMode.TIMER) {
            val intervalFormat = stringResource(R.string.settings_auto_advance_interval)
            LabeledStepper(stringResource(R.string.settings_auto_advance_interval_label), settings.autoPageTurnIntervalSeconds.toFloat(), 5f, 3f..60f, format = { intervalFormat.format(it.toInt()) }) {
                viewModel.setAutoPageTurnIntervalSeconds(it.toInt())
            }
        }
    }
}

@Composable
private fun ChaptersSettingsTab(
    viewModel: SettingsController,
    settings: ReaderSettings,
    onOpenChapterPatterns: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.settings_section_chapter_jump), style = MaterialTheme.typography.titleMedium)
        LabeledStepper(stringResource(R.string.settings_chapter_jump_divisions), settings.chapterJumpDivisions.toFloat(), 1f, 2f..10f, format = { "${it.toInt()}" }) {
            viewModel.setChapterJumpDivisions(it.toInt())
        }
        OutlinedButton(onClick = onOpenChapterPatterns, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_chapter_pattern_button))
        }
    }
}

@Composable
private fun SyncSettingsTab(
    settings: ReaderSettings,
    homeFolderName: String?,
    onChangeHomeFolder: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.settings_section_position_sync), style = MaterialTheme.typography.titleMedium)
        val isVerified = settings.supabaseVerifiedSecret.isNotBlank() &&
            settings.supabaseVerifiedSecret == settings.supabaseSharedSecret
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isVerified) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32))
                Text(stringResource(R.string.settings_connected), color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.settings_position_sync_not_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (onChangeHomeFolder != null) {
            SectionDivider()
            Text(stringResource(R.string.settings_section_home_folder), style = MaterialTheme.typography.titleMedium)
            if (homeFolderName != null) {
                Text(
                    homeFolderName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            OutlinedButton(onClick = onChangeHomeFolder, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.library_change_folder))
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun LabeledStepper(
    label: String,
    value: Float,
    step: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onValueChange((value - step).coerceIn(range)) }, enabled = value > range.start) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.settings_stepper_decrease_desc, label))
        }
        Text(format(value), modifier = Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onValueChange((value + step).coerceIn(range)) }, enabled = value < range.endInclusive) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_stepper_increase_desc, label))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemePreviewButton(
    name: String,
    colors: ReaderColors,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(colors.background, RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFF3B82F6) else Color(0x33888888),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun Standard3ColumnDiagram() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.settings_gesture_previous_page),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Box(
                Modifier
                    .weight(0.2f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.settings_gesture_show_menu),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Box(
                Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.settings_gesture_next_page),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Grid3x3Customizer(
    actions: List<PageGestureAction>,
    onSelectAction: (Int, PageGestureAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (col in 0..2) {
                    val index = row * 3 + col
                    val currentAction = actions.getOrElse(index) { PageGestureAction.NEXT_PAGE }
                    var expanded by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.weight(if (col == 1) 0.2f else 0.4f)) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = stringResource(gestureActionLabelRes(currentAction)),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            PageGestureAction.entries.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(gestureActionLabelRes(action))) },
                                    onClick = {
                                        onSelectAction(index, action)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureActionRow(label: String, selected: PageGestureAction, onSelect: (PageGestureAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(stringResource(gestureActionLabelRes(selected)))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PageGestureAction.entries.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(gestureActionLabelRes(action))) },
                        onClick = { onSelect(action); expanded = false },
                    )
                }
            }
        }
    }
}

private fun gestureActionLabelRes(action: PageGestureAction): Int = when (action) {
    PageGestureAction.PREVIOUS_PAGE -> R.string.settings_gesture_previous_page
    PageGestureAction.NEXT_PAGE -> R.string.settings_gesture_next_page
    PageGestureAction.PREVIOUS_CHAPTER_JUMP -> R.string.settings_gesture_previous_chapter_jump
    PageGestureAction.NEXT_CHAPTER_JUMP -> R.string.settings_gesture_next_chapter_jump
    PageGestureAction.PREVIOUS_CHAPTER -> R.string.settings_gesture_previous_chapter
    PageGestureAction.NEXT_CHAPTER -> R.string.settings_gesture_next_chapter
    PageGestureAction.SHOW_MENU -> R.string.settings_gesture_show_menu
    PageGestureAction.NONE -> R.string.settings_gesture_none
}
