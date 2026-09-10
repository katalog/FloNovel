package com.moonkata.flonovel.android.ui.reader

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.TextMeasurer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonkata.flonovel.android.R
import com.moonkata.flonovel.android.data.datastore.AutoAdvanceMode
import com.moonkata.flonovel.android.data.datastore.OrientationLock
import com.moonkata.flonovel.android.data.datastore.PageGestureAction
import com.moonkata.flonovel.android.data.datastore.PageTransitionAnimation
import com.moonkata.flonovel.android.data.datastore.PageTurnMode
import com.moonkata.flonovel.android.data.datastore.ReaderSettings
import com.moonkata.flonovel.android.data.datastore.ReaderSettingsRepository
import com.moonkata.flonovel.android.data.datastore.ThemePreset
import com.moonkata.flonovel.android.data.datastore.TouchZoneMode
import com.moonkata.flonovel.android.data.db.AppDatabase
import com.moonkata.flonovel.android.data.db.BookEntity
import com.moonkata.flonovel.android.data.font.FontCatalogEntry
import com.moonkata.flonovel.android.data.font.FontDownloadManager
import com.moonkata.flonovel.android.data.font.FontDownloadState
import com.moonkata.flonovel.android.data.parser.ChapterDetector
import com.moonkata.flonovel.android.data.parser.ChapterPatternCatalog
import com.moonkata.flonovel.android.data.parser.ChapterJumpNavigator
import com.moonkata.flonovel.android.data.parser.PaginationParams
import com.moonkata.flonovel.android.data.parser.Paginator
import com.moonkata.flonovel.android.data.parser.ParagraphSplitter
import com.moonkata.flonovel.android.data.file.BookSource
import com.moonkata.flonovel.android.data.repository.BookRepository
import com.moonkata.flonovel.android.data.sync.ReadingPositionSyncClient
import com.moonkata.flonovel.android.data.sync.SupabaseConfig
import com.moonkata.flonovel.android.data.sync.relativePathFromSafDocumentUri
import com.moonkata.flonovel.android.model.Chapter
import com.moonkata.flonovel.android.model.PageBreak
import com.moonkata.flonovel.android.model.Paragraph
import com.moonkata.flonovel.android.model.SearchResult
import com.moonkata.flonovel.android.tts.AutoPageTurnController
import com.moonkata.flonovel.android.tts.TtsController
import com.moonkata.flonovel.android.ui.SettingsController
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: BookEntity? = null,
    val fullText: String = "",
    val paragraphs: List<Paragraph> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    /** The single page to show on screen right now in page mode — the full page list for the book is no longer held. */
    val currentPage: PageBreak? = null,
    val currentOffset: Int = 0,
    val settings: ReaderSettings = ReaderSettings(),
    /** Offset when a PC (VSCode) or another device has read further into this book — null means no popup.
     * See docs 06-SYNC-STRATEGY Part A. */
    val externalFurtherOffset: Int? = null,
)

sealed class ReaderNavEvent {
    data class JumpToOffset(val offset: Int, val animate: Boolean) : ReaderNavEvent()
    data object RequestNextPage : ReaderNavEvent()
    data object RequestPreviousPage : ReaderNavEvent()
    data object ToggleMenu : ReaderNavEvent()
}

/**
 * [bookRepository] is injected via the constructor — this lets tests swap in an isolated in-memory DB
 * instead of the production singleton `AppDatabase` (otherwise a bookId created by a test would point
 * at a completely different book row in the real app DB, which would break with a permission error if
 * that book is a real SAF file). Production wiring is handled by [ReaderViewModelFactory].
 */
