package com.moonkata.flonovel.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import com.moonkata.flonovel.desktop.sync.RemotePositionNotice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.moonkata.flonovel.desktop.i18n.Strings
import com.moonkata.flonovel.desktop.i18n.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.font.FontCatalog
import com.moonkata.flonovel.desktop.font.FontDownloader
import com.moonkata.flonovel.desktop.font.FontManager
import com.moonkata.flonovel.desktop.library.ChapterSettings
import com.moonkata.flonovel.desktop.library.DeleteSettings
import com.moonkata.flonovel.desktop.library.KeymapSettings
import com.moonkata.flonovel.desktop.library.ViewSettings
import com.moonkata.flonovel.desktop.reader.ChapterJumpNavigator
import com.moonkata.flonovel.desktop.reader.AutoPageTurnScheduler
import com.moonkata.flonovel.desktop.reader.EyeStrainScheduler
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import com.moonkata.flonovel.desktop.audio.RadioPlayer
import com.moonkata.flonovel.desktop.text.Chapter
import com.moonkata.flonovel.desktop.text.ChapterDetector
import com.moonkata.flonovel.desktop.text.Search
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

// Tick interval driving the auto page-turn countdown bar. Short enough for the bar to
// look smooth, long enough to not matter for recomposition cost.
private const val AUTO_PAGE_TURN_TICK_MS = 100L

object ProgressFormatter {
    /**
     * Formats reading progress [0.0, 1.0] as a percentage with 1 decimal place.
     * Stored value is ratio [0, 1], displayed as ratio * 100 with 1 decimal place.
     */
    fun format(progress: Double): String {
        return String.format(Locale.US, "%.1f%%", progress * 100)
    }
}

/**
 * Resolves a [FontFamily] by name.
 *
 * Looks in two places, in this order:
 *  1. fonts we downloaded ourselves (configDir()/fonts) — a font installed
 *     through Settings is not registered with the OS, so
 *     [FontMgr.matchFamilyStyle] would never find it;
 *  2. families the OS already knows about.
 *
 * Falls back to [FontFamily.Default] when blank, "system", "default", or when
 * resolution fails. Never throws — a missing font must not stop reading.
 */
fun resolveFontFamily(fontFamilyName: String): FontFamily {
    val trimmed = fontFamilyName.trim()
    if (trimmed.isBlank() ||
        trimmed.equals("system", ignoreCase = true) ||
        trimmed.equals("default", ignoreCase = true)
    ) {
        return FontFamily.Default
    }

    // (1) A font file on disk (downloaded catalog font or user custom font)
    try {
        val file = FontManager.findFontFile(trimmed)
        if (file != null) {
            val loaded = FontMgr.default.makeFromFile(file.toString(), 0)
            if (loaded != null) return FontFamily(Typeface(loaded))
        }
    } catch (e: Throwable) {
        System.err.println("Failed to load font file for '$trimmed': ${e.message}")
        // fall through to the system lookup
    }

    // (2) A family the OS provides (Windows installed fonts).
    return try {
        val skiaTypeface = FontMgr.default.matchFamilyStyle(trimmed, FontStyle.NORMAL)
        if (skiaTypeface != null) {
            FontFamily(Typeface(skiaTypeface))
        } else {
            FontFamily.Default
        }
    } catch (e: Throwable) {
        System.err.println("Failed to resolve font '$trimmed', falling back to Default: ${e.message}")
        FontFamily.Default
    }
}


/**
 * Dispatches a reading navigation action for a given [key] and modifier:
 * - Chapter Jump: Ctrl+PageDown, Ctrl+DirectionRight, ']' (next); Ctrl+PageUp, Ctrl+DirectionLeft, '[' (previous)
 * - Search: Ctrl+F, '/'
 * - Regular navigation: PageDown, PageUp, DirectionRight, DirectionLeft, Period (>), Comma (<), Spacebar
 * - Dialogs: F2 (Settings), F3/T (TOC)
 * - Exit: Escape (Return to library/folder view)
 *
 * Returns true if the key was handled, false otherwise.
 */
