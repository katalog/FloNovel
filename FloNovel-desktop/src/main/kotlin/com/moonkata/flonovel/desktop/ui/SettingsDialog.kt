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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.moonkata.flonovel.desktop.i18n.stringResource
import com.moonkata.flonovel.desktop.font.CatalogFont
import kotlin.math.roundToInt
import com.moonkata.flonovel.desktop.font.FontManager
import com.moonkata.flonovel.desktop.font.FontState
import com.moonkata.flonovel.desktop.font.FontStatus
import com.moonkata.flonovel.desktop.library.KeymapSettings
import com.moonkata.flonovel.desktop.library.ViewSettings
import java.awt.GraphicsEnvironment
import org.jetbrains.skia.FontMgr

/**
 * Returns available system font family names with "system" at the front.
 */
fun getAvailableSystemFonts(): List<String> {
    val fonts = LinkedHashSet<String>()
    try {
        val count = FontMgr.default.familiesCount
        for (i in 0 until count) {
            val name = FontMgr.default.getFamilyName(i)
            if (name.isNotBlank()) fonts.add(name)
        }
    } catch (_: Throwable) {}
    try {
        val awtNames = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
        for (name in awtNames) {
            if (name.isNotBlank()) fonts.add(name)
        }
    } catch (_: Throwable) {}

    val sorted = fonts.sortedWith(String.CASE_INSENSITIVE_ORDER)
    return listOf("system") + sorted
}

/**
 * Settings dialog overlay for FloNovel.
 *
 * Requirements (T-09):
 * - Font family, font size, theme 6 types (WARM_IVORY, SEPIA_CREAM, DARK_NAVY, SOFT_GRAY, COOL_LIGHT, SOFT_DARK_BROWN),
 *   line height multiplier, letter spacing, margins (horizontal, top, bottom).
 * - Changes are reflected immediately upon adjustment.
 * - Changing settings MUST NEVER modify the reading position anchor or progress percentage.
 *
 * Fonts (T-32): a curated catalog of 13 entries, not every family the OS reports.
 * Each entry is one of three kinds — see [com.moonkata.flonovel.desktop.font.FontSource]:
 *   Download      → we fetch it ourselves ("받기" / "Download")
 *   OfficialPage  → vendor has no direct URL; we open their page ("사이트 열기" / "Open site")
 *   SystemOnly    → bundled with the OS; never downloadable
 * Only installed fonts are selectable: picking one we cannot render would
 * silently fall back to the default and look like a bug.
 */
enum class SettingsTab {
    VIEW,
    SHORTCUTS,
    SYNC,
}

