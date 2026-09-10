package com.moonkata.flonovel.android.ui.library

import android.app.Application
import android.net.Uri
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
import com.moonkata.flonovel.android.data.file.BookSource
import com.moonkata.flonovel.android.data.file.FolderBrowser
import com.moonkata.flonovel.android.data.file.SafFolderBrowser
import com.moonkata.flonovel.android.data.font.FontCatalogEntry
import com.moonkata.flonovel.android.data.font.FontDownloadManager
import com.moonkata.flonovel.android.data.repository.BookRepository
import com.moonkata.flonovel.android.data.sync.DropboxAuthRedirect
import com.moonkata.flonovel.android.data.sync.DropboxAuthSession
import com.moonkata.flonovel.android.data.sync.DropboxClient
import com.moonkata.flonovel.android.data.sync.DropboxConfig
import com.moonkata.flonovel.android.data.sync.DropboxFileSync
import com.moonkata.flonovel.android.data.sync.DropboxOAuth
import com.moonkata.flonovel.android.data.sync.DropboxSyncProgress
import com.moonkata.flonovel.android.data.sync.DropboxSyncResult
import com.moonkata.flonovel.android.data.sync.ReadingPositionSyncClient
import com.moonkata.flonovel.android.data.sync.SecretResult
import com.moonkata.flonovel.android.data.sync.SecretStore
import com.moonkata.flonovel.android.data.sync.SupabaseConfig
import com.moonkata.flonovel.android.data.sync.normalizeRelativePath
import com.moonkata.flonovel.android.model.FolderEntry
import com.moonkata.flonovel.android.model.FolderSortOption
import com.moonkata.flonovel.android.ui.SettingsController
import com.moonkata.flonovel.android.util.hasPersistedReadPermission
import com.moonkata.flonovel.android.util.hasPersistedWritePermission
import com.moonkata.flonovel.android.util.takePersistableReadWritePermission
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One step of where the folder view is currently looking — either a real SAF folder or inside a zip archive. */
sealed class BrowseLocation {
    abstract val name: String
    data class Folder(val uri: Uri, override val name: String) : BrowseLocation()
    data class Zip(val uri: Uri, override val name: String) : BrowseLocation()
}

private data class BrowseState(
    val rootUri: Uri? = null,
    val path: List<BrowseLocation> = emptyList(),
    val entries: List<FolderEntry> = emptyList(),
    val isLoading: Boolean = false,
    val sortOption: FolderSortOption = FolderSortOption.NAME_ASC,
    val folderAccessLost: Boolean = false,
)

/** "Sync now" state — `LibraryViewModel.dropboxState`. */
data class DropboxUiState(
    val isSyncing: Boolean = false,
    val isConnecting: Boolean = false,
    val progress: DropboxSyncProgress? = null,
    val result: DropboxSyncResult? = null,
    val errorMessage: String? = null,
    /** Null until a fetch has been attempted. Drives the "position sync" line in the sheet. */
    val secretState: SecretResult? = null,
)

data class LibraryUiState(
    val rootUri: Uri? = null,
    val path: List<BrowseLocation> = emptyList(),
    val entries: List<FolderEntry> = emptyList(),
    val isLoading: Boolean = false,
    val sortOption: FolderSortOption = FolderSortOption.NAME_ASC,
    val progressByStoredUri: Map<String, Float> = emptyMap(),
    val settings: ReaderSettings = ReaderSettings(),
    // True when a home folder was picked before, but its SAF permission grant no longer exists —
    // e.g. Android's Auto Backup restored the saved URI string on a fresh install without the grant
    // surviving (SAF permissions are re-issued per install), or the folder itself was deleted/moved.
    // Distinguished from "never picked one" (rootUri == null, this false) so the empty state can tell
    // the user to re-pick rather than implying no folder was ever added.
    val folderAccessLost: Boolean = false,
)

/**
 * [bookRepository]/[settingsRepository]/[folderBrowser] are injected via the constructor — so tests
 * can swap in fake implementations to verify folder-browsing scenarios without real SAF permissions
 * or the app's real Room DB. Production wiring is handled by [LibraryViewModelFactory].
 */
class LibraryViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val folderBrowser: FolderBrowser,
) : AndroidViewModel(application), SettingsController {

    private val fontDownloadManager = FontDownloadManager(application)

    private val _browseState = MutableStateFlow(BrowseState())

    private val _openBookEvents = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openBookEvents: SharedFlow<Long> = _openBookEvents

    // Candidate for the "resume reading?" prompt, asked exactly once per fresh app launch — once the
    // user answers, it won't show again for as long as this process stays alive (even after
    // navigating back to the library and re-reading).
    private val _resumeCandidate = MutableStateFlow<BookEntity?>(null)
    val resumeCandidate: StateFlow<BookEntity?> = _resumeCandidate

    fun dismissResumePrompt() {
        _resumeCandidate.value = null
    }

    // --- Dropbox file sync (docs 06-SYNC-STRATEGY Part B) ---

    private val dropboxClient = DropboxClient(settingsRepository)
    private val dropboxFileSync = DropboxFileSync(getApplication(), dropboxClient, settingsRepository)
    private val secretStore = SecretStore(dropboxClient)

    private val _dropboxState = MutableStateFlow(DropboxUiState())
    val dropboxState: StateFlow<DropboxUiState> = _dropboxState

    fun dismissDropboxResult() {
        _dropboxState.update { it.copy(result = null, errorMessage = null) }
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(_browseState, bookRepository.observeLibrary(), settingsRepository.settingsFlow) { browse, books, settings ->
            LibraryUiState(
                rootUri = browse.rootUri,
                path = browse.path,
                entries = sortEntries(browse.entries, browse.sortOption),
                isLoading = browse.isLoading,
                sortOption = browse.sortOption,
                progressByStoredUri = books.associate { it.documentUri to it.lastReadProgressPercent },
                settings = settings,
                folderAccessLost = browse.folderAccessLost,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            _browseState.update { it.copy(sortOption = settings.librarySortOption) }
            val savedUri = settings.lastUsedSafTreeUri?.let { Uri.parse(it) }
            if (savedUri != null) {
                if (getApplication<Application>().hasPersistedReadPermission(savedUri)) {
                    openRoot(savedUri)
                } else {
                    _browseState.update { it.copy(folderAccessLost = true) }
                }
            }
        }
        viewModelScope.launch {
            val mostRecent = bookRepository.observeLibrary().first().firstOrNull()
            // Exclude a book whose file was deleted/moved or whose SAF permission was revoked from
            // the candidate — leaving it as a candidate would crash the app when the file can't be
            // opened after tapping "Continue".
            if (mostRecent?.lastOpenedAt != null && bookRepository.bookFileExists(mostRecent)) {
                _resumeCandidate.value = mostRecent
            }
        }
    }

    fun onRootFolderSelected(uri: Uri) {
        getApplication<Application>().takePersistableReadWritePermission(uri)
        viewModelScope.launch {
            val previousUri = settingsRepository.settingsFlow.first().lastUsedSafTreeUri
            settingsRepository.updateLastUsedSafTreeUri(uri.toString())
            // The Dropbox cursor describes how far into the remote change stream this *folder* has
            // already been brought up to date — unlinkDropbox() clears it for the same reason when
            // the account changes. Picking a genuinely different folder (not just re-granting the
            // same one after losing permission, in which case the URI string comes back identical)
            // needs the same treatment: left stale, the next sync runs list_folder/continue against
            // a history the new folder never actually received, silently downloads only whatever
            // changed on Dropbox after that point, and reports nothing else left to do — even though
            // the new folder is still missing everything from before that point.
            val newUriString = uri.toString()
            if (previousUri != null && previousUri != newUriString) {
                settingsRepository.updateDropboxSyncState(cursor = "", lastSyncAtMillis = 0L)
            }
        }
        openRoot(uri)
    }

    private fun openRoot(uri: Uri) {
        val name = folderBrowser.rootDisplayName(uri)
        _browseState.update { it.copy(rootUri = uri, path = listOf(BrowseLocation.Folder(uri, name)), folderAccessLost = false) }
        loadCurrent()
    }

    fun navigateInto(entry: FolderEntry) {
        when (entry) {
            is FolderEntry.Folder -> {
                _browseState.update { it.copy(path = it.path + BrowseLocation.Folder(entry.uri, entry.name)) }
                loadCurrent()
            }
            is FolderEntry.ZipArchive -> {
                _browseState.update { it.copy(path = it.path + BrowseLocation.Zip(entry.uri, entry.name)) }
                loadCurrent()
            }
            is FolderEntry.TextFile -> openTextFile(entry)
        }
    }

    /** Returns true if it moved to the parent folder (the caller should consume the back press), false if already at the top. */
    fun navigateUp(): Boolean {
        val path = _browseState.value.path
        if (path.size <= 1) return false
        _browseState.update { it.copy(path = path.dropLast(1)) }
        loadCurrent()
        return true
    }

    fun navigateToBreadcrumb(index: Int) {
        val path = _browseState.value.path
        if (index < 0 || index >= path.size - 1) return
        _browseState.update { it.copy(path = path.subList(0, index + 1)) }
        loadCurrent()
    }

    fun setSortOption(option: FolderSortOption) {
        _browseState.update { it.copy(sortOption = option) }
        viewModelScope.launch { settingsRepository.updateLibrarySortOption(option) }
    }

    private fun loadCurrent() {
        val location = _browseState.value.path.lastOrNull() ?: return
        viewModelScope.launch {
            _browseState.update { it.copy(isLoading = true) }
            val entries = when (location) {
                is BrowseLocation.Folder -> folderBrowser.listFolder(location.uri)
                is BrowseLocation.Zip -> folderBrowser.listZipEntries(location.uri)
            }
            _browseState.update { it.copy(entries = entries, isLoading = false) }
        }
    }

    private fun openTextFile(entry: FolderEntry.TextFile) {
        viewModelScope.launch {
            // A file inside a zip has no direct path VSCode could open, so it's not a sync-matching target (§3) — leave it blank.
            val relativePath = if (entry.source is BookSource.PlainTxt) {
                val folderNames = _browseState.value.path.drop(1).map { it.name }
                normalizeRelativePath(folderNames + entry.name)
            } else {
                ""
            }
            val id = bookRepository.findOrCreateBook(entry.source, entry.name, entry.sizeBytes, relativePath)
            _openBookEvents.tryEmit(id)
        }
    }

    /**
     * Sends the user to the Dropbox consent page. The result comes back through
     * [DropboxAuthSession] into a different Activity, so this returns as soon as the browser opens;
     * [completeDropboxSignIn] finishes the job.
     */
    fun startDropboxSignIn() {
        if (!DropboxConfig.isConfigured) {
            _dropboxState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.dropbox_not_configured)) }
            return
        }
        _dropboxState.update { it.copy(errorMessage = null, result = null) }
        // Deliberately NOT setting isConnecting here. Backing out of the browser sends no redirect
        // at all, so a spinner started now would never stop and the button would stay disabled for
        // good. It is set in completeDropboxSignIn instead, around a request that actually ends.
        if (!DropboxAuthSession.launch(getApplication())) {
            _dropboxState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.dropbox_no_browser))
            }
        }
    }

    /**
     * Exchanges the authorization code for tokens. A missing refresh token is treated as a failure
     * rather than a partial success: without it the link would work now and silently die in a few
     * hours, which is far harder to diagnose than an immediate error.
     */
    fun completeDropboxSignIn(redirect: DropboxAuthRedirect) {
        DropboxAuthSession.consume()
        if (redirect !is DropboxAuthRedirect.Code) {
            // Cancelled, denied, or the verifier was lost to a process death -- a retry is one tap.
            _dropboxState.update { it.copy(isConnecting = false) }
            return
        }
        _dropboxState.update { it.copy(isConnecting = true) }
        viewModelScope.launch {
            val tokens = DropboxOAuth.exchangeCodeForTokens(
                code = redirect.code,
                codeVerifier = redirect.codeVerifier,
                appKey = DropboxConfig.appKey,
                redirectUri = DropboxConfig.redirectUri,
            )
            val refreshToken = tokens?.refreshToken
            if (tokens == null || refreshToken.isNullOrBlank()) {
                _dropboxState.update {
                    it.copy(isConnecting = false, errorMessage = getApplication<Application>().getString(R.string.dropbox_sign_in_failed))
                }
                return@launch
            }
            dropboxClient.cacheAccessToken(tokens.accessToken, tokens.expiresInSeconds)
            settingsRepository.linkDropbox(refreshToken, dropboxClient.accountEmail().orEmpty())
            _dropboxState.update { it.copy(isConnecting = false) }
            // Signing in is the only pairing step there is, so the secret is picked up right here
            // rather than making the user press a second button whose purpose they cannot guess.
            refreshSharedSecret()
        }
    }

    /**
     * Reads the shared secret from the Dropbox app folder and, if it works against Supabase, stores
     * it as the verified secret — which is what [com.moonkata.flonovel.android.ui.reader.ReaderViewModel]
     * gates position sync on.
     *
     * Verifying rather than just storing matters: the secret is machine-generated and could well be
     * from a different Supabase project or a partition this key cannot reach. Without the check the
     * app would report "connected" and silently sync nothing.
     */
    fun refreshSharedSecret() {
        viewModelScope.launch { fetchAndVerifySharedSecret() }
    }

    private suspend fun fetchAndVerifySharedSecret() {
        val result = secretStore.fetch()
        if (result is SecretResult.Success && !verifySupabaseSecret(result.secret)) {
            _dropboxState.update { it.copy(secretState = SecretResult.Failed) }
            return
        }
        _dropboxState.update { it.copy(secretState = result) }
    }

    /**
     * True when the secret reaches Supabase; on success it is committed together with its verified
     * state, which is the flag position sync actually gates on.
     *
     * The network check is skipped when the fetched secret is already the verified one — that is the
     * common case on every sync, and re-testing it each time would add a round trip that can only
     * confirm what is already known. A *changed* secret (Desktop regenerated it) always gets tested.
     */
    private suspend fun verifySupabaseSecret(secret: String): Boolean {
        val settings = settingsRepository.settingsFlow.first()
        if (secret == settings.supabaseVerifiedSecret && secret == settings.supabaseSharedSecret) return true

        val client = ReadingPositionSyncClient(SupabaseConfig.URL, SupabaseConfig.PUBLISHABLE_KEY, secret)
        val success = client.testConnection()
        if (success) settingsRepository.updateSupabaseSharedSecret(secret, verifiedSecret = secret)
        return success
    }

    /**
     * Also clears the cached secret. It came from that account's app folder, so keeping it would
     * leave the reader syncing positions into a partition the user just disconnected from.
     */
    fun signOutOfDropbox() {
        viewModelScope.launch {
            dropboxClient.forgetAccessToken()
            settingsRepository.unlinkDropbox()
            settingsRepository.updateSupabaseSharedSecret("", verifiedSecret = "")
            _dropboxState.value = DropboxUiState()
        }
    }

    /**
     * The "Sync now" button. Needs a library folder because every downloaded file is written into
     * that SAF tree — there is nowhere to put them otherwise.
     */
    fun syncFromDropbox() {
        val rootUri = _browseState.value.rootUri
        if (rootUri == null) {
            _dropboxState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.dropbox_select_folder_first)) }
            return
        }
        // A folder picked before write permission was persisted here too (see SafUriExt.kt) reads
        // and browses perfectly, but every download/delete this sync attempts would fail — on every
        // file, every single time, no matter how many times "Sync now" is pressed again. Catching it
        // here instead of running the sync anyway turns that into one clear, actionable message
        // instead of a wall of per-file failures that retrying can never fix.
        if (!getApplication<Application>().hasPersistedWritePermission(rootUri)) {
            _dropboxState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.dropbox_write_permission_missing))
            }
            return
        }
        if (_dropboxState.value.isSyncing) return

        viewModelScope.launch {
            _dropboxState.value = DropboxUiState(isSyncing = true)
            // Picks up a secret the Desktop regenerated since last time. It is one small download,
            // and the Supabase check behind it is skipped unless the value actually changed.
            fetchAndVerifySharedSecret()
            val result = dropboxFileSync.sync(rootUri) { progress ->
                _dropboxState.update { it.copy(progress = progress) }
            }
            _dropboxState.update {
                if (result != null) {
                    it.copy(isSyncing = false, progress = null, result = result)
                } else {
                    it.copy(
                        isSyncing = false,
                        progress = null,
                        errorMessage = getApplication<Application>().getString(R.string.dropbox_sync_failed),
                    )
                }
            }
            // The sync may have added or removed files inside the folder on screen, and the folder
            // listing is only read when it is entered — without this, new books stay invisible until
            // the user navigates away and back (the same fix the PC sync needed).
            if (result != null && result.changed > 0) loadCurrent()
        }
    }

    // --- SettingsController implementation — so QuickSettingsSheet can be reused as-is from the
    // library screen too. There's no open book here, so this does nothing beyond persisting values
    // (no side effects like ReaderViewModel's immediate TTS start or resetting navigation history) —
    // ReaderViewModel reads the saved values and applies them itself once a book is actually opened
    // later. ---
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
    override fun setSwipeLeftAction(value: PageGestureAction) = launchSetting { settingsRepository.updateSwipeLeftAction(value) }
    override fun setSwipeRightAction(value: PageGestureAction) = launchSetting { settingsRepository.updateSwipeRightAction(value) }
    override fun setSwipeUpAction(value: PageGestureAction) = launchSetting { settingsRepository.updateSwipeUpAction(value) }
    override fun setSwipeDownAction(value: PageGestureAction) = launchSetting { settingsRepository.updateSwipeDownAction(value) }
    override fun setPageTransitionAnimation(value: PageTransitionAnimation) = launchSetting { settingsRepository.updatePageTransitionAnimation(value) }
    override fun setAutoAdvanceMode(mode: AutoAdvanceMode) = launchSetting { settingsRepository.updateAutoAdvanceMode(mode) }

    override fun toggleChapterPattern(id: String, enabled: Boolean) = launchSetting {
        val current = uiState.value.settings.chapterPatternEnabledIds
        val updated = if (enabled) current + id else current - id
        settingsRepository.updateChapterPatternEnabledIds(updated)
    }

    override fun addCustomChapterPattern(pattern: String): Boolean {
        if (pattern.isBlank() || runCatching { Regex(pattern) }.isFailure) return false
        launchSetting {
            val current = uiState.value.settings.chapterCustomPatterns
            settingsRepository.updateChapterCustomPatterns(current + pattern)
        }
        return true
    }

    override fun removeCustomChapterPattern(pattern: String) = launchSetting {
        val current = uiState.value.settings.chapterCustomPatterns
        settingsRepository.updateChapterCustomPatterns(current - pattern)
    }

    override fun downloadFont(entry: FontCatalogEntry) = fontDownloadManager.download(entry)
    override fun isFontDownloaded(entry: FontCatalogEntry) = fontDownloadManager.isDownloaded(entry)


    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun sortEntries(entries: List<FolderEntry>, option: FolderSortOption): List<FolderEntry> {
        val folders = entries.filterIsInstance<FolderEntry.Folder>().let { folders ->
            if (option == FolderSortOption.NAME_DESC) folders.sortedByDescending { it.name.lowercase() }
            else folders.sortedBy { it.name.lowercase() }
        }
        val files = entries.filterNot { it is FolderEntry.Folder }
        val sortedFiles = when (option) {
            FolderSortOption.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            FolderSortOption.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            FolderSortOption.DATE_DESC -> files.sortedByDescending { lastModifiedOf(it) }
            FolderSortOption.DATE_ASC -> files.sortedBy { lastModifiedOf(it) }
            FolderSortOption.SIZE_DESC -> files.sortedByDescending { sizeOf(it) }
            FolderSortOption.SIZE_ASC -> files.sortedBy { sizeOf(it) }
        }
        return folders + sortedFiles
    }

    private fun lastModifiedOf(entry: FolderEntry): Long = when (entry) {
        is FolderEntry.TextFile -> entry.lastModified
        is FolderEntry.ZipArchive -> entry.lastModified
        is FolderEntry.Folder -> 0L
    }

    private fun sizeOf(entry: FolderEntry): Long = when (entry) {
        is FolderEntry.TextFile -> entry.sizeBytes
        is FolderEntry.ZipArchive -> entry.sizeBytes
        is FolderEntry.Folder -> 0L
    }
}

class LibraryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(application)
        return LibraryViewModel(
            application = application,
            bookRepository = BookRepository(application, db.bookDao()),
            settingsRepository = ReaderSettingsRepository(application),
            folderBrowser = SafFolderBrowser(application),
        ) as T
    }
}