class ReaderViewModel(
    application: Application,
    private val bookId: Long,
    private val bookRepository: BookRepository,
) : AndroidViewModel(application), SettingsController {

    val settingsRepository = ReaderSettingsRepository(application)
    private val fontDownloadManager = FontDownloadManager(application)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    private val _navEvents = MutableSharedFlow<ReaderNavEvent>(extraBufferCapacity = 4)
    val navEvents: SharedFlow<ReaderNavEvent> = _navEvents

    /** One-off, transient user-facing notices (e.g. "no chapter pattern found") — a string resource id
     * the screen shows as a Toast. Not part of [uiState] since these aren't persistent UI state. */
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages

    private var ttsController: TtsController? = null
    private var ttsPendingRange: Pair<Int, Int>? = null
    private val ttsChunkChars = 500

    /**
     * If the reading position stays put this long (5 minutes) without moving, also leave a checkpoint
     * remotely — see §remote sync. This was originally 1 minute; anticipating more users, the interval
     * was widened to reduce remote write frequency while keeping the immediate-push path on screen exit
     * unchanged (SYNC_MULTIUSER_PLAN.md stage 2).
     */
    private val remoteSyncIdleMs = 300_000L

    /**
     * Minimum interval between remote fetches (§checkRemoteAndMaybeNotify) — if the screen repeatedly
     * goes background↔foreground in a short span (e.g. opening the recent-apps list and immediately
     * closing it), ON_START fires every time and can trigger duplicate fetches, so no fetch is repeated
     * within this window of the last one (SYNC_MULTIUSER_PLAN.md stage 2).
     */
    private val remoteFetchCooldownMs = 30_000L

    /** Timestamp (ms) of the last time a remote fetch was actually attempted — see §remoteFetchCooldownMs. */
    private var lastRemoteFetchAtMs = 0L

    /**
     * Only show the "you've read further" popup when the remote is ahead by more than this many
     * characters — the VSCode cursor offset and the Android page offset point at fundamentally
     * different units (character offset vs. page start position), so even when reading the same spot
     * they can drift by a few hundred characters. Same value as the VSCode side (see §remote sync).
     */
    private val minOffsetDiffToNotify = 500

    private val autoPageTurnController = AutoPageTurnController(viewModelScope) { advance() }

    private var lastPaginationKey: String? = null
    private var pageComputeJob: Job? = null
    private var lastTextMeasurer: TextMeasurer? = null
    private var lastPaginationParams: PaginationParams? = null
    private var writePositionJob: Job? = null

    /** Pages navigated forward through — popped straight off the stack for "previous" without recomputing. */
    private val pageHistory = ArrayDeque<PageBreak>()

    /** Pages navigated backward through — popped straight off the stack for "next" without recomputing, restoring the exact page. */
    private val pageForwardHistory = ArrayDeque<PageBreak>()

    /**
     * The target offset of the last chapter jump — once a page actually settles, currentOffset gets
     * readjusted to that page's start offset (which can be earlier than the target), and if that value
     * were used as-is as the basis for the next breakpoint calculation, the same spot would be picked
     * again, requiring an extra tap. This separately remembers the actual target offset to use as the
     * basis instead, preventing that.
     */
    private var lastChapterJumpOffset: Int? = null

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                val previous = _uiState.value.settings
                _uiState.update { it.copy(settings = settings) }
                val patternsChanged = settings.chapterPatternEnabledIds != previous.chapterPatternEnabledIds ||
                    settings.chapterCustomPatterns != previous.chapterCustomPatterns
                if (patternsChanged && _uiState.value.fullText.isNotEmpty()) {
                    redetectChapters(settings)
                }
                handleAutoAdvanceModeChange(settings)
            }
        }
        viewModelScope.launch {
            bookRepository.observeBook(bookId).collect { book ->
                if (book != null) _uiState.update { it.copy(book = book) }
            }
        }
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var book = bookRepository.observeBook(bookId).first() ?: return@launch
            book = backfillRelativePathIfNeeded(book)
            val result = bookRepository.openBookContent(book)
            val settings = _uiState.value.settings
            val paragraphs = withContext(Dispatchers.Default) {
                ParagraphSplitter.split(result.text)
            }
            bookRepository.markOpened(bookId, result.text.length, result.encoding)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    fullText = result.text,
                    paragraphs = paragraphs,
                    currentOffset = book.lastReadCharOffset.coerceIn(0, result.text.length),
                )
            }
            // Baseline for what's already been pushed remotely — the same offset won't be pushed again (see §remote sync).
            lastRemoteSyncedOffset = book.lastReadCharOffset
            // Chapter detection (a line-by-line regex scan of the full text) isn't needed to show the
            // first page — pull it out of the loading gate to run in the background, and fill in
            // `chapters` later once it finishes.
            redetectChapters(settings)
            // Must use the `book` that just finished backfilling directly, not state.book — this check
            // can run before the observeBook Flow has reflected the new relativePath into _uiState (a timing race).
            checkRemoteAndMaybeNotify(book.relativePath, book.lastReadCharOffset, settings)
        }
    }

    /**
     * Checks whether another device such as VSCode has read further into this book — checked once when
     * the book is opened (loadBook), and again each time the reader screen becomes visible again (unlocking
     * the screen, returning from another app, etc., see §onReaderResumed). Does not keep polling while
     * reading — the other device's position only matters again at the moment the screen is seen again.
     */
    private fun checkExternalFurtherPositionNow() {
        val state = _uiState.value
        val book = state.book
        if (state.isLoading || book == null) return
        checkRemoteAndMaybeNotify(book.relativePath, state.currentOffset, state.settings)
    }

    private fun checkRemoteAndMaybeNotify(relativePath: String, localOffset: Int, settings: ReaderSettings) {
        if (relativePath.isEmpty()) return
        val client = syncClientOrNull(settings) ?: return
        val now = System.currentTimeMillis()
        if (now - lastRemoteFetchAtMs < remoteFetchCooldownMs) return
        lastRemoteFetchAtMs = now
        viewModelScope.launch {
            val remote = client.fetch(relativePath) ?: return@launch
            if (remote.charOffset - _uiState.value.currentOffset > minOffsetDiffToNotify) {
                _uiState.update { it.copy(externalFurtherOffset = remote.charOffset) }
            }
        }
    }

    /**
     * `LibraryViewModel` only computes and passes along relativePath while browsing folders (the
     * BrowseLocation stack), but it was found in real usage that opening via the "continue reading"
     * dialog or reopening an already-registered book doesn't go through that stack, so relativePath
     * kept ending up empty — contrary to the premise in §Open question 6 that treated this as a "niche
     * revisit," "continue reading" turned out to be the most common entry path instead. Every time a
     * book is opened, if relativePath is empty it's backfilled via a fallback that derives it from the
     * SAF document URI (see RelativePath.kt).
     */
    private suspend fun backfillRelativePathIfNeeded(book: BookEntity): BookEntity {
        if (book.relativePath.isNotEmpty()) return book
        val source = BookSource.fromStoredString(book.documentUri)
        if (source !is BookSource.PlainTxt) return book
        val treeUriString = settingsRepository.settingsFlow.first().lastUsedSafTreeUri ?: return book
        val relativePath = relativePathFromSafDocumentUri(source.uri, Uri.parse(treeUriString)) ?: return book
        bookRepository.updateRelativePath(bookId, relativePath)
        return book.copy(relativePath = relativePath)
    }

    /** Called when the reader screen becomes visible again (screen turned on, returning from another app, etc.) — wired to ON_START. */
    fun onReaderResumed() {
        checkExternalFurtherPositionNow()
    }

    /**
     * Returns null if the secret was only entered but "test connection" was never pressed (or the value
     * changed since the last verification) — this prevents silently and repeatedly sending requests that
     * would keep failing with an unverified secret. The "connected" badge on the settings screen must mean
     * exactly the same thing as this feature actually being on, or users will get confused.
     */
    private fun syncClientOrNull(settings: ReaderSettings): ReadingPositionSyncClient? {
        if (settings.supabaseSharedSecret.isBlank()) return null
        if (settings.supabaseSharedSecret != settings.supabaseVerifiedSecret) return null
        return ReadingPositionSyncClient(SupabaseConfig.URL, SupabaseConfig.PUBLISHABLE_KEY, settings.supabaseSharedSecret)
    }


    fun dismissExternalPositionPrompt() {
        _uiState.update { it.copy(externalFurtherOffset = null) }
    }

    fun jumpToExternalPosition() {
        val offset = _uiState.value.externalFurtherOffset ?: return
        _uiState.update { it.copy(externalFurtherOffset = null) }
        jumpToOffset(offset)
    }

    private suspend fun redetectChapters(settings: ReaderSettings) {
        val text = _uiState.value.fullText
        val patterns = ChapterPatternCatalog.buildPatterns(settings.chapterPatternEnabledIds, settings.chapterCustomPatterns)
        val chapters = withContext(Dispatchers.Default) { ChapterDetector.detect(text, patterns) }
        _uiState.update { it.copy(chapters = chapters) }
    }

    fun onViewportMeasured(textMeasurer: TextMeasurer, params: PaginationParams) {
        val state = _uiState.value
        if (state.settings.pageTurnMode != PageTurnMode.HORIZONTAL_PAGE) return
        if (state.paragraphs.isEmpty()) return
        val key = "${params.fontFamily}|${params.fontSizeSp}|${params.lineHeightMultiplier}|" +
            "${params.letterSpacingSp}|${params.contentWidthPx}|${params.contentHeightPx}"
        if (key == lastPaginationKey) return
        lastPaginationKey = key
        lastTextMeasurer = textMeasurer
        lastPaginationParams = params
        // Changing font/margins/screen size changes line wrapping, which changes the page boundaries
        // themselves — recompute only the current page based on the position being read right now. The
        // visit histories were built against the old page boundaries and are no longer valid, so clear them.
        pageHistory.clear()
        pageForwardHistory.clear()
        computeCurrentPageAt(state.currentOffset)
    }

    /** Computes just the one page starting at anchorOffset and reflects it into currentPage — used on initial load / settings change. */
    private fun computeCurrentPageAt(anchorOffset: Int) {
        val textMeasurer = lastTextMeasurer ?: return
        val params = lastPaginationParams ?: return
        val state = _uiState.value
        if (state.paragraphs.isEmpty()) return

        pageComputeJob?.cancel()
        pageComputeJob = viewModelScope.launch(Dispatchers.Default) {
            val safeOffset = anchorOffset.coerceIn(0, state.fullText.length)
            var page = Paginator.paginateFrom(state.fullText, state.paragraphs, safeOffset, textMeasurer, params, maxPages = 1).firstOrNull()
            if (page == null && state.fullText.isNotEmpty()) {
                // If safeOffset is at or near EOF (e.g. 100% position synced from PC), forward pagination finds no subsequent text.
                // Fall back to the final page ending at EOF so the user sees the final page without entering infinite loading.
                page = Paginator.onePageEndingAt(
                    fullText = state.fullText,
                    paragraphs = state.paragraphs,
                    endOffset = state.fullText.length,
                    textMeasurer = textMeasurer,
                    params = params,
                    referenceSpanChars = 500,
                ) ?: Paginator.paginateFrom(state.fullText, state.paragraphs, 0, textMeasurer, params, maxPages = 1).firstOrNull()
            }
            _uiState.update { it.copy(currentPage = page ?: PageBreak(0, 0)) }
        }
    }

    /** In page mode, jumps immediately to the page starting at offset — shared by search/TOC/bookmark/chapter jump. */
    private fun jumpToPageAt(offset: Int) {
        pageHistory.clear()
        pageForwardHistory.clear()
        computeCurrentPageAt(offset)
    }

    /** In page mode, advances one page forward — pops from forward history if available, or computes next page from current end. */
    private fun advancePageForward() {
        val textMeasurer = lastTextMeasurer ?: return
        val params = lastPaginationParams ?: return
        val state = _uiState.value
        val current = state.currentPage ?: return
        if (current.endOffset >= state.fullText.length) return // already the last page

        // 1. If forward history exists (e.g. user retreated after a jump or page turn), restore exact page!
        val fromForward = pageForwardHistory.removeLastOrNull()
        if (fromForward != null) {
            pageHistory.addLast(current)
            _uiState.update { it.copy(currentPage = fromForward) }
            updateCurrentOffset(fromForward.startOffset)
            return
        }

        // 2. Otherwise compute next page normally
        pageHistory.addLast(current)
        pageComputeJob?.cancel()
        pageComputeJob = viewModelScope.launch(Dispatchers.Default) {
            val next = Paginator.paginateFrom(state.fullText, state.paragraphs, current.endOffset, textMeasurer, params, maxPages = 1).firstOrNull()
            if (next != null) {
                _uiState.update { it.copy(currentPage = next) }
                updateCurrentOffset(next.startOffset)
            }
        }
    }

    /**
     * In page mode, goes back one page:
     * - Saves current page into forward history so that a subsequent "next" returns to it exactly.
     * - If visit history is present: popped and used as-is (accurate, immediate).
     * - If visit history is empty: estimated in reverse via [Paginator.onePageEndingAt].
     */
    private fun advancePageBackward() {
        val textMeasurer = lastTextMeasurer ?: return
        val params = lastPaginationParams ?: return
        val state = _uiState.value
        val current = state.currentPage ?: return
        if (current.startOffset <= 0) {
            _messages.tryEmit(R.string.reader_notice_first_page)
            return // already the first page
        }

        _messages.tryEmit(R.string.reader_notice_previous_page)

        // Save current page into forward history before moving backward
        pageForwardHistory.addLast(current)

        val fromHistory = pageHistory.removeLastOrNull()
        if (fromHistory != null) {
            _uiState.update { it.copy(currentPage = fromHistory) }
            updateCurrentOffset(fromHistory.startOffset)
            return
        }

        val referenceSpan = (current.endOffset - current.startOffset).coerceAtLeast(1)
        pageComputeJob?.cancel()
        pageComputeJob = viewModelScope.launch(Dispatchers.Default) {
            val prev = Paginator.onePageEndingAt(state.fullText, state.paragraphs, current.startOffset, textMeasurer, params, referenceSpan)
            if (prev != null) {
                _uiState.update { it.copy(currentPage = prev) }
                updateCurrentOffset(prev.startOffset)
            }
        }
    }

    /** The last offset not yet written to the DB — held onto so it isn't lost if the screen is left before the debounce timer finishes. */
    private var pendingWriteOffset: Int? = null

    fun updateCurrentOffset(offset: Int, persist: Boolean = true) {
        _uiState.update { it.copy(currentOffset = offset) }
        if (persist) {
            schedulePositionWrite(offset)
            scheduleRemoteSyncCheckpoint(offset)
        }
    }

    private fun schedulePositionWrite(offset: Int) {
        pendingWriteOffset = offset
        writePositionJob?.cancel()
        writePositionJob = viewModelScope.launch {
            delay(500)
            flushPendingPosition()
        }
    }

    /**
     * Immediately persists the last reading position locally (Room) without waiting for the debounce
     * timer. Called when the screen goes to the background (ON_STOP) or the reader is left, to prevent
     * losing the position. Remote (Supabase) sync is handled through a separate path (§remote sync,
     * [syncNowToRemote]) — local saves are essentially free even when they happen frequently on every
     * page/paragraph change, but remote is a network call, so pushing on every change would be wasteful;
     * the triggers were kept separate for that reason.
     */
    fun flushPendingPosition() {
        val offset = consumePendingOffset() ?: return
        viewModelScope.launch { persistPositionLocal(offset) }
    }

    private fun consumePendingOffset(): Int? {
        val offset = pendingWriteOffset ?: return null
        pendingWriteOffset = null
        writePositionJob?.cancel()
        return offset
    }

    private suspend fun persistPositionLocal(offset: Int) {
        val total = _uiState.value.fullText.length
        val progress = if (total > 0) offset.toFloat() / total else 0f
        bookRepository.updateReadPosition(bookId, offset, progress)
    }

    // --- Remote (Supabase) sync ---
    // Pushing on every page/paragraph move would be wasteful, so remote is only updated through these two paths:
    // 1) When the position stays put for remoteSyncIdleMs (5 minutes) or longer (checkpoint)
    // 2) Immediately when leaving the reader screen (back press → onCleared / screen off, home, switching apps → ON_STOP)

    private var remoteCheckpointJob: Job? = null

    /** The offset last actually pushed to remote — if it matches, it isn't pushed again, cutting down unnecessary calls. */
    private var lastRemoteSyncedOffset: Int? = null

    private fun scheduleRemoteSyncCheckpoint(offset: Int) {
        remoteCheckpointJob?.cancel()
        remoteCheckpointJob = viewModelScope.launch {
            delay(remoteSyncIdleMs)
            pushRemoteSync(offset)
        }
    }

    /** Called at the moment the viewer is left — immediately pushes the current position to remote without waiting for the checkpoint timer. */
    fun syncNowToRemote() {
        remoteCheckpointJob?.cancel()
        pushRemoteSync(_uiState.value.currentOffset)
    }

    private fun pushRemoteSync(offset: Int) {
        if (offset == lastRemoteSyncedOffset) return
        val state = _uiState.value
        val book = state.book
        val relativePath = book?.relativePath.orEmpty()
        if (relativePath.isEmpty()) return
        val client = syncClientOrNull(state.settings) ?: return
        lastRemoteSyncedOffset = offset
        // Deliberately split off onto GlobalScope — if this were tied to viewModelScope, it would be
        // already cancelled right after onCleared (exactly when the runBlocking local save finishes), so
        // this upsert would never even start. The position at the moment the screen is left is precisely
        // when syncing matters most, so it needs to go out best-effort, independent of the view model's lifecycle.
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) { client.upsert(relativePath, offset, book?.detectedEncoding) }
    }

    fun jumpToOffset(offset: Int) {
        lastChapterJumpOffset = null
        val safeOffset = offset.coerceIn(0, _uiState.value.fullText.length)
        updateCurrentOffset(safeOffset)
        if (_uiState.value.settings.pageTurnMode == PageTurnMode.HORIZONTAL_PAGE) {
            jumpToPageAt(safeOffset)
        } else {
            _navEvents.tryEmit(ReaderNavEvent.JumpToOffset(safeOffset, animate = true))
        }
    }

    /** Routes one of the configurable gestures (touch zones / swipe directions) to the action assigned to it. */
    fun performGestureAction(action: PageGestureAction) {
        when (action) {
            PageGestureAction.PREVIOUS_PAGE -> previousPage()
            PageGestureAction.NEXT_PAGE -> nextPage()
            PageGestureAction.PREVIOUS_CHAPTER_JUMP -> previousChapterJump()
            PageGestureAction.NEXT_CHAPTER_JUMP -> nextChapterJump()
            PageGestureAction.PREVIOUS_CHAPTER -> previousChapter()
            PageGestureAction.NEXT_CHAPTER -> nextChapter()
            PageGestureAction.SHOW_MENU -> _navEvents.tryEmit(ReaderNavEvent.ToggleMenu)
            PageGestureAction.NONE -> {}
        }
    }

    fun nextPage() = advanceNormally(_uiState.value)

    fun previousPage() = retreatNormally(_uiState.value)

    fun nextChapter() {
        val state = _uiState.value
        if (state.chapters.isEmpty()) {
            _messages.tryEmit(R.string.reader_chapter_jump_no_pattern)
            advanceNormally(state)
            return
        }
        val targetChapter = state.chapters.firstOrNull { it.charOffset > state.currentOffset }
        if (targetChapter == null) {
            _messages.tryEmit(R.string.reader_notice_last_chapter)
            advanceNormally(state)
            return
        }
        val target = targetChapter.charOffset
        lastChapterJumpOffset = target
        updateCurrentOffset(target)
        if (state.settings.pageTurnMode == PageTurnMode.HORIZONTAL_PAGE) {
            jumpToPageAt(target)
        } else {
            _navEvents.tryEmit(ReaderNavEvent.JumpToOffset(target, animate = false))
        }
    }

    fun previousChapter() {
        val state = _uiState.value
        if (state.chapters.isEmpty()) {
            _messages.tryEmit(R.string.reader_chapter_jump_no_pattern)
            retreatNormally(state)
            return
        }
        val currentChapter = state.chapters.lastOrNull { it.charOffset <= state.currentOffset }
        val targetChapter = if (currentChapter != null && state.currentOffset > currentChapter.charOffset + 30) {
            currentChapter
        } else {
            state.chapters.lastOrNull { it.charOffset < (currentChapter?.charOffset ?: state.currentOffset) }
        }
        if (targetChapter == null) {
            _messages.tryEmit(R.string.reader_notice_first_chapter)
            retreatNormally(state)
            return
        }
        val target = targetChapter.charOffset
        lastChapterJumpOffset = target
        updateCurrentOffset(target)
        if (state.settings.pageTurnMode == PageTurnMode.HORIZONTAL_PAGE) {
            jumpToPageAt(target)
        } else {
            _navEvents.tryEmit(ReaderNavEvent.JumpToOffset(target, animate = false))
        }
    }

    fun nextChapterJump() {
        val state = _uiState.value
        if (state.chapters.isEmpty()) {
            // No chapter pattern matched anything in this book — say so, then still turn the page
            // normally so the gesture doesn't feel like it did nothing.
            _messages.tryEmit(R.string.reader_chapter_jump_no_pattern)
            advanceNormally(state)
            return
        }
        val breakpoints = ChapterJumpNavigator.breakpoints(state.chapters, state.fullText.length, state.settings.chapterJumpDivisions, state.fullText)
        val anchor = maxOf(state.currentOffset, lastChapterJumpOffset ?: Int.MIN_VALUE)
        val target = ChapterJumpNavigator.nextBreakpoint(breakpoints, anchor)
        if (target == null) {
            // No more breakpoints to jump to (past the last chapter) — fall back to a plain page turn so the tap doesn't "do nothing."
            advanceNormally(state)
            return
        }
        lastChapterJumpOffset = target
        updateCurrentOffset(target)
        _messages.tryEmit(R.string.reader_notice_next_chapter_jump)
        if (state.settings.pageTurnMode == PageTurnMode.HORIZONTAL_PAGE) {
            jumpToPageAt(target)
        } else {
            _navEvents.tryEmit(ReaderNavEvent.JumpToOffset(target, animate = false))
        }
    }

    fun previousChapterJump() {
        val state = _uiState.value
        if (state.chapters.isEmpty()) {
            _messages.tryEmit(R.string.reader_chapter_jump_no_pattern)
            retreatNormally(state)
            return
        }
        val breakpoints = ChapterJumpNavigator.breakpoints(state.chapters, state.fullText.length, state.settings.chapterJumpDivisions, state.fullText)
        val anchor = minOf(state.currentOffset, lastChapterJumpOffset ?: Int.MAX_VALUE)
        val target = ChapterJumpNavigator.previousBreakpoint(breakpoints, anchor)
        if (target == null) {
            retreatNormally(state)
            return
        }
        lastChapterJumpOffset = target
        updateCurrentOffset(target)
        _messages.tryEmit(R.string.reader_notice_previous_chapter_jump)
        if (state.settings.pageTurnMode == PageTurnMode.HORIZONTAL_PAGE) {
            jumpToPageAt(target)
        } else {
            _navEvents.tryEmit(ReaderNavEvent.JumpToOffset(target, animate = false))
        }
    }

    private fun advanceNormally(state: ReaderUiState) {
        if (state.settings.pageTurnMode == PageTurnMode.HORIZONTAL_PAGE) {
            advancePageForward()
        } else {
            _navEvents.tryEmit(ReaderNavEvent.RequestNextPage)
        }
    }

    private fun retreatNormally(state: ReaderUiState) {
        if (state.settings.pageTurnMode == PageTurnMode.HORIZONTAL_PAGE) {
            advancePageBackward()
        } else {
            if (state.currentOffset <= 0) {
                _messages.tryEmit(R.string.reader_notice_first_page)
            } else {
                _messages.tryEmit(R.string.reader_notice_previous_page)
            }
            _navEvents.tryEmit(ReaderNavEvent.RequestPreviousPage)
        }
    }

    // --- Search ---
    /** Remembered so the last search results can still be seen after closing and reopening the search sheet. */
    var lastSearchQuery: String = ""
        private set
    var lastSearchResults: List<SearchResult> = emptyList()
        private set

    fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val text = _uiState.value.fullText
        val results = mutableListOf<SearchResult>()
        var idx = text.indexOf(query, 0, ignoreCase = true)
        while (idx >= 0) {
            val snippetStart = (idx - 20).coerceAtLeast(0)
            val snippetEnd = (idx + query.length + 20).coerceAtMost(text.length)
            // If a snippet spans a paragraph boundary (blank line), the raw newlines eat up the
            // preview's maxLines with blank lines and push the actual match off screen — collapse them
            // into a single space instead.
            val snippet = text.substring(snippetStart, snippetEnd).replace(Regex("\\s+"), " ").trim()
            results += SearchResult(idx, snippet)
            idx = text.indexOf(query, idx + query.length, ignoreCase = true)
        }
        lastSearchQuery = query
        lastSearchResults = results
        return results
    }

    // --- Setting setters (SettingsController implementation) ---
    override fun setFontSizeSp(value: Float) = launchSetting { settingsRepository.updateFontSizeSp(value) }
    override fun setLineHeightMultiplier(value: Float) = launchSetting { settingsRepository.updateLineHeightMultiplier(value) }
    override fun setLetterSpacingSp(value: Float) = launchSetting { settingsRepository.updateLetterSpacingSp(value) }
    override fun setMarginHorizontalDp(value: Float) = launchSetting { settingsRepository.updateMarginHorizontalDp(value) }
    override fun setMarginTopDp(value: Float) = launchSetting { settingsRepository.updateMarginTopDp(value) }
    override fun setMarginBottomDp(value: Float) = launchSetting { settingsRepository.updateMarginBottomDp(value) }
    override fun setThemePreset(value: ThemePreset) = launchSetting { settingsRepository.updateThemePreset(value) }
    override fun setPageTurnMode(value: PageTurnMode) = launchSetting { settingsRepository.updatePageTurnMode(value) }
    override fun setBrightnessOverrideEnabled(value: Boolean) = launchSetting { settingsRepository.updateBrightnessOverrideEnabled(value) }
    override fun setBrightnessValue(value: Float) = launchSetting { settingsRepository.updateBrightnessValue(value) }
    override fun setOrientationLock(value: OrientationLock) = launchSetting { settingsRepository.updateOrientationLock(value) }
    override fun setKeepScreenOnEnabled(value: Boolean) = launchSetting { settingsRepository.updateKeepScreenOnEnabled(value) }
    override fun setVolumeKeyPagingEnabled(value: Boolean) = launchSetting { settingsRepository.updateVolumeKeyPagingEnabled(value) }
    override fun setChapterJumpDivisions(value: Int) = launchSetting { settingsRepository.updateChapterJumpDivisions(value) }
    override fun setAutoPageTurnIntervalSeconds(value: Int) = launchSetting { settingsRepository.updateAutoPageTurnIntervalSeconds(value) }
    override fun selectFont(fontId: String) = launchSetting { settingsRepository.updateFontFamilyId(fontId) }
    override fun setTouchZoneMode(value: TouchZoneMode) = launchSetting { settingsRepository.updateTouchZoneMode(value) }
    override fun setGridTouchAction(index: Int, action: PageGestureAction) = launchSetting { settingsRepository.updateGridTouchAction(index, action) }
    override fun setTouchLeftAction(value: PageGestureAction) = launchSetting { settingsRepository.updateTouchLeftAction(value) }
    override fun setTouchRightAction(value: PageGestureAction) = launchSetting { settingsRepository.updateTouchRightAction(value) }
    override fun setSwipeLeftAction(value: PageGestureAction) = launchSetting { settingsRepository.updateSwipeLeftAction(value) }
    override fun setSwipeRightAction(value: PageGestureAction) = launchSetting { settingsRepository.updateSwipeRightAction(value) }
    override fun setSwipeUpAction(value: PageGestureAction) = launchSetting { settingsRepository.updateSwipeUpAction(value) }
    override fun setSwipeDownAction(value: PageGestureAction) = launchSetting { settingsRepository.updateSwipeDownAction(value) }
    override fun setPageTransitionAnimation(value: PageTransitionAnimation) = launchSetting { settingsRepository.updatePageTransitionAnimation(value) }

    // --- Chapter detection patterns ---
    override fun toggleChapterPattern(id: String, enabled: Boolean) = launchSetting {
        val current = _uiState.value.settings.chapterPatternEnabledIds
        val updated = if (enabled) current + id else current - id
        settingsRepository.updateChapterPatternEnabledIds(updated)
    }

    /** If the pattern is not a well-formed regex, it is not added and false is returned. */
    override fun addCustomChapterPattern(pattern: String): Boolean {
        if (pattern.isBlank() || runCatching { Regex(pattern) }.isFailure) return false
        launchSetting {
            val current = _uiState.value.settings.chapterCustomPatterns
            settingsRepository.updateChapterCustomPatterns(current + pattern)
        }
        return true
    }

    override fun removeCustomChapterPattern(pattern: String) = launchSetting {
        val current = _uiState.value.settings.chapterCustomPatterns
        settingsRepository.updateChapterCustomPatterns(current - pattern)
    }

    override fun setAutoAdvanceMode(mode: AutoAdvanceMode) {
        if (mode == AutoAdvanceMode.TTS) {
            startTts()
        } else {
            launchSetting { settingsRepository.updateAutoAdvanceMode(mode) }
        }
    }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // --- Font download ---
    override fun downloadFont(entry: FontCatalogEntry) = fontDownloadManager.download(entry)
    override fun isFontDownloaded(entry: FontCatalogEntry) = fontDownloadManager.isDownloaded(entry)

    // --- TTS ---
    fun startTts() {
        if (ttsController == null) {
            ttsController = TtsController(getApplication()) { utteranceId -> onTtsUtteranceDone(utteranceId) }
        }
        viewModelScope.launch { settingsRepository.updateAutoAdvanceMode(AutoAdvanceMode.TTS) }
        speakFromCurrentOffset()
    }

    fun stopTts() {
        ttsController?.stop()
        viewModelScope.launch { settingsRepository.updateAutoAdvanceMode(AutoAdvanceMode.OFF) }
    }

    private fun speakFromCurrentOffset() {
        val state = _uiState.value
        val start = state.currentOffset
        if (start >= state.fullText.length) {
            stopTts()
            return
        }
        val end = (start + ttsChunkChars).coerceAtMost(state.fullText.length)
        ttsPendingRange = start to end
        ttsController?.speak(start, state.fullText.substring(start, end))
    }

    private fun onTtsUtteranceDone(utteranceId: String) {
        viewModelScope.launch {
            val range = ttsPendingRange ?: return@launch
            if (_uiState.value.settings.autoAdvanceMode != AutoAdvanceMode.TTS) return@launch
            jumpToOffset(range.second)
            speakFromCurrentOffset()
        }
    }

    // --- Auto-advance (timer) ---
    private fun advance() {
        if (_uiState.value.settings.autoAdvanceMode != AutoAdvanceMode.TIMER) return
        nextPage()
    }

    private var lastTimerMode: AutoAdvanceMode? = null
    private var lastTimerIntervalSeconds: Int? = null

    private fun handleAutoAdvanceModeChange(settings: ReaderSettings) {
        if (settings.autoAdvanceMode == AutoAdvanceMode.TIMER) {
            if (lastTimerMode != AutoAdvanceMode.TIMER || lastTimerIntervalSeconds != settings.autoPageTurnIntervalSeconds) {
                autoPageTurnController.start(settings.autoPageTurnIntervalSeconds)
            }
        } else {
            autoPageTurnController.stop()
        }
        lastTimerMode = settings.autoAdvanceMode
        lastTimerIntervalSeconds = settings.autoPageTurnIntervalSeconds
        if (settings.autoAdvanceMode != AutoAdvanceMode.TTS) {
            ttsController?.stop()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is cancelled right after onCleared, so any not-yet-saved position is flushed immediately with a blocking call.
        consumePendingOffset()?.let { offset -> runBlocking { persistPositionLocal(offset) } }
        // The moment the reader screen is left entirely, e.g. via back press — push immediately without waiting for the remote checkpoint either.
        syncNowToRemote()
        pageComputeJob?.cancel()
        autoPageTurnController.stop()
        ttsController?.shutdown()
    }
}

class ReaderViewModelFactory(
    private val application: Application,
    private val bookId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val bookRepository = BookRepository(application, AppDatabase.getDatabase(application).bookDao())
        return ReaderViewModel(application, bookId, bookRepository) as T
    }
}