@Composable
fun SettingsDialog(
    currentSettings: ViewSettings,
    onSettingsChanged: (ViewSettings) -> Unit,
    onDismiss: () -> Unit,
    currentLanguage: String = "SYSTEM",
    onLanguageChanged: ((String) -> Unit)? = null,
    currentKeymap: KeymapSettings = KeymapSettings(),
    onKeymapChanged: ((KeymapSettings) -> Unit)? = null,
    initialTab: SettingsTab = SettingsTab.VIEW,
    isDropboxLinked: Boolean = false,
    cachedSupabaseSecret: String? = null,
    isSupabaseConfigured: Boolean = false,
    isSupabaseVerified: Boolean = false,
    lastSupabaseTestError: String? = null,
    onStartDropboxLogin: (() -> Unit)? = null,
    onRegenerateSecret: (() -> Unit)? = null,
    onTestSupabaseConnection: (() -> Unit)? = null,
    fontStates: List<FontState> = emptyList(),
    downloadingFamily: String? = null,
    downloadProgress: Float = 0f,
    fontError: String? = null,
    onDownloadFont: ((CatalogFont) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var currentTab by remember { mutableStateOf(initialTab) }
    var fontDropdownOpen by remember { mutableStateOf(false) }
    var showRegenConfirmDialog by remember { mutableStateOf(false) }
    var recordingAction by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
                .width(500.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (recordingAction != null && keyEvent.type == KeyEventType.KeyDown) {
                        val newKey = KeymapHelper.fromKeyEvent(keyEvent)
                        val action = recordingAction
                        recordingAction = null
                        if (onKeymapChanged != null && action != null) {
                            val updated = when (action) {
                                "nextPage" -> currentKeymap.copy(nextPage = newKey)
                                "prevPage" -> currentKeymap.copy(prevPage = newKey)
                                "nextChapter" -> currentKeymap.copy(nextChapter = newKey)
                                "prevChapter" -> currentKeymap.copy(prevChapter = newKey)
                                "back" -> currentKeymap.copy(back = newKey)
                                "home" -> currentKeymap.copy(home = newKey)
                                "search" -> currentKeymap.copy(search = newKey)
                                "toc" -> currentKeymap.copy(toc = newKey)
                                "settings" -> currentKeymap.copy(settings = newKey)
                                "openInExplorer" -> currentKeymap.copy(openInExplorer = newKey)
                                "openInDefaultApp" -> currentKeymap.copy(openInDefaultApp = newKey)
                                else -> currentKeymap
                            }
                            onKeymapChanged(updated)
                        }
                        true
                    } else {
                        false
                    }
                }
                .clickable(enabled = false) {}, // Prevent click propagation to overlay
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF252528),
            elevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                // Title & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource("settings_title"),
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

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFF3E3E42))
                Spacer(modifier = Modifier.height(16.dp))

                // Tab selection pills
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        SettingsTab.VIEW to stringResource("settings_tab_view"),
                        SettingsTab.SHORTCUTS to stringResource("settings_tab_shortcuts"),
                        SettingsTab.SYNC to stringResource("settings_tab_sync"),
                    ).forEach { (tab, label) ->
                        val isSelected = currentTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) Color(0xFF2563EB) else Color(0xFF2A2A2E),
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    recordingAction = null
                                    currentTab = tab
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF9CA3AF),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // Scrollable settings contents
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    if (currentTab == SettingsTab.VIEW) {
                    // 0. Language (SYSTEM, KO, EN)
                    item {
                        SettingSectionTitle(stringResource("settings_language_title"))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                "SYSTEM" to stringResource("settings_language_system"),
                                "KO" to stringResource("settings_language_ko"),
                                "EN" to stringResource("settings_language_en"),
                            ).forEach { (code, name) ->
                                val selected = currentLanguage.equals(code, ignoreCase = true)
                                ThemeButton(
                                    name = name,
                                    selected = selected,
                                    onClick = { onLanguageChanged?.invoke(code) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 1. Theme (6 options in 2 rows of 3)
                    item {
                        SettingSectionTitle(stringResource("settings_theme_title"))
                        val currentCode = ReaderColors.canonicalCode(currentSettings.theme)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ReaderColors.AllThemes.chunked(3).forEach { rowThemes ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowThemes.forEach { spec ->
                                        val selected = currentCode.equals(spec.code, ignoreCase = true)
                                        ThemePreviewButton(
                                            name = stringResource(spec.stringResKey),
                                            themeColors = spec.colors,
                                            selected = selected,
                                            onClick = { onSettingsChanged(currentSettings.copy(theme = spec.code)) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 2. Pane Mode (1-pane / 2-pane)
                    item {
                        SettingSectionTitle(stringResource("settings_pane_mode_title"))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("ONE" to "1-pane", "TWO" to "2-pane").forEach { (code, name) ->
                                val selected = currentSettings.paneMode.equals(code, ignoreCase = true)
                                ThemeButton(
                                    name = name,
                                    selected = selected,
                                    onClick = { onSettingsChanged(currentSettings.copy(paneMode = code)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 2.5. UI Scale (1.0x ~ 1.5x)
                    item {
                        SettingSectionTitle(stringResource("settings_ui_scale_title"))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                1.0f to "1.0x",
                                1.1f to "1.1x",
                                1.2f to "1.2x",
                                1.3f to "1.3x",
                                1.4f to "1.4x",
                                1.5f to "1.5x",
                            ).forEach { (scale, label) ->
                                val selected = kotlin.math.abs(currentSettings.uiScale - scale) < 0.01f
                                ThemeButton(
                                    name = label,
                                    selected = selected,
                                    onClick = { onSettingsChanged(currentSettings.copy(uiScale = scale)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 3. Font Family Selection — curated catalog, not every system font.
                    item {
                        SettingSectionTitle(stringResource("settings_font_family_title"))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF333338), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF4A4A50), RoundedCornerShape(6.dp))
                                .clickable { fontDropdownOpen = !fontDropdownOpen }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (currentSettings.fontFamily.isBlank()) stringResource("common_system_default") else currentSettings.fontFamily,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                )
                                Text(text = if (fontDropdownOpen) "▲" else "▼", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                            }
                        }

                        if (fontDropdownOpen) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF2C2C30),
                                elevation = 8.dp,
                            ) {
                                LazyColumn {
                                    // "System default" stays available as an escape hatch.
                                    item {
                                        FontRow(
                                            label = stringResource("common_system_default"),
                                            statusText = "",
                                            statusColor = Color(0xFF888888),
                                            selected = currentSettings.fontFamily.isBlank(),
                                            note = "",
                                            actionLabel = null,
                                            onSelect = {
                                                onSettingsChanged(currentSettings.copy(fontFamily = ""))
                                                fontDropdownOpen = false
                                            },
                                            onAction = null,
                                        )
                                    }

                                    items(fontStates) { state ->
                                        val font = state.font
                                        val busy = downloadingFamily == font.familyName
                                        val installed = state.status == FontStatus.INSTALLED
                                        FontRow(
                                            label = font.displayName,
                                            statusText = when {
                                                busy -> stringResource("settings_font_status_downloading", (downloadProgress * 100).toInt())
                                                installed -> stringResource("settings_font_status_installed")
                                                state.status == FontStatus.DOWNLOADABLE -> stringResource("settings_font_status_downloadable")
                                                state.status == FontStatus.MANUAL_INSTALL -> stringResource("settings_font_status_manual_install")
                                                else -> stringResource("settings_font_status_not_found")
                                            },
                                            statusColor = when {
                                                busy -> Color(0xFF5B9BD5)
                                                installed -> Color(0xFF6BBF6B)
                                                state.status == FontStatus.UNAVAILABLE -> Color(0xFF777777)
                                                else -> Color(0xFFAAAAAA)
                                            },
                                            selected = currentSettings.fontFamily.equals(font.familyName, ignoreCase = true),
                                            note = if (installed) "" else font.note,
                                            actionLabel = when {
                                                busy -> null
                                                installed -> null
                                                state.status == FontStatus.DOWNLOADABLE -> stringResource("settings_font_action_download")
                                                state.status == FontStatus.MANUAL_INSTALL -> stringResource("settings_font_action_open_site")
                                                else -> null
                                            },
                                            // Only installed fonts are selectable — picking one we
                                            // cannot render would silently fall back to the default.
                                            onSelect = if (installed) {
                                                {
                                                    onSettingsChanged(currentSettings.copy(fontFamily = font.familyName))
                                                    fontDropdownOpen = false
                                                }
                                            } else {
                                                null
                                            },
                                            onAction = when (state.status) {
                                                FontStatus.DOWNLOADABLE -> ({ onDownloadFont?.invoke(font) })
                                                FontStatus.MANUAL_INSTALL -> ({ FontManager.openOfficialPage(font) })
                                                else -> null
                                            },
                                        )
                                    }

                                    if (fontError != null) {
                                        item {
                                            Text(
                                                text = fontError,
                                                color = Color(0xFFE57373),
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 4. Font Size (sp)
                    item {
                        NumericSettingRow(
                            title = stringResource("settings_font_size"),
                            valueDisplay = "${currentSettings.fontSizeSp.toInt()} sp",
                            onDecrease = {
                                if (currentSettings.fontSizeSp > 12f) {
                                    onSettingsChanged(currentSettings.copy(fontSizeSp = currentSettings.fontSizeSp - 1f))
                                }
                            },
                            onIncrease = {
                                if (currentSettings.fontSizeSp < 48f) {
                                    onSettingsChanged(currentSettings.copy(fontSizeSp = currentSettings.fontSizeSp + 1f))
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 5. Line Height Multiplier
                    item {
                        NumericSettingRow(
                            title = stringResource("settings_line_height"),
                            valueDisplay = String.format("%.1fx", currentSettings.lineHeightMultiplier),
                            onDecrease = {
                                if (currentSettings.lineHeightMultiplier > 1.1f) {
                                    onSettingsChanged(currentSettings.copy(lineHeightMultiplier = currentSettings.lineHeightMultiplier - 0.1f))
                                }
                            },
                            onIncrease = {
                                if (currentSettings.lineHeightMultiplier < 3.0f) {
                                    onSettingsChanged(currentSettings.copy(lineHeightMultiplier = currentSettings.lineHeightMultiplier + 0.1f))
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 5-1. Empty Line Spacing
                    item {
                        NumericSettingRow(
                            title = stringResource("settings_empty_line_spacing"),
                            valueDisplay = "${(currentSettings.emptyLineSpacingRatio * 100).roundToInt()}%",
                            onDecrease = {
                                if (currentSettings.emptyLineSpacingRatio > 0.55f) {
                                    val next = ((currentSettings.emptyLineSpacingRatio - 0.1f) * 10).roundToInt() / 10f
                                    onSettingsChanged(currentSettings.copy(emptyLineSpacingRatio = next.coerceIn(0.5f, 1.0f)))
                                }
                            },
                            onIncrease = {
                                if (currentSettings.emptyLineSpacingRatio < 0.95f) {
                                    val next = ((currentSettings.emptyLineSpacingRatio + 0.1f) * 10).roundToInt() / 10f
                                    onSettingsChanged(currentSettings.copy(emptyLineSpacingRatio = next.coerceIn(0.5f, 1.0f)))
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 6. Letter Spacing (sp)
                    item {
                        NumericSettingRow(
                            title = stringResource("settings_letter_spacing"),
                            valueDisplay = String.format("%.1f sp", currentSettings.letterSpacing),
                            onDecrease = {
                                if (currentSettings.letterSpacing > -2f) {
                                    onSettingsChanged(currentSettings.copy(letterSpacing = currentSettings.letterSpacing - 0.5f))
                                }
                            },
                            onIncrease = {
                                if (currentSettings.letterSpacing < 10f) {
                                    onSettingsChanged(currentSettings.copy(letterSpacing = currentSettings.letterSpacing + 0.5f))
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 7. Horizontal Margin (dp)
                    item {
                        NumericSettingRow(
                            title = stringResource("settings_margin_horizontal"),
                            valueDisplay = "${currentSettings.marginHorizontal.toInt()} dp",
                            onDecrease = {
                                if (currentSettings.marginHorizontal > 0f) {
                                    onSettingsChanged(currentSettings.copy(marginHorizontal = currentSettings.marginHorizontal - 4f))
                                }
                            },
                            onIncrease = {
                                if (currentSettings.marginHorizontal < 80f) {
                                    onSettingsChanged(currentSettings.copy(marginHorizontal = currentSettings.marginHorizontal + 4f))
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 8. Vertical Margins (dp)
                    item {
                        NumericSettingRow(
                            title = stringResource("settings_margin_vertical"),
                            valueDisplay = "${currentSettings.marginTop.toInt()} dp",
                            onDecrease = {
                                if (currentSettings.marginTop > 0f) {
                                    val newMargin = currentSettings.marginTop - 4f
                                    onSettingsChanged(currentSettings.copy(marginTop = newMargin, marginBottom = newMargin))
                                }
                            },
                            onIncrease = {
                                if (currentSettings.marginTop < 80f) {
                                    val newMargin = currentSettings.marginTop + 4f
                                    onSettingsChanged(currentSettings.copy(marginTop = newMargin, marginBottom = newMargin))
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 9. Gutter (pane spacing in dp)
                    item {
                        NumericSettingRow(
                            title = stringResource("settings_gutter"),
                            valueDisplay = "${currentSettings.gutter.toInt()} dp",
                            onDecrease = {
                                if (currentSettings.gutter > 0f) {
                                    onSettingsChanged(currentSettings.copy(gutter = (currentSettings.gutter - 4f).coerceAtLeast(0f)))
                                }
                            },
                            onIncrease = {
                                if (currentSettings.gutter < 80f) {
                                    onSettingsChanged(currentSettings.copy(gutter = currentSettings.gutter + 4f))
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 10. Pane Ratio (Left:Right proportion)
                    item {
                        SettingSectionTitle(stringResource("settings_pane_ratio_title"))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                0.45f to "45 : 55",
                                0.5f to "50 : 50",
                                0.55f to "55 : 45",
                            ).forEach { (ratio, label) ->
                                val selected = kotlin.math.abs(currentSettings.paneRatio - ratio) < 0.01f
                                ThemeButton(
                                    name = label,
                                    selected = selected,
                                    onClick = { onSettingsChanged(currentSettings.copy(paneRatio = ratio)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 11. Max Line Width (px)
                    item {
                        NumericSettingRow(
                            title = stringResource("settings_max_line_width"),
                            valueDisplay = if (currentSettings.maxLineWidth > 0) "${currentSettings.maxLineWidth} px" else stringResource("common_unlimited"),
                            onDecrease = {
                                if (currentSettings.maxLineWidth > 400) {
                                    onSettingsChanged(currentSettings.copy(maxLineWidth = currentSettings.maxLineWidth - 100))
                                } else if (currentSettings.maxLineWidth <= 400 && currentSettings.maxLineWidth > 0) {
                                    onSettingsChanged(currentSettings.copy(maxLineWidth = 0))
                                }
                            },
                            onIncrease = {
                                if (currentSettings.maxLineWidth == 0) {
                                    onSettingsChanged(currentSettings.copy(maxLineWidth = 500))
                                } else if (currentSettings.maxLineWidth < 2400) {
                                    onSettingsChanged(currentSettings.copy(maxLineWidth = currentSettings.maxLineWidth + 100))
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 12. Align Chapter to Left Pane (optional, defaults to false)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(text = stringResource("settings_chapter_left_align_title"), color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                Text(text = stringResource("settings_chapter_left_align_desc"), color = Color(0xFF888888), fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ThemeButton(
                                    name = stringResource("common_off"),
                                    selected = !currentSettings.alignChapterToLeftPane,
                                    onClick = { onSettingsChanged(currentSettings.copy(alignChapterToLeftPane = false)) },
                                    modifier = Modifier.width(60.dp),
                                )
                                ThemeButton(
                                    name = stringResource("common_on"),
                                    selected = currentSettings.alignChapterToLeftPane,
                                    onClick = { onSettingsChanged(currentSettings.copy(alignChapterToLeftPane = true)) },
                                    modifier = Modifier.width(60.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else if (currentTab == SettingsTab.SHORTCUTS) {
                    item {
                        SettingSectionTitle(stringResource("settings_shortcuts_title"))
                        Text(
                            text = stringResource("settings_shortcuts_desc"),
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_next_page"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.nextPage),
                            isRecording = recordingAction == "nextPage",
                            onClick = {
                                recordingAction = if (recordingAction == "nextPage") null else "nextPage"
                                focusRequester.requestFocus()
                            },
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_prev_page"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.prevPage),
                            isRecording = recordingAction == "prevPage",
                            onClick = {
                                recordingAction = if (recordingAction == "prevPage") null else "prevPage"
                                focusRequester.requestFocus()
                            },
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_next_chapter"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.nextChapter),
                            isRecording = recordingAction == "nextChapter",
                            onClick = {
                                recordingAction = if (recordingAction == "nextChapter") null else "nextChapter"
                                focusRequester.requestFocus()
                            },
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_prev_chapter"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.prevChapter),
                            isRecording = recordingAction == "prevChapter",
                            onClick = {
                                recordingAction = if (recordingAction == "prevChapter") null else "prevChapter"
                                focusRequester.requestFocus()
                            },
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_back"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.back),
                            isRecording = recordingAction == "back",
                            onClick = {
                                recordingAction = if (recordingAction == "back") null else "back"
                                focusRequester.requestFocus()
                            },
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_home"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.home),
                            isRecording = recordingAction == "home",
                            onClick = {
                                recordingAction = if (recordingAction == "home") null else "home"
                                focusRequester.requestFocus()
                            },
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_search"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.search),
                            isRecording = recordingAction == "search",
                            onClick = {
                                recordingAction = if (recordingAction == "search") null else "search"
                                focusRequester.requestFocus()
                            },
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_toc"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.toc),
                            isRecording = recordingAction == "toc",
                            onClick = {
                                recordingAction = if (recordingAction == "toc") null else "toc"
                                focusRequester.requestFocus()
                            },
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_settings"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.settings),
                            isRecording = recordingAction == "settings",
                            onClick = {
                                recordingAction = if (recordingAction == "settings") null else "settings"
                                focusRequester.requestFocus()
                            },
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_open_in_explorer"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.openInExplorer),
                            isRecording = recordingAction == "openInExplorer",
                            onClick = {
                                recordingAction = if (recordingAction == "openInExplorer") null else "openInExplorer"
                                focusRequester.requestFocus()
                            },
                        )
                        ShortcutRow(
                            title = stringResource("settings_shortcut_open_in_default_app"),
                            keyDisplayName = KeymapHelper.toDisplayName(currentKeymap.openInDefaultApp),
                            isRecording = recordingAction == "openInDefaultApp",
                            onClick = {
                                recordingAction = if (recordingAction == "openInDefaultApp") null else "openInDefaultApp"
                                focusRequester.requestFocus()
                            },
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = {
                                    recordingAction = null
                                    onKeymapChanged?.invoke(KeymapSettings())
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF374151),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("settings_shortcut_reset"), fontSize = 12.sp)
                            }
                        }
                    }
                } else if (currentTab == SettingsTab.SYNC) {
                    item {
                        // Synchronization Section
                        SettingSectionTitle(stringResource("settings_sync_title"))
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E1E22),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333338)),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(stringResource("settings_dropbox_link_title"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = if (isDropboxLinked) stringResource("settings_dropbox_linked") else stringResource("settings_dropbox_unlinked"),
                                            color = if (isDropboxLinked) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                            fontSize = 12.sp,
                                        )
                                    }
                                    if (!isDropboxLinked) {
                                        Button(
                                            onClick = { onStartDropboxLogin?.invoke() },
                                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2563EB), contentColor = Color.White),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            Text(stringResource("settings_dropbox_login"), fontSize = 11.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = { onStartDropboxLogin?.invoke() },
                                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF374151), contentColor = Color.White),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(stringResource("settings_dropbox_relink"), fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Divider(color = Color(0xFF2A2A2E))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(stringResource("settings_supabase_secret_title"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = if (cachedSupabaseSecret != null) {
                                                stringResource("settings_supabase_secret_configured", cachedSupabaseSecret.takeLast(6))
                                            } else {
                                                stringResource("settings_supabase_secret_not_registered")
                                            },
                                            color = if (cachedSupabaseSecret != null) Color(0xFF60A5FA) else Color(0xFF9CA3AF),
                                            fontSize = 12.sp,
                                        )
                                    }
                                    if (cachedSupabaseSecret != null && isDropboxLinked) {
                                        Button(
                                            onClick = { showRegenConfirmDialog = true },
                                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFDC2626), contentColor = Color.White),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            Text(stringResource("settings_supabase_secret_regenerate"), fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Divider(color = Color(0xFF2A2A2E))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(stringResource("settings_supabase_sync_title"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        val statusText = when {
                                            !isSupabaseConfigured -> stringResource("settings_supabase_sync_unconfigured")
                                            cachedSupabaseSecret.isNullOrBlank() -> stringResource("settings_supabase_sync_no_secret")
                                            isSupabaseVerified -> stringResource("settings_supabase_sync_verified")
                                            else -> stringResource("settings_supabase_sync_unverified")
                                        }
                                        val statusColor = when {
                                            isSupabaseVerified -> Color(0xFF10B981)
                                            !isSupabaseConfigured || cachedSupabaseSecret.isNullOrBlank() -> Color(0xFF9CA3AF)
                                            else -> Color(0xFFF59E0B)
                                        }
                                        Text(text = statusText, color = statusColor, fontSize = 12.sp)
                                        if (!lastSupabaseTestError.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = stringResource("settings_supabase_sync_error", lastSupabaseTestError ?: ""),
                                                color = Color(0xFFEF4444),
                                                fontSize = 11.sp,
                                            )
                                        }
                                    }
                                    if (isSupabaseConfigured && !cachedSupabaseSecret.isNullOrBlank()) {
                                        Button(
                                            onClick = { onTestSupabaseConnection?.invoke() },
                                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF0284C7), contentColor = Color.White),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            Text(stringResource("settings_supabase_test_connection"), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                }

                if (showRegenConfirmDialog) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f))
                            .clickable { showRegenConfirmDialog = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(400.dp)
                                .clickable(enabled = false) {},
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF221515),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource("settings_supabase_regenerate_warning_title"),
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource("settings_supabase_regenerate_warning_desc"),
                                    color = Color(0xFFE5E7EB),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Button(
                                        onClick = { showRegenConfirmDialog = false },
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF374151), contentColor = Color.White),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(stringResource("common_cancel"), fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Button(
                                        onClick = {
                                            showRegenConfirmDialog = false
                                            onRegenerateSecret?.invoke()
                                        },
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFDC2626), contentColor = Color.White),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(stringResource("settings_supabase_regenerate_confirm"), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFF3E3E42))
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom actions: Reset & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = { onSettingsChanged(ViewSettings()) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCCCCCC)),
                    ) {
                        Text(stringResource("settings_reset_defaults"), fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A82F6), contentColor = Color.White),
                    ) {
                        Text(stringResource("common_done"), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSectionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFFCCCCCC),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun ThemeButton(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(if (selected) Color(0xFF3A82F6) else Color(0xFF333338), RoundedCornerShape(6.dp))
            .border(1.dp, if (selected) Color(0xFF60A5FA) else Color(0xFF4A4A50), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            color = if (selected) Color.White else Color(0xFFCCCCCC),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ThemePreviewButton(
    name: String,
    themeColors: ThemeColors,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(themeColors.background, RoundedCornerShape(6.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFF3B82F6) else Color(0x33888888),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            color = themeColors.text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun NumericSettingRow(
    title: String,
    valueDisplay: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = Color(0xFFCCCCCC), fontSize = 13.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF333338), RoundedCornerShape(4.dp))
                    .clickable(onClick = onDecrease)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "−", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = valueDisplay,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .width(70.dp)
                    .padding(horizontal = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Box(
                modifier = Modifier
                    .background(Color(0xFF333338), RoundedCornerShape(4.dp))
                    .clickable(onClick = onIncrease)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * One row in the curated font list.
 *
 * [onSelect] is null for fonts that are not installed — they are shown greyed
 * out with an action button instead, so the user cannot pick a font that would
 * silently render as the default.
 */
@Composable
private fun FontRow(
    label: String,
    statusText: String,
    statusColor: Color,
    selected: Boolean,
    note: String,
    actionLabel: String?,
    onSelect: (() -> Unit)?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onSelect != null) it.clickable(onClick = onSelect) else it }
            .background(if (selected) Color(0xFF3E3E48) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = when {
                    selected -> Color(0xFF5B9BD5)
                    onSelect != null -> Color.White
                    else -> Color(0xFF999999)
                },
                fontSize = 13.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (statusText.isNotBlank()) {
                    Text(text = statusText, color = statusColor, fontSize = 11.sp)
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF3A5A7A), RoundedCornerShape(4.dp))
                            .clickable(onClick = onAction)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(text = actionLabel, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
        if (note.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = note, color = Color(0xFF777777), fontSize = 10.sp)
        }
    }
}

@Composable
private fun ShortcutRow(
    title: String,
    keyDisplayName: String,
    isRecording: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Box(
            modifier = Modifier
                .background(
                    if (isRecording) Color(0xFFB45309) else Color(0xFF1E1E22),
                    RoundedCornerShape(6.dp),
                )
                .border(
                    1.dp,
                    if (isRecording) Color(0xFFF59E0B) else Color(0xFF3F3F46),
                    RoundedCornerShape(6.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isRecording) stringResource("settings_shortcut_press_key") else keyDisplayName,
                color = if (isRecording) Color(0xFFFEF3C7) else Color(0xFF93C5FD),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