fun handleKeyAction(
    key: Key,
    isShiftPressed: Boolean = false,
    isCtrlPressed: Boolean = false,
    codePoint: Int = 0,
    keymap: KeymapSettings = KeymapSettings(),
    navigator: ReaderNavigator,
    advanceRatio: Float,
    onAnchorChanged: ((Int) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenToc: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    onNextChapterJump: (() -> Unit)? = null,
    onPreviousChapterJump: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null,
    onPreviousChapter: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onOpenInExplorer: (() -> Unit)? = null,
    onOpenInDefaultApp: (() -> Unit)? = null,
    onDeleteFile: (() -> Unit)? = null,
    autoPageTurnEnabled: Boolean = false,
    onToggleAutoPageTurn: (() -> Unit)? = null,
): Boolean {
    // 0. Escape / Back (returns to library/folder view)
    if (KeymapHelper.matches(keymap.back, key, codePoint)) {
        onBack?.invoke()
        return true
    }

    // 1. Home (returns to library home or start of book)
    if (KeymapHelper.matches(keymap.home, key, codePoint)) {
        onHome?.invoke()
        return true
    }

    // 1b. Reveal the current file in the OS file manager (F7 by default)
    if (KeymapHelper.matches(keymap.openInExplorer, key, codePoint)) {
        onOpenInExplorer?.invoke()
        return true
    }

    // 1c. Open the current file with the OS default application for it (F8 by default)
    if (KeymapHelper.matches(keymap.openInDefaultApp, key, codePoint)) {
        onOpenInDefaultApp?.invoke()
        return true
    }

    // 1d. Delete the current file, or move it to the configured folder (Del by default)
    if (KeymapHelper.matches(keymap.deleteFile, key, codePoint)) {
        onDeleteFile?.invoke()
        return true
    }

    // 2. Search (F2 by default, or Ctrl+F / '/')
    if (KeymapHelper.matches(keymap.search, key, codePoint) ||
        (isCtrlPressed && key == Key.F) ||
        (!isCtrlPressed && key == Key.Slash)
    ) {
        onOpenSearch?.invoke()
        return true
    }

    // 3. TOC / Chapter list (F3 by default, or 'T')
    if (KeymapHelper.matches(keymap.toc, key, codePoint) || (!isCtrlPressed && key == Key.T)) {
        onOpenToc?.invoke()
        return true
    }

    // 4. Settings (F4 by default)
    if (KeymapHelper.matches(keymap.settings, key, codePoint)) {
        onOpenSettings?.invoke()
        return true
    }

    // 5. Chapter jump forward (subdivision jump: PgDn by default, or Ctrl+PgDn, Ctrl+Right)
    if (KeymapHelper.matches(keymap.nextChapterJump, key, codePoint) ||
        (isCtrlPressed && (key == Key.PageDown || key == Key.DirectionRight))
    ) {
        onNextChapterJump?.invoke()
        return true
    }

    // 6. Chapter jump backward (subdivision jump: PgUp by default, or Ctrl+PgUp, Ctrl+Left)
    if (KeymapHelper.matches(keymap.prevChapterJump, key, codePoint) ||
        (isCtrlPressed && (key == Key.PageUp || key == Key.DirectionLeft))
    ) {
        onPreviousChapterJump?.invoke()
        return true
    }

    // 5b. Direct next chapter (whole chapter forward: ']' by default)
    if (KeymapHelper.matches(keymap.nextChapter, key, codePoint) ||
        (!isCtrlPressed && key == Key.RightBracket)
    ) {
        (onNextChapter ?: onNextChapterJump)?.invoke()
        return true
    }

    // 6b. Direct previous chapter (whole chapter backward: '[' by default)
    if (KeymapHelper.matches(keymap.prevChapter, key, codePoint) ||
        (!isCtrlPressed && key == Key.LeftBracket)
    ) {
        (onPreviousChapter ?: onPreviousChapterJump)?.invoke()
        return true
    }

    // 7. Auto page-turn toggle ('P' by default)
    if (KeymapHelper.matches(keymap.autoPageTurn, key, codePoint)) {
        onToggleAutoPageTurn?.invoke()
        return true
    }

    // 8. Regular page navigation: Next Page ('>' / '.' by default, or DirectionRight, or Spacebar without Shift)
    if (KeymapHelper.matches(keymap.nextPage, key, codePoint) ||
        (!isCtrlPressed && (key == Key.DirectionRight || (key == Key.Spacebar && !isShiftPressed)))
    ) {
        // A manual page turn while auto page-turn is running switches it off outright,
        // rather than merely resetting its countdown — the reader took over, so stay handed off.
        if (autoPageTurnEnabled) onToggleAutoPageTurn?.invoke()
        navigator.advance(advanceRatio.coerceIn(0.1f, 1.0f))
        onAnchorChanged?.invoke(navigator.anchor)
        return true
    }

    // 10. Regular page navigation: Previous Page ('<' / ',' by default, or DirectionLeft, or Spacebar with Shift)
    if (KeymapHelper.matches(keymap.prevPage, key, codePoint) ||
        (!isCtrlPressed && (key == Key.DirectionLeft || (key == Key.Spacebar && isShiftPressed)))
    ) {
        if (autoPageTurnEnabled) onToggleAutoPageTurn?.invoke()
        navigator.retreat(advanceRatio.coerceIn(0.1f, 1.0f))
        onAnchorChanged?.invoke(navigator.anchor)
        return true
    }

    return false
}

/**
 * Handles keyboard navigation key events for reader.
 * Accepts '>' (Shift+Period) and '<' (Shift+Comma) as well as unshifted '.' and ','.
 */
fun handleReaderKeyEvent(
    keyEvent: KeyEvent,
    keymap: KeymapSettings = KeymapSettings(),
    navigator: ReaderNavigator,
    advanceRatio: Float,
    onAnchorChanged: ((Int) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenToc: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    onNextChapterJump: (() -> Unit)? = null,
    onPreviousChapterJump: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null,
    onPreviousChapter: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onOpenInExplorer: (() -> Unit)? = null,
    onOpenInDefaultApp: (() -> Unit)? = null,
    onDeleteFile: (() -> Unit)? = null,
    autoPageTurnEnabled: Boolean = false,
    onToggleAutoPageTurn: (() -> Unit)? = null,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false

    val codePoint = keyEvent.utf16CodePoint
    val isPeriodOrGreater = keyEvent.key == Key.Period || codePoint == '>'.code || codePoint == '.'.code
    val isCommaOrLess = keyEvent.key == Key.Comma || codePoint == '<'.code || codePoint == ','.code

    val resolvedKey = when {
        isPeriodOrGreater -> Key.Period
        isCommaOrLess -> Key.Comma
        else -> keyEvent.key
    }

    return handleKeyAction(
        key = resolvedKey,
        isShiftPressed = keyEvent.isShiftPressed,
        isCtrlPressed = keyEvent.isCtrlPressed || keyEvent.isMetaPressed,
        codePoint = codePoint,
        keymap = keymap,
        navigator = navigator,
        advanceRatio = advanceRatio,
        onAnchorChanged = onAnchorChanged,
        onOpenSettings = onOpenSettings,
        onOpenToc = onOpenToc,
        onOpenSearch = onOpenSearch,
        onNextChapterJump = onNextChapterJump,
        onPreviousChapterJump = onPreviousChapterJump,
        onNextChapter = onNextChapter,
        onPreviousChapter = onPreviousChapter,
        onHome = onHome,
        onBack = onBack,
        onOpenInExplorer = onOpenInExplorer,
        onOpenInDefaultApp = onOpenInDefaultApp,
        onDeleteFile = onDeleteFile,
        autoPageTurnEnabled = autoPageTurnEnabled,
        onToggleAutoPageTurn = onToggleAutoPageTurn,
    )
}

/**
 * Main reader view supporting both 1-pane and 2-pane modes:
 * - Read-only display: using [Text] component, strictly preventing any editing.
 * - Measurement matches rendering: uses [ComposeTextFitter] with the exact same [TextStyle].
 * - Chapter highlight: applies background tint only without bolding.
 * - Corner progress: displays progress percentage with 1 decimal place in the corner.
 * - Keyboard navigation: PageDown / PageUp / > / < moves by 1 unit.
 * - Preserves anchor across window resize and mode switch: clears history and recalculates from same anchor.
 */
@Composable
fun ReaderView(
    fullText: String,
    navigator: ReaderNavigator,
    viewSettings: ViewSettings,
    chapterSettings: ChapterSettings = ChapterSettings(),
    onChapterSettingsChanged: ((ChapterSettings) -> Unit)? = null,
    chapterOffsets: Set<Int> = emptySet(),
    remoteSyncNotice: RemotePositionNotice? = null,
    onAcceptRemoteSync: ((Int) -> Unit)? = null,
    onDismissRemoteSync: (() -> Unit)? = null,
    onAnchorChanged: ((Int) -> Unit)? = null,
    onViewSettingsChanged: ((ViewSettings) -> Unit)? = null,
    onBackToLibrary: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    onOpenInExplorer: (() -> Unit)? = null,
    onOpenInDefaultApp: (() -> Unit)? = null,
    onDeleteFile: (() -> Unit)? = null,
    deleteSettings: DeleteSettings = DeleteSettings(),
    onDeleteSettingsChanged: ((DeleteSettings) -> Unit)? = null,
    libraryFolder: String = "",
    keymap: KeymapSettings = KeymapSettings(),
    onKeymapChanged: ((KeymapSettings) -> Unit)? = null,
    currentLanguage: String = "SYSTEM",
    onLanguageChanged: ((String) -> Unit)? = null,
    onStartDropboxLogin: (() -> Unit)? = null,
    isDropboxLinked: Boolean = false,
    cachedSupabaseSecret: String? = null,
    isSupabaseConfigured: Boolean = false,
    isSupabaseVerified: Boolean = false,
    lastSupabaseTestError: String? = null,
    onRegenerateSecret: (() -> Unit)? = null,
    onTestSupabaseConnection: (() -> Unit)? = null,
    onForcePushCurrentPosition: (() -> Unit)? = null,
    forcePushInProgress: Boolean = false,
    forcePushResultMessage: String? = null,
    onRegisterKeyDispatcher: (((KeyEvent) -> Boolean)?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val colors = remember(viewSettings.theme) { ReaderColors.forName(viewSettings.theme) }
    val resolvedFontFamily = remember(viewSettings.fontFamily) { resolveFontFamily(viewSettings.fontFamily) }
    var showSettings by remember { mutableStateOf(false) }

    // ── Font catalog state (T-32) ──────────────────────────────────────────
    // fontRefreshTick is bumped after a download so the installed/not-installed
    // status is recomputed; without it the row would still say "다운로드 가능" ("Downloadable").
    var downloadingFontFamily by remember { mutableStateOf<String?>(null) }
    var fontDownloadProgress by remember { mutableStateOf(0f) }
    var fontError by remember { mutableStateOf<String?>(null) }
    var fontRefreshTick by remember { mutableStateOf(0) }
    val fontScope = rememberCoroutineScope()
    val systemFamilies = remember { getAvailableSystemFonts().toSet() }
    val fontStates = remember(fontRefreshTick, systemFamilies) {
        FontManager.states(systemFamilies)
    }
    var showToc by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchDialogState by remember(fullText) { mutableStateOf(SearchDialogState()) }
    var showRadioDialog by remember { mutableStateOf(false) }
    val radioPlaybackState by RadioPlayer.state
    var lastChapterJumpOffset by remember { mutableStateOf<Int?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    // The window-level dispatcher below is only rebuilt when its keys change, so read the
    // callback through updated state instead of capturing the first composition's lambda.
    val currentOnDeleteFile by rememberUpdatedState(onDeleteFile)

    var isHeaderVisible by remember { mutableStateOf(true) }
    var isHoveringTopRight by remember { mutableStateOf(false) }

    // Auto-hide top-right buttons after 5 seconds on startup unless hovered
    LaunchedEffect(Unit) {
        delay(5000L)
        if (!isHoveringTopRight) {
            isHeaderVisible = false
        }
    }

    // Auto-show when mouse hovers top-right area, auto-hide with 1.2s delay on exit
    LaunchedEffect(isHoveringTopRight) {
        if (isHoveringTopRight) {
            isHeaderVisible = true
        } else {
            delay(1200L)
            if (!isHoveringTopRight) {
                isHeaderVisible = false
            }
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2500)
            toastMessage = null
        }
    }

    // 20-20-20 eye strain reminder: ticks once per second while enabled, and forces
    // a blocking break overlay once EyeStrainScheduler crosses the active interval.
    val eyeStrainScheduler = remember { EyeStrainScheduler() }
    var eyeStrainState by remember { mutableStateOf<EyeStrainScheduler.State>(eyeStrainScheduler.state) }
    LaunchedEffect(viewSettings.eyeStrainReminderEnabled) {
        if (viewSettings.eyeStrainReminderEnabled) {
            while (true) {
                delay(1000L)
                eyeStrainScheduler.tick(1000L)
                eyeStrainState = eyeStrainScheduler.state
            }
        } else {
            eyeStrainScheduler.reset()
            eyeStrainState = eyeStrainScheduler.state
        }
    }
    val isOnEyeStrainBreak = eyeStrainState is EyeStrainScheduler.State.OnBreak

    var detectedChapters by remember { mutableStateOf<List<Chapter>?>(null) }
    LaunchedEffect(fullText) {
        withContext(Dispatchers.Default) {
            detectedChapters = ChapterDetector.detect(fullText)
        }
    }

    val activeChapterOffsets = remember(chapterOffsets, detectedChapters) {
        if (chapterOffsets.isNotEmpty()) chapterOffsets
        else (detectedChapters ?: emptyList()).map { it.charOffset }.toSet()
    }

    val cachedChapterOffsets = remember(detectedChapters) {
        val chapters = detectedChapters
        if (chapters.isNullOrEmpty()) emptyList()
        else ChapterJumpNavigator.chapterOffsets(chapters)
    }

    val cachedBreakpoints = remember(detectedChapters, fullText, chapterSettings.jumpDivisions) {
        val chapters = detectedChapters
        if (chapters.isNullOrEmpty()) {
            emptyList()
        } else {
            ChapterJumpNavigator.breakpoints(
                chapters = chapters,
                totalCharCount = fullText.length,
                divisions = chapterSettings.jumpDivisions.coerceAtLeast(1),
                text = fullText,
            )
        }
    }

    val style = remember(
        viewSettings.fontSizeSp,
        viewSettings.lineHeightMultiplier,
        viewSettings.letterSpacing,
        viewSettings.fontWeight,
        resolvedFontFamily,
        colors.text,
    ) {
        ReaderTextLayout.resolveTextStyle(
            viewSettings = viewSettings,
            fontFamily = resolvedFontFamily,
            textColor = colors.text,
        )
    }

    var currentAnchor by remember(navigator) { mutableStateOf(navigator.anchor) }

    LaunchedEffect(navigator.anchor) {
        currentAnchor = navigator.anchor
    }

    // Page-turn slide animation (1-pane only): remembers the anchor we're turning FROM so
    // the page being left and the page being arrived at can be stacked and slid past each
    // other by exactly the pixel amount advance()/retreat() just moved by. Left null for
    // anything that isn't a contiguous page turn (TOC/search/chapter jump, remote sync
    // accept) — those keep snapping instantly, since there is no adjacent page to slide in
    // from. beginPageTurnSlide() must be called BEFORE currentAnchor is updated, since it
    // reads currentAnchor as the "from" position.
    var slideFromAnchor by remember { mutableStateOf<Int?>(null) }
    var slideForward by remember { mutableStateOf(true) }
    val slideOffsetPx = remember { Animatable(0f) }

    fun beginPageTurnSlide(forward: Boolean) {
        if (!viewSettings.pageTurnAnimationEnabled || viewSettings.paneMode.equals("TWO", ignoreCase = true)) {
            return
        }
        slideFromAnchor = currentAnchor
        slideForward = forward
    }

    // Auto page-turn: waits a fixed interval after each page is shown, then advances the
    // same way a spacebar/arrow press would. Restarts whenever the page (currentAnchor)
    // changes, whether from the timer itself, a manual jump, TOC, or search.
    val autoPageTurnScheduler = remember(viewSettings.autoPageTurnIntervalSeconds) {
        AutoPageTurnScheduler(intervalSeconds = viewSettings.autoPageTurnIntervalSeconds)
    }
    var autoPageTurnRemainingRatio by remember { mutableStateOf(1f) }

    LaunchedEffect(viewSettings.autoPageTurnEnabled, currentAnchor, isOnEyeStrainBreak, autoPageTurnScheduler) {
        if (!viewSettings.autoPageTurnEnabled) {
            return@LaunchedEffect
        }
        if (currentAnchor >= fullText.length) {
            // Nothing left to turn to: switch the setting off and say why, rather than
            // leaving a "playing" icon that silently does nothing at the end of the book.
            toastMessage = Strings.get("reader_auto_page_turn_end_toast")
            onViewSettingsChanged?.invoke(viewSettings.copy(autoPageTurnEnabled = false))
            return@LaunchedEffect
        }
        if (isOnEyeStrainBreak) {
            return@LaunchedEffect
        }
        autoPageTurnScheduler.startPage()
        autoPageTurnRemainingRatio = 1f
        while (true) {
            delay(AUTO_PAGE_TURN_TICK_MS)
            autoPageTurnScheduler.tick(AUTO_PAGE_TURN_TICK_MS)
            autoPageTurnRemainingRatio = autoPageTurnScheduler.remainingRatio
            if (autoPageTurnScheduler.isReadyToTurn) {
                beginPageTurnSlide(forward = true)
                navigator.advance(viewSettings.advanceRatio.coerceIn(0.1f, 1.0f))
                currentAnchor = navigator.anchor
                onAnchorChanged?.invoke(currentAnchor)
                break
            }
        }
    }

    fun acceptRemoteSync(offset: Int) {
        lastChapterJumpOffset = null
        navigator.jumpTo(offset)
        currentAnchor = navigator.anchor
        onAnchorChanged?.invoke(currentAnchor)
        onAcceptRemoteSync?.invoke(offset)
    }

    fun performNextChapterJump() {
        val chapters = detectedChapters
        if (chapters.isNullOrEmpty()) {
            toastMessage = Strings.get("reader_no_chapter_toast")
            lastChapterJumpOffset = null
            beginPageTurnSlide(forward = true)
            navigator.advance(viewSettings.advanceRatio.coerceIn(0.1f, 1.0f))
            currentAnchor = navigator.anchor
            onAnchorChanged?.invoke(currentAnchor)
            return
        }
        val breakpoints = cachedBreakpoints
        val anchor = maxOf(currentAnchor, lastChapterJumpOffset ?: Int.MIN_VALUE)
        val target = ChapterJumpNavigator.nextBreakpoint(breakpoints, anchor)
        if (target != null) {
            lastChapterJumpOffset = target
            navigator.jumpTo(target, centerInOnePane = true, ratio = viewSettings.advanceRatio)
        } else {
            lastChapterJumpOffset = null
            beginPageTurnSlide(forward = true)
            navigator.advance(viewSettings.advanceRatio.coerceIn(0.1f, 1.0f))
        }
        currentAnchor = navigator.anchor
        onAnchorChanged?.invoke(currentAnchor)
    }

    fun performPreviousChapterJump() {
        val chapters = detectedChapters
        if (chapters.isNullOrEmpty()) {
            toastMessage = Strings.get("reader_no_chapter_toast")
            lastChapterJumpOffset = null
            beginPageTurnSlide(forward = false)
            navigator.retreat(viewSettings.advanceRatio.coerceIn(0.1f, 1.0f))
            currentAnchor = navigator.anchor
            onAnchorChanged?.invoke(currentAnchor)
            return
        }
        val breakpoints = cachedBreakpoints
        val anchor = minOf(currentAnchor, lastChapterJumpOffset ?: Int.MAX_VALUE)
        val target = ChapterJumpNavigator.previousBreakpoint(breakpoints, anchor)
        if (target != null) {
            lastChapterJumpOffset = target
            navigator.jumpTo(target, centerInOnePane = true, ratio = viewSettings.advanceRatio)
        } else {
            lastChapterJumpOffset = null
            beginPageTurnSlide(forward = false)
            navigator.retreat(viewSettings.advanceRatio.coerceIn(0.1f, 1.0f))
        }
        currentAnchor = navigator.anchor
        onAnchorChanged?.invoke(currentAnchor)
    }

    fun performNextChapter() {
        val chapters = detectedChapters
        if (chapters.isNullOrEmpty()) {
            toastMessage = Strings.get("reader_no_chapter_toast")
            lastChapterJumpOffset = null
            beginPageTurnSlide(forward = true)
            navigator.advance(viewSettings.advanceRatio.coerceIn(0.1f, 1.0f))
            currentAnchor = navigator.anchor
            onAnchorChanged?.invoke(currentAnchor)
            return
        }
        val anchor = maxOf(currentAnchor, lastChapterJumpOffset ?: Int.MIN_VALUE)
        val target = cachedChapterOffsets.firstOrNull { it > anchor }
        if (target != null) {
            lastChapterJumpOffset = target
            navigator.jumpTo(target, centerInOnePane = true, ratio = viewSettings.advanceRatio)
        } else {
            lastChapterJumpOffset = null
            beginPageTurnSlide(forward = true)
            navigator.advance(viewSettings.advanceRatio.coerceIn(0.1f, 1.0f))
        }
        currentAnchor = navigator.anchor
        onAnchorChanged?.invoke(currentAnchor)
    }

    fun performPreviousChapter() {
        val chapters = detectedChapters
        if (chapters.isNullOrEmpty()) {
            toastMessage = Strings.get("reader_no_chapter_toast")
            lastChapterJumpOffset = null
            beginPageTurnSlide(forward = false)
            navigator.retreat(viewSettings.advanceRatio.coerceIn(0.1f, 1.0f))
            currentAnchor = navigator.anchor
            onAnchorChanged?.invoke(currentAnchor)
            return
        }
        val anchor = minOf(currentAnchor, lastChapterJumpOffset ?: Int.MAX_VALUE)
        val target = cachedChapterOffsets.lastOrNull { it < anchor }
        if (target != null) {
            lastChapterJumpOffset = target
            navigator.jumpTo(target, centerInOnePane = true, ratio = viewSettings.advanceRatio)
        } else {
            lastChapterJumpOffset = null
            beginPageTurnSlide(forward = false)
            navigator.retreat(viewSettings.advanceRatio.coerceIn(0.1f, 1.0f))
        }
        currentAnchor = navigator.anchor
        onAnchorChanged?.invoke(currentAnchor)
    }

    fun performHome() {
        lastChapterJumpOffset = null
        navigator.jumpTo(0)
        currentAnchor = navigator.anchor
        onAnchorChanged?.invoke(currentAnchor)
    }

    // Window-level key event dispatcher registration:
    // Guarantees shortcuts always work regardless of focus, but passes through keys when a dialog is open.
    DisposableEffect(
        showSettings,
        showToc,
        showSearch,
        showRadioDialog,
        isOnEyeStrainBreak,
        remoteSyncNotice,
        viewSettings,
        chapterSettings,
        keymap,
        currentAnchor,
        detectedChapters,
        lastChapterJumpOffset,
        navigator,
    ) {
        val dispatcher: (KeyEvent) -> Boolean = { keyEvent ->
            if (isOnEyeStrainBreak) {
                // Swallow everything, including Escape: the break is not user-skippable.
                true
            } else if (showSettings || showToc || showSearch || showRadioDialog) {
                if (showSettings) {
                    // SettingsDialog manages its own keyboard handling (including Escape to clear a shortcut
                    // while recording, or Escape to dismiss), so pass through without window-level interception.
                    false
                } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                    if (showSearch) showSearch = false
                    else if (showToc) showToc = false
                    else if (showRadioDialog) showRadioDialog = false
                    true
                } else {
                    // Dialog is open: do NOT intercept shortcuts, pass through to dialog / text input
                    false
                }
            } else if (remoteSyncNotice != null) {
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (keyEvent.key == Key.Enter) {
                        acceptRemoteSync(remoteSyncNotice.charOffset)
                        true
                    } else if (keyEvent.key == Key.Escape) {
                        onDismissRemoteSync?.invoke()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } else {
                handleReaderKeyEvent(
                    keyEvent = keyEvent,
                    keymap = keymap,
                    navigator = navigator,
                    advanceRatio = viewSettings.advanceRatio,
                    onAnchorChanged = { newAnchor ->
                        lastChapterJumpOffset = null
                        if (newAnchor != currentAnchor) {
                            beginPageTurnSlide(forward = newAnchor > currentAnchor)
                        }
                        currentAnchor = newAnchor
                        onAnchorChanged?.invoke(newAnchor)
                    },
                    onOpenSettings = { showSettings = true },
                    onOpenToc = { showToc = true },
                    onOpenSearch = { showSearch = true },
                    onNextChapterJump = { performNextChapterJump() },
                    onPreviousChapterJump = { performPreviousChapterJump() },
                    onNextChapter = { performNextChapter() },
                    onPreviousChapter = { performPreviousChapter() },
                    onHome = { onHome?.invoke() ?: performHome() },
                    onBack = {
                        onBackToLibrary?.invoke()
                    },
                    onOpenInExplorer = { onOpenInExplorer?.invoke() },
                    onOpenInDefaultApp = { onOpenInDefaultApp?.invoke() },
                    onDeleteFile = { currentOnDeleteFile?.invoke() },
                    autoPageTurnEnabled = viewSettings.autoPageTurnEnabled,
                    onToggleAutoPageTurn = {
                        onViewSettingsChanged?.invoke(viewSettings.copy(autoPageTurnEnabled = !viewSettings.autoPageTurnEnabled))
                    },
                )
            }
        }
        onRegisterKeyDispatcher(dispatcher)
        onDispose {
            onRegisterKeyDispatcher(null)
        }
    }

    val paneMode = if (viewSettings.paneMode.equals("TWO", ignoreCase = true)) {
        PaneMode.TWO
    } else {
        PaneMode.ONE
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val totalWidthPx = with(density) { maxWidth.roundToPx() }
        val totalHeightPx = with(density) { maxHeight.roundToPx() }

        val horizontalMarginPx = with(density) { viewSettings.marginHorizontal.dp.roundToPx() }
        val topMarginPx = with(density) { viewSettings.marginTop.dp.roundToPx() }
        val bottomMarginPx = with(density) { (viewSettings.marginBottom.dp + 28.dp).roundToPx() } // extra space for corner footer

        val contentWidthPx = (totalWidthPx - (horizontalMarginPx * 2)).coerceAtLeast(1)
        val contentHeightPx = (totalHeightPx - topMarginPx - bottomMarginPx).coerceAtLeast(1)
        val gutterPx = with(density) { viewSettings.gutter.dp.roundToPx() }

        val fitter = remember(fullText, textMeasurer, style) {
            ComposeTextFitter(fullText, textMeasurer, style)
        }

        // Layout specification for 1-pane or 2-pane
        val spec = remember(contentWidthPx, contentHeightPx, paneMode, viewSettings.paneRatio, gutterPx, viewSettings.maxLineWidth) {
            ViewportSpec(
                widthPx = contentWidthPx,
                heightPx = contentHeightPx,
                paneMode = paneMode,
                paneRatio = viewSettings.paneRatio,
                gutterPx = gutterPx,
                maxLineWidthPx = if (viewSettings.maxLineWidth > 0) viewSettings.maxLineWidth else null,
            )
        }

        // Layout key change handling (window resize, settings change, fitter change, paneMode change):
        // Clears visit history as boundaries are invalidated, recalculates from anchor,
        // strictly preserving anchor itself.
        remember(spec, fitter) {
            if (navigator.spec != spec || navigator.textFitter != fitter) {
                navigator.onLayoutKeyChanged(spec, fitter)
                currentAnchor = navigator.anchor
            }
        }

        val layout = remember(currentAnchor, spec, fitter, viewSettings.alignChapterToLeftPane, activeChapterOffsets) {
            navigator.layoutFor(
                anchor = currentAnchor,
                spec = spec,
                alignChapterToLeftPane = viewSettings.alignChapterToLeftPane,
                chapterOffsets = activeChapterOffsets,
            )
        }

        val progressText = remember(currentAnchor, fullText.length) {
            ProgressFormatter.format(navigator.progress(currentAnchor, fullText.length))
        }

        val contentModifier = Modifier
            .fillMaxSize()
            .padding(
                start = viewSettings.marginHorizontal.dp,
                end = viewSettings.marginHorizontal.dp,
                top = viewSettings.marginTop.dp,
                bottom = (viewSettings.marginBottom.dp + 28.dp),
            )

        when (layout.paneMode) {
            PaneMode.ONE -> {
                val primaryPane = layout.primaryPane
                val pageText = remember(fullText, primaryPane.startOffset, primaryPane.endOffset) {
                    if (primaryPane.startOffset < fullText.length) {
                        fullText.substring(primaryPane.startOffset, minOf(primaryPane.endOffset, fullText.length))
                    } else {
                        ""
                    }
                }

                val displayText = remember(pageText, primaryPane.startOffset, activeChapterOffsets, colors.chapterHighlight) {
                    ReaderTextLayout.buildAnnotatedText(
                        rawText = pageText,
                        baseOffset = primaryPane.startOffset,
                        chapterOffsets = activeChapterOffsets,
                        chapterHighlightColor = colors.chapterHighlight,
                    )
                }

                val activeSlideFromAnchor = slideFromAnchor
                if (activeSlideFromAnchor != null && activeSlideFromAnchor != currentAnchor) {
                    // Half-page slide: the page being left and the page being arrived at are
                    // stacked in the same box, offset by exactly the pixel distance
                    // advance()/retreat() just moved the anchor by. Because that distance IS
                    // "half of what's visible" (or whatever advanceRatio is configured to),
                    // the tail of the old page is pixel-identical to the head of the new one,
                    // so the two slide past each other with no seam.
                    val previousLayout = remember(activeSlideFromAnchor, spec, viewSettings.alignChapterToLeftPane, activeChapterOffsets) {
                        navigator.layoutFor(
                            anchor = activeSlideFromAnchor,
                            spec = spec,
                            alignChapterToLeftPane = viewSettings.alignChapterToLeftPane,
                            chapterOffsets = activeChapterOffsets,
                        )
                    }
                    val previousPane = previousLayout.primaryPane
                    val previousPageText = remember(fullText, previousPane.startOffset, previousPane.endOffset) {
                        if (previousPane.startOffset < fullText.length) {
                            fullText.substring(previousPane.startOffset, minOf(previousPane.endOffset, fullText.length))
                        } else {
                            ""
                        }
                    }
                    val previousDisplayText = remember(previousPageText, previousPane.startOffset, activeChapterOffsets, colors.chapterHighlight) {
                        ReaderTextLayout.buildAnnotatedText(
                            rawText = previousPageText,
                            baseOffset = previousPane.startOffset,
                            chapterOffsets = activeChapterOffsets,
                            chapterHighlightColor = colors.chapterHighlight,
                        )
                    }

                    val slidePixels = contentHeightPx * viewSettings.advanceRatio.coerceIn(0.1f, 1.0f)
                    val turnedForward = slideForward

                    LaunchedEffect(activeSlideFromAnchor, currentAnchor, turnedForward, slidePixels, viewSettings.pageTurnAnimationSpeedMs) {
                        slideOffsetPx.snapTo(0f)
                        slideOffsetPx.animateTo(
                            targetValue = if (turnedForward) -slidePixels else slidePixels,
                            animationSpec = tween(durationMillis = viewSettings.pageTurnAnimationSpeedMs, easing = FastOutSlowInEasing),
                        )
                        slideFromAnchor = null
                    }

                    Box(
                        modifier = contentModifier.clipToBounds(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        // The old and new page always overlap by (1 - advanceRatio) of the
                        // viewport height while sliding — that's what makes the tail of one
                        // line up with the head of the other. Text has no opaque background,
                        // so without one here the overlap shows both pages' text blended
                        // together instead of the new page cleanly covering the old one.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = slideOffsetPx.value }
                                .background(colors.background),
                        ) {
                            Text(text = previousDisplayText, style = style)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationY = slideOffsetPx.value + (if (turnedForward) slidePixels else -slidePixels)
                                }
                                .background(colors.background),
                        ) {
                            Text(text = displayText, style = style)
                        }
                    }
                } else {
                    Box(
                        modifier = contentModifier,
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Text(
                            text = displayText,
                            style = style,
                        )
                    }
                }
            }

            PaneMode.TWO -> {
                val leftSpan = layout.leftPane
                val rightSpan = layout.rightPane

                val leftText = remember(fullText, leftSpan.startOffset, leftSpan.endOffset) {
                    if (leftSpan.startOffset < leftSpan.endOffset && leftSpan.startOffset < fullText.length) {
                        fullText.substring(leftSpan.startOffset, minOf(leftSpan.endOffset, fullText.length))
                    } else {
                        ""
                    }
                }

                val leftDisplay = remember(leftText, leftSpan.startOffset, activeChapterOffsets, colors.chapterHighlight) {
                    if (leftText.isNotEmpty()) {
                        ReaderTextLayout.buildAnnotatedText(
                            rawText = leftText,
                            baseOffset = leftSpan.startOffset,
                            chapterOffsets = activeChapterOffsets,
                            chapterHighlightColor = colors.chapterHighlight,
                        )
                    } else {
                        AnnotatedString("")
                    }
                }

                val rightText = remember(fullText, rightSpan?.startOffset, rightSpan?.endOffset) {
                    if (rightSpan != null && rightSpan.startOffset < rightSpan.endOffset && rightSpan.startOffset < fullText.length) {
                        fullText.substring(rightSpan.startOffset, minOf(rightSpan.endOffset, fullText.length))
                    } else {
                        ""
                    }
                }

                val rightDisplay = remember(rightText, rightSpan?.startOffset, activeChapterOffsets, colors.chapterHighlight) {
                    if (rightSpan != null && rightText.isNotEmpty()) {
                        ReaderTextLayout.buildAnnotatedText(
                            rawText = rightText,
                            baseOffset = rightSpan.startOffset,
                            chapterOffsets = activeChapterOffsets,
                            chapterHighlightColor = colors.chapterHighlight,
                        )
                    } else {
                        AnnotatedString("")
                    }
                }

                val leftPaneWidthDp = with(density) { spec.leftPaneWidthPx.toDp() }
                val rightPaneWidthDp = with(density) { spec.rightPaneWidthPx.toDp() }
                val gutterDp = with(density) { spec.gutterPx.toDp() }

                Row(
                    modifier = contentModifier,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier.width(leftPaneWidthDp).fillMaxHeight(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Text(text = leftDisplay, style = style)
                    }

                    Spacer(modifier = Modifier.width(gutterDp))

                    Box(
                        modifier = Modifier.width(rightPaneWidthDp).fillMaxHeight(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Text(text = rightDisplay, style = style)
                    }
                }
            }
        }

        // Auto page-turn countdown bar: a thin strip at the very bottom edge that depletes
        // as the next turn approaches. Only rendered while the feature is on, so it stays
        // completely invisible during normal reading.
        if (viewSettings.autoPageTurnEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(autoPageTurnRemainingRatio.coerceIn(0f, 1f))
                        .background(colors.progressText),
                )
            }
        }

        // Reading progress displayed in viewer bottom-right corner (always visible)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (radioPlaybackState.isPlaying) {
                    val streamTitle = radioPlaybackState.streamName ?: ""
                    val timeRemaining = radioPlaybackState.formatRemainingTime()
                    Text(
                        text = "[$streamTitle] $timeRemaining",
                        color = colors.progressText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = progressText,
                    color = colors.progressText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default,
                )
            }
        }

        // Header buttons in top-right corner with auto-hide and hover detection
        @OptIn(ExperimentalComposeUiApi::class)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(300.dp)
                .height(60.dp)
                .onPointerEvent(PointerEventType.Enter) {
                    isHoveringTopRight = true
                }
                .onPointerEvent(PointerEventType.Move) {
                    isHoveringTopRight = true
                }
                .onPointerEvent(PointerEventType.Exit) {
                    isHoveringTopRight = false
                },
            contentAlignment = Alignment.TopEnd,
        ) {
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .padding(end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBackToLibrary != null) {
                        Box(
                            modifier = Modifier
                                .clickable { onBackToLibrary.invoke() }
                                .padding(4.dp),
                        ) {
                            Text(
                                text = "📚",
                                color = colors.progressText,
                                fontSize = 17.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clickable {
                                if (radioPlaybackState.isPlaying) {
                                    RadioPlayer.stop()
                                } else {
                                    showRadioDialog = true
                                }
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MediaControlIcon(
                            shape = if (radioPlaybackState.isPlaying) MediaControlShape.STOP else MediaControlShape.PLAY,
                            size = 19.dp,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clickable {
                                onViewSettingsChanged?.invoke(
                                    viewSettings.copy(autoPageTurnEnabled = !viewSettings.autoPageTurnEnabled),
                                )
                            }
                            .padding(4.dp),
                    ) {
                        Text(
                            text = if (viewSettings.autoPageTurnEnabled) "⏸" else "▶",
                            color = colors.progressText,
                            fontSize = 17.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clickable { showSearch = true }
                            .padding(4.dp),
                    ) {
                        Text(
                            text = "🔍",
                            color = colors.progressText,
                            fontSize = 17.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clickable { showToc = true }
                            .padding(4.dp),
                    ) {
                        Text(
                            text = "☰",
                            color = colors.progressText,
                            fontSize = 18.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clickable { showSettings = true }
                            .padding(4.dp),
                    ) {
                        Text(
                            text = "⚙",
                            color = colors.progressText,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }

        // Toast notification chip
        if (toastMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .background(Color(0xFF1E1E22).copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    text = toastMessage!!,
                    color = Color(0xFFE5E7EB),
                    fontSize = 13.sp,
                )
            }
        }

        // Search Dialog Overlay
        if (showSearch) {
            SearchDialog(
                fullText = fullText,
                readerLayout = layout,
                initialState = searchDialogState,
                onStateChanged = { searchDialogState = it },
                onResultSelected = { result ->
                    val targetOffset = Search.findParagraphStart(fullText, result.charOffset)
                    lastChapterJumpOffset = targetOffset
                    navigator.jumpTo(targetOffset, centerInOnePane = true, ratio = viewSettings.advanceRatio)
                    currentAnchor = navigator.anchor
                    onAnchorChanged?.invoke(currentAnchor)
                    showSearch = false
                },
                onDismiss = { showSearch = false },
            )
        }

        // TOC Dialog Overlay
        if (showToc) {
            TocDialog(
                chapters = detectedChapters,
                currentAnchor = maxOf(currentAnchor, lastChapterJumpOffset ?: Int.MIN_VALUE),
                totalCharCount = fullText.length,
                onChapterSelected = { chapter ->
                    lastChapterJumpOffset = chapter.charOffset
                    navigator.jumpTo(chapter.charOffset, centerInOnePane = true, ratio = viewSettings.advanceRatio)
                    currentAnchor = navigator.anchor
                    onAnchorChanged?.invoke(currentAnchor)
                    showToc = false
                },
                onDismiss = { showToc = false },
            )
        }

        // Settings Dialog Overlay
        if (showSettings) {
            SettingsDialog(
                currentSettings = viewSettings,
                onSettingsChanged = { updated ->
                    onViewSettingsChanged?.invoke(updated)
                },
                onDismiss = { showSettings = false },
                currentLanguage = currentLanguage,
                onLanguageChanged = onLanguageChanged,
                currentKeymap = keymap,
                onKeymapChanged = onKeymapChanged,
                deleteSettings = deleteSettings,
                onDeleteSettingsChanged = onDeleteSettingsChanged,
                libraryFolder = libraryFolder,
                currentChapterSettings = chapterSettings,
                onChapterSettingsChanged = onChapterSettingsChanged,
                isDropboxLinked = isDropboxLinked,
                cachedSupabaseSecret = cachedSupabaseSecret,
                isSupabaseConfigured = isSupabaseConfigured,
                isSupabaseVerified = isSupabaseVerified,
                lastSupabaseTestError = lastSupabaseTestError,
                onStartDropboxLogin = {
                    showSettings = false
                    onStartDropboxLogin?.invoke()
                },
                onRegenerateSecret = onRegenerateSecret,
                onTestSupabaseConnection = onTestSupabaseConnection,
                onForcePushCurrentPosition = onForcePushCurrentPosition,
                forcePushInProgress = forcePushInProgress,
                forcePushResultMessage = forcePushResultMessage,
                fontStates = fontStates,
                downloadingFamily = downloadingFontFamily,
                downloadProgress = fontDownloadProgress,
                fontError = fontError,
                onDownloadFont = { font ->
                    // Download off the UI thread, then re-read status and apply.
                    // Applying automatically is the point of the feature: the user
                    // pressed "받기" ("Download") because they want to read in that font.
                    downloadingFontFamily = font.familyName
                    fontError = null
                    fontDownloadProgress = 0f
                    fontScope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                FontDownloader.download(font, FontManager.fontsDir()) { p ->
                                    fontDownloadProgress = p
                                }
                            }
                        }
                        downloadingFontFamily = null
                        fontDownloadProgress = 0f
                        result.onSuccess {
                            fontRefreshTick++
                            onViewSettingsChanged?.invoke(viewSettings.copy(fontFamily = font.familyName))
                        }.onFailure { e ->
                            // Never swallow: the user must know which font failed and why.
                            fontError = Strings.get("reader_font_download_error", font.displayName, e.message ?: (e::class.simpleName ?: ""))
                        }
                    }
                },
            )
        }

        // Radio BGM Stream Dialog Overlay
        if (showRadioDialog) {
            RadioDialog(
                onDismiss = { showRadioDialog = false },
                onToast = { msg ->
                    toastMessage = Strings.get("radio_error_toast", msg)
                },
            )
        }
        // Remote Reading Position Notice Overlay (Keyboard accessible: Enter to jump, Esc to dismiss)
        if (remoteSyncNotice != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { onDismissRemoteSync?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material.Surface(
                    modifier = Modifier
                        .width(420.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                    elevation = 16.dp,
                ) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource("reader_remote_sync_title"),
                            color = Color(0xFF38BDF8),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource("reader_remote_sync_desc", remoteSyncNotice.source, remoteSyncNotice.delta, remoteSyncNotice.charOffset),
                            color = Color(0xFFF1F5F9),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material.OutlinedButton(
                                onClick = { onDismissRemoteSync?.invoke() },
                                colors = androidx.compose.material.ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("reader_remote_sync_close"), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            androidx.compose.material.Button(
                                onClick = { acceptRemoteSync(remoteSyncNotice.charOffset) },
                                colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = Color(0xFF0284C7), contentColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("reader_remote_sync_jump"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Eye strain break overlay (20-20-20 rule): covers the entire reader, not
        // user-dismissable, disappears automatically when EyeStrainScheduler resumes.
        val breakState = eyeStrainState
        if (breakState is EyeStrainScheduler.State.OnBreak) {
            val remainingSeconds = ((breakState.remainingMs + 999L) / 1000L).toInt().coerceAtLeast(0)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = remainingSeconds.toString(),
                        color = Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource("reader_eye_strain_break_title"),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource("reader_eye_strain_break_desc"),
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}



