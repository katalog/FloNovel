package com.moonkata.flonovel.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.moonkata.flonovel.desktop.i18n.Strings
import com.moonkata.flonovel.desktop.i18n.stringResource
import com.moonkata.flonovel.desktop.library.BookRecord
import com.moonkata.flonovel.desktop.library.BookStore
import com.moonkata.flonovel.desktop.library.CredentialsStore
import com.moonkata.flonovel.desktop.library.LibrarySortOption
import com.moonkata.flonovel.desktop.library.ResumeManager
import com.moonkata.flonovel.desktop.library.ResumeTarget
import com.moonkata.flonovel.desktop.library.SettingsStore
import com.moonkata.flonovel.desktop.library.WindowSettings
import com.moonkata.flonovel.desktop.platform.FileOpener
import com.moonkata.flonovel.desktop.platform.WindowsTitleBar
import com.moonkata.flonovel.desktop.platform.configDir
import com.moonkata.flonovel.desktop.preprocess.IntakeFailure
import com.moonkata.flonovel.desktop.preprocess.IntakePipeline
import com.moonkata.flonovel.desktop.ui.ReaderColors
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import com.moonkata.flonovel.desktop.sync.DropboxClient
import com.moonkata.flonovel.desktop.sync.DropboxConfig
import com.moonkata.flonovel.desktop.sync.DropboxOAuth
import com.moonkata.flonovel.desktop.sync.DropboxSyncEngine
import com.moonkata.flonovel.desktop.sync.ForcePushOutcome
import com.moonkata.flonovel.desktop.sync.InitialUploadProgress
import com.moonkata.flonovel.desktop.sync.ReadingPositionSyncClient
import com.moonkata.flonovel.desktop.sync.ReadingPositionSyncCoordinator
import com.moonkata.flonovel.desktop.sync.RemotePositionNotice
import com.moonkata.flonovel.desktop.sync.SecretManager
import com.moonkata.flonovel.desktop.sync.SupabaseConfig
import com.moonkata.flonovel.desktop.sync.SyncFileFailure
import com.moonkata.flonovel.desktop.sync.SyncStatus
import com.moonkata.flonovel.desktop.text.TextLoader
import com.moonkata.flonovel.desktop.ui.LibraryView
import com.moonkata.flonovel.desktop.ui.ReaderView
import com.moonkata.flonovel.desktop.ui.SettingsDialog
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    System.setProperty("sun.java2d.uiScale", "1.0")

    if (args.contains("--check-config")) {
        println("DROPBOX_APP_KEY=" + DropboxConfig.appKey)
        println("FLONOVEL_DEV=" + DropboxConfig.isDev)
        println("SUPABASE_URL=" + SupabaseConfig.url)
        println("SUPABASE_PUBLISHABLE_KEY=" + SupabaseConfig.publishableKey)
        return
    }
    application {
    val coroutineScope = rememberCoroutineScope()
    val appConfigDir = remember { configDir(DropboxConfig.appConfigDirName) }
    val settingsStore = remember { SettingsStore(appConfigDir.resolve("settings.json")) }
    val bookStore = remember { BookStore(appConfigDir.resolve("books.json")) }
    val credentialsStore = remember { CredentialsStore(appConfigDir.resolve("credentials.json")) }

    val readingSyncCoordinator = remember(credentialsStore) {
        ReadingPositionSyncCoordinator(
            credentialsStore = credentialsStore,
            coroutineScope = coroutineScope,
        )
    }
    var remoteSyncNotice by remember { mutableStateOf<RemotePositionNotice?>(null) }
    LaunchedEffect(readingSyncCoordinator) {
        readingSyncCoordinator.onNoticeChanged = { notice ->
            remoteSyncNotice = notice
        }
    }
    var lastSupabaseTestError by remember { mutableStateOf<String?>(null) }
    var forcePushInProgress by remember { mutableStateOf(false) }
    var forcePushResultMessage by remember { mutableStateOf<String?>(null) }

    var settings by remember {
        val loaded = settingsStore.load()
        Strings.applyLanguage(loaded.language)
        mutableStateOf(loaded)
    }
    val homePath = remember(settings.homeFolder) {
        if (settings.homeFolder.isNotBlank()) runCatching { Path.of(settings.homeFolder) }.getOrNull() else null
    }
    var booksData by remember(homePath) {
        val raw = bookStore.load()
        val initial = if (homePath != null && Files.exists(homePath)) {
            bookStore.sanitizePaths(homePath)
        } else {
            raw
        }
        mutableStateOf(initial)
    }
    var credentials by remember { mutableStateOf(credentialsStore.load()) }
    var failedFilesState by remember { mutableStateOf<List<IntakeFailure>>(emptyList()) }

    val dropboxClient = remember(credentialsStore) {
        DropboxClient(credentialsStore = credentialsStore)
    }
    val secretManager = remember(dropboxClient, credentialsStore) {
        SecretManager(dropboxClient, credentialsStore)
    }

    val syncEngine = remember(homePath, dropboxClient) {
        if (homePath != null && Files.exists(homePath)) {
            DropboxSyncEngine(
                homeFolder = homePath,
                bookStore = bookStore,
                credentialsStore = credentialsStore,
                settingsStore = settingsStore,
                dropboxClient = dropboxClient,
            )
        } else null
    }

    var syncStatus by remember { mutableStateOf(syncEngine?.status ?: SyncStatus.IDLE) }
    var initialProgress by remember { mutableStateOf<InitialUploadProgress?>(null) }
    var syncCompletedMessage by remember { mutableStateOf<String?>(null) }
    var syncToastMessage by remember { mutableStateOf<String?>(null) }
    var autoSyncJob by remember { mutableStateOf<Job?>(null) }
    var syncFailedFiles by remember { mutableStateOf<List<SyncFileFailure>>(emptyList()) }
    var isInitialUploadRequired by remember { mutableStateOf(syncEngine?.isInitialUploadRequired ?: false) }
    var showLibrarySettingsDialog by remember { mutableStateOf(false) }
    var directoryRevision by remember { mutableStateOf(0L) }

    val triggerAutoSync: (String) -> Unit = { _ ->
        if (dropboxClient.isLinked && !isInitialUploadRequired) {
            autoSyncJob?.cancel()
            autoSyncJob = coroutineScope.launch(Dispatchers.IO) {
                delay(1200L) // Debounce batch changes in explorer
                syncStatus = SyncStatus.SYNCING
                val summary = syncEngine?.syncIncremental { prog ->
                    initialProgress = prog
                    syncStatus = syncEngine.status
                }
                syncStatus = syncEngine?.status ?: SyncStatus.IDLE
                initialProgress = null
                syncFailedFiles = syncEngine?.failedFiles ?: emptyList()
                booksData = bookStore.load()
                directoryRevision++

                if (summary != null && (summary.successCount > 0 || summary.deletedCount > 0)) {
                    val countMsg = when {
                        summary.successCount > 0 && summary.deletedCount > 0 ->
                            Strings.get("sync_toast_uploaded_and_deleted", summary.successCount, summary.deletedCount)
                        summary.deletedCount > 0 ->
                            Strings.get("sync_toast_deleted_only", summary.deletedCount)
                        else ->
                            Strings.get("sync_toast_synced_only", summary.successCount)
                    }
                    syncCompletedMessage = countMsg
                    syncToastMessage = Strings.get("sync_toast_prefix", countMsg)
                    delay(3000L)
                    if (syncCompletedMessage == countMsg) syncCompletedMessage = null
                    if (syncToastMessage?.startsWith(Strings.get("sync_toast_title_match")) == true) syncToastMessage = null
                }
            }
        }
    }

    val initialResumeTarget = remember(settings.homeFolder) {
        if (settings.homeFolder.isNotEmpty()) {
            ResumeManager.findResumeTarget(Path.of(settings.homeFolder), settings, booksData)
        } else null
    }

    val initialFolder = remember(initialResumeTarget, homePath) {
        val rawPath = initialResumeTarget?.book?.path ?: ""
        val relPath = if (homePath != null && rawPath.isNotBlank()) {
            val p = runCatching { Path.of(rawPath) }.getOrNull()
            val normHome = homePath.toAbsolutePath().normalize()
            if (p != null && p.isAbsolute && p.startsWith(normHome)) {
                normHome.relativize(p).toString().replace('\\', '/')
            } else {
                rawPath.replace('\\', '/')
            }
        } else {
            rawPath.replace('\\', '/')
        }
        relPath.substringBeforeLast('/', "")
    }
    var currentLibraryFolder by remember { mutableStateOf(initialFolder) }
    // The book the reader is about to hand back from, so the library can land the selection on
    // its row instead of the folder's first row (real-usage feedback). Captured only on the
    // "back to folder view" path, not onHome -- going home already resets the folder itself.
    var lastClosedBookPath by remember { mutableStateOf<String?>(null) }

    var activeTarget by remember { mutableStateOf<ResumeTarget?>(initialResumeTarget) }

    val intakePipeline = remember(homePath) {
        if (homePath != null && Files.exists(homePath)) {
            IntakePipeline(
                homeFolder = homePath,
                bookStore = bookStore,
            ).apply {
                onFileProcessed = { path, record ->
                    booksData = bookStore.load()
                    failedFilesState = failedFiles.toList()
                    directoryRevision++
                    val current = activeTarget
                    if (current != null && current.book.key == record.key) {
                        val loaded = try { TextLoader.load(path) } catch (_: Exception) { null }
                        if (loaded != null) {
                            val safeAnchor = current.clampedAnchor.coerceIn(0, loaded.text.length)
                            activeTarget = current.copy(
                                book = record,
                                loadedText = loaded,
                                clampedAnchor = safeAnchor,
                            )
                        }
                    }
                    triggerAutoSync(record.displayName)
                }
                onFileFailed = { _ ->
                    failedFilesState = failedFiles.toList()
                    directoryRevision++
                }
                onFileDeleted = { path ->
                    booksData = bookStore.load()
                    val current = activeTarget
                    if (current != null) {
                        val currentPath = current.filePath.toAbsolutePath().normalize()
                        val deletedPath = path.toAbsolutePath().normalize()
                        if (currentPath == deletedPath || currentPath.startsWith(deletedPath)) {
                            activeTarget = null
                        }
                    }
                    directoryRevision++
                    triggerAutoSync(path.fileName.toString())
                }
                onDirectoryChanged = {
                    directoryRevision++
                }
                reconcile()
                startWatcher()
            }
        } else null
    }

    LaunchedEffect(dropboxClient.isLinked) {
        if (dropboxClient.isLinked && !isInitialUploadRequired) {
            triggerAutoSync("startup")
        }
    }

    LaunchedEffect(initialResumeTarget) {
        if (initialResumeTarget != null) {
            readingSyncCoordinator.onBookOpened(
                initialResumeTarget.book.key,
                initialResumeTarget.clampedAnchor,
                initialResumeTarget.book.detectedEncoding,
            )
        }
    }

    var dropboxAuthErrorMessage by remember { mutableStateOf<String?>(null) }
    var dropboxManualAuthUrl by remember { mutableStateOf<String?>(null) }
    var dropboxAuthFailureReason by remember { mutableStateOf<String?>(null) }

    val startDropboxAuthAction = {
        if (DropboxConfig.appKey.isBlank()) {
            dropboxAuthErrorMessage = Strings.get("dropbox_auth_no_key")
        } else {
            val verifier = DropboxOAuth.generateCodeVerifier()
            val challenge = DropboxOAuth.generateCodeChallenge(verifier)
            val authFuture = try {
                DropboxOAuth.startLoopbackAuth(
                    appKey = DropboxConfig.appKey,
                    codeChallenge = challenge,
                    onAuthUrlReady = { authUrl, browserOpened, failureReason ->
                        if (!browserOpened) {
                            dropboxManualAuthUrl = authUrl
                            dropboxAuthFailureReason = failureReason
                        }
                    },
                )
            } catch (e: Throwable) {
                val reason = e.message ?: e.javaClass.simpleName
                dropboxAuthErrorMessage = Strings.get("dropbox_auth_loopback_failed", DropboxConfig.REDIRECT_PORT, reason)
                null
            }

            if (authFuture != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val code = authFuture.get()
                        val tokens = DropboxOAuth.exchangeCodeForTokens(code, verifier, DropboxConfig.appKey)
                        credentials = credentialsStore.update {
                            it.copy(
                                dropboxRefreshToken = tokens.refreshToken ?: it.dropboxRefreshToken,
                                dropboxAccountId = tokens.accountId,
                            )
                        }
                        if (tokens.refreshToken != null) {
                            settings = settingsStore.update { s -> s.copy(sync = s.sync.copy(dropboxLinked = true)) }
                        }
                        secretManager.fetchOrInitializeSecret()
                        credentials = credentialsStore.load()
                        isInitialUploadRequired = syncEngine?.isInitialUploadRequired ?: false
                        dropboxManualAuthUrl = null
                        dropboxAuthFailureReason = null
                    } catch (e: Throwable) {
                        dropboxManualAuthUrl = null
                        val msg = e.cause?.message ?: e.message ?: Strings.get("dropbox_unknown_error")
                        dropboxAuthErrorMessage = Strings.get("dropbox_auth_failed_desc", msg)
                    }
                }
            }
        }
    }

    var windowKeyDispatcher by remember { mutableStateOf<((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?>(null) }

    val initialWindowSettings = remember { settings.window }
    val isPosValid = remember(initialWindowSettings) {
        if (initialWindowSettings.x != null && initialWindowSettings.y != null) {
            try {
                val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                ge.screenDevices.any { device ->
                    device.defaultConfiguration.bounds.contains(initialWindowSettings.x + 50, initialWindowSettings.y + 50)
                }
            } catch (_: Throwable) {
                true
            }
        } else {
            false
        }
    }
    val initialPlacement = if (initialWindowSettings.isMaximized) WindowPlacement.Maximized else WindowPlacement.Floating
    val initialPosition = if (isPosValid) {
        WindowPosition(initialWindowSettings.x!!.dp, initialWindowSettings.y!!.dp)
    } else {
        WindowPosition.PlatformDefault
    }
    val initialSize = DpSize(initialWindowSettings.width.dp, initialWindowSettings.height.dp)

    val windowState = rememberWindowState(
        placement = initialPlacement,
        position = initialPosition,
        size = initialSize,
    )

    var lastFloatingPos by remember { mutableStateOf(initialPosition) }
    var lastFloatingSize by remember { mutableStateOf(initialSize) }

    LaunchedEffect(windowState.placement, windowState.position, windowState.size) {
        if (windowState.placement == WindowPlacement.Floating) {
            lastFloatingPos = windowState.position
            lastFloatingSize = windowState.size
        }
    }

    // Shared by the window's own close button (onCloseRequest below) and the library's top-level
    // Esc-to-exit confirmation dialog — both need the exact same cleanup (persist window geometry,
    // flush the sync coordinator, stop the intake pipeline, close the book store) before actually
    // exiting, so there is exactly one place that does it.
    val performExit: () -> Unit = {
        val isMaximized = windowState.placement == WindowPlacement.Maximized
        val pos = if (isMaximized) lastFloatingPos else windowState.position
        val size = if (isMaximized) lastFloatingSize else windowState.size
        val x = if (pos is WindowPosition.Absolute) pos.x.value.toInt() else null
        val y = if (pos is WindowPosition.Absolute) pos.y.value.toInt() else null
        val w = size.width.value.toInt().coerceAtLeast(400)
        val h = size.height.value.toInt().coerceAtLeast(300)
        val updatedWindow = WindowSettings(
            x = x,
            y = y,
            width = w,
            height = h,
            isMaximized = isMaximized,
        )
        settingsStore.save(settingsStore.load().copy(window = updatedWindow))
        readingSyncCoordinator.onBookClosed()
        intakePipeline?.close()
        bookStore.close()
        exitApplication()
    }

    Window(
        onCloseRequest = performExit,
        state = windowState,
        title = if (activeTarget != null) "${activeTarget!!.book.displayName} - FloNovel" else "FloNovel",
        icon = painterResource("icon.png"),
        onPreviewKeyEvent = { keyEvent ->
            if (dropboxAuthErrorMessage != null || dropboxManualAuthUrl != null) {
                if (keyEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && keyEvent.key == androidx.compose.ui.input.key.Key.Escape) {
                    dropboxAuthErrorMessage = null
                    dropboxManualAuthUrl = null
                    dropboxAuthFailureReason = null
                    true
                } else {
                    false
                }
            } else {
                windowKeyDispatcher?.invoke(keyEvent) ?: false
            }
        },
    ) {
        DisposableEffect(window) {
            val focusListener = object : java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {
                    readingSyncCoordinator.onWindowFocusGained()
                }
                override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                    readingSyncCoordinator.onWindowFocusLost()
                }
            }
            window.addWindowFocusListener(focusListener)
            onDispose {
                window.removeWindowFocusListener(focusListener)
            }
        }

        LaunchedEffect(window, settings.view.theme) {
            val themeColors = ReaderColors.forName(settings.view.theme)
            WindowsTitleBar.updateTitleBarColor(
                window = window,
                backgroundColor = themeColors.background,
                textColor = themeColors.text,
            )
            // Re-apply after short delay to ensure native peer binding on initial launch
            delay(50L)
            WindowsTitleBar.updateTitleBarColor(
                window = window,
                backgroundColor = themeColors.background,
                textColor = themeColors.text,
            )
        }

        val baseDensity = LocalDensity.current
        val uiScale = settings.view.uiScale.coerceIn(0.8f, 2.0f)
        val scaledDensity = remember(baseDensity, uiScale) {
            Density(
                density = baseDensity.density * uiScale,
                fontScale = baseDensity.fontScale * uiScale,
            )
        }

        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Box(modifier = Modifier.fillMaxSize()) {
            val currentTarget = activeTarget
            if (currentTarget != null) {
            val navigator = remember(currentTarget.book.key, currentTarget.loadedText) {
                val initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE)
                ReaderNavigator(
                    totalLength = currentTarget.loadedText.text.length,
                    textFitter = { from, _, _ -> from },
                    initialSpec = initialSpec,
                    initialAnchor = currentTarget.clampedAnchor,
                )
            }
            ReaderView(
                fullText = currentTarget.loadedText.text,
                navigator = navigator,
                viewSettings = settings.view,
                remoteSyncNotice = remoteSyncNotice,
                onAcceptRemoteSync = { targetOffset ->
                    navigator.jumpTo(targetOffset)
                    bookStore.updateReadingPosition(
                        bookKey = currentTarget.book.key,
                        anchor = targetOffset,
                        totalCharCount = currentTarget.loadedText.text.length,
                    )
                    readingSyncCoordinator.onAnchorChanged(targetOffset)
                    readingSyncCoordinator.dismissNotice()
                    remoteSyncNotice = null
                },
                onDismissRemoteSync = {
                    readingSyncCoordinator.dismissNotice()
                    remoteSyncNotice = null
                },
                onAnchorChanged = { newAnchor ->
                    readingSyncCoordinator.onAnchorChanged(newAnchor)
                    bookStore.updateReadingPosition(
                        bookKey = currentTarget.book.key,
                        anchor = newAnchor,
                        totalCharCount = currentTarget.loadedText.text.length,
                    )
                },
                onViewSettingsChanged = { newViewSettings ->
                    val newSettings = settings.copy(view = newViewSettings)
                    settings = newSettings
                    settingsStore.save(newSettings)
                },
                keymap = settings.keymap,
                onKeymapChanged = { newKeymap ->
                    val newSettings = settings.copy(keymap = newKeymap)
                    settings = newSettings
                    settingsStore.save(newSettings)
                },
                currentLanguage = settings.language,
                onLanguageChanged = { newLang ->
                    val updated = settings.copy(language = newLang)
                    settings = updated
                    settingsStore.save(updated)
                    Strings.applyLanguage(newLang)
                },
                onBackToLibrary = {
                    readingSyncCoordinator.onBookClosed()
                    bookStore.flush()
                    booksData = bookStore.load()
                    val normPath = currentTarget.book.path.replace('\\', '/')
                    currentLibraryFolder = normPath.substringBeforeLast('/', "")
                    lastClosedBookPath = currentTarget.book.path
                    activeTarget = null
                },
                onOpenInExplorer = { FileOpener.revealInFileManager(currentTarget.filePath) },
                onOpenInDefaultApp = { FileOpener.openWithDefaultApp(currentTarget.filePath) },
                onHome = {
                    readingSyncCoordinator.onBookClosed()
                    bookStore.flush()
                    booksData = bookStore.load()
                    activeTarget = null
                    lastClosedBookPath = null
                    currentLibraryFolder = ""
                },
                onStartDropboxLogin = { startDropboxAuthAction() },
                isDropboxLinked = !credentials.dropboxRefreshToken.isNullOrBlank(),
                cachedSupabaseSecret = credentials.cachedSupabaseSecret,
                isSupabaseConfigured = SupabaseConfig.isConfigured,
                isSupabaseVerified = !credentials.cachedSupabaseSecret.isNullOrBlank() && credentials.cachedSupabaseSecret == credentials.verifiedSupabaseSecret,
                lastSupabaseTestError = lastSupabaseTestError,
                onRegenerateSecret = {
                    coroutineScope.launch(Dispatchers.IO) {
                        secretManager.regenerateSecret()
                        credentials = credentialsStore.load()
                    }
                },
                onTestSupabaseConnection = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val secret = credentialsStore.load().cachedSupabaseSecret ?: ""
                        val client = ReadingPositionSyncClient(
                            baseUrl = SupabaseConfig.url,
                            publishableKey = SupabaseConfig.publishableKey,
                            sharedSecret = secret,
                        )
                        val success = client.testConnection()
                        if (success) {
                            credentials = credentialsStore.update { it.copy(verifiedSupabaseSecret = secret) }
                            lastSupabaseTestError = null
                        } else {
                            lastSupabaseTestError = client.lastTestConnectionError
                        }
                    }
                },
                onForcePushCurrentPosition = {
                    coroutineScope.launch(Dispatchers.IO) {
                        forcePushInProgress = true
                        forcePushResultMessage = null
                        when (val outcome = readingSyncCoordinator.forcePush()) {
                            is ForcePushOutcome.Success ->
                                forcePushResultMessage = Strings.get("settings_supabase_force_push_success", outcome.charOffset)
                            is ForcePushOutcome.Failure ->
                                forcePushResultMessage = Strings.get("settings_supabase_force_push_error", outcome.message)
                            ForcePushOutcome.NoBookOpen ->
                                forcePushResultMessage = Strings.get("settings_supabase_force_push_no_book")
                            ForcePushOutcome.SyncNotAvailable ->
                                forcePushResultMessage = Strings.get("settings_supabase_sync_no_secret")
                        }
                        forcePushInProgress = false
                    }
                },
                forcePushInProgress = forcePushInProgress,
                forcePushResultMessage = forcePushResultMessage,
                onRegisterKeyDispatcher = { windowKeyDispatcher = it },
            )
        } else {
            LibraryView(
                homeFolder = settings.homeFolder,
                booksData = booksData,
                failedFiles = failedFilesState,
                isDropboxLinked = !credentials.dropboxRefreshToken.isNullOrBlank(),
                isInitialUploadRequired = isInitialUploadRequired,
                syncStatus = syncStatus,
                initialProgress = initialProgress,
                syncCompletedMessage = syncCompletedMessage,
                syncFailedFiles = syncFailedFiles,
                initialSortOption = runCatching { LibrarySortOption.valueOf(settings.librarySortOption) }.getOrDefault(LibrarySortOption.RECENT),
                initialRelativePath = currentLibraryFolder,
                onRelativePathChanged = { currentLibraryFolder = it },
                initialSelectedRelativePath = lastClosedBookPath,
                onExitApp = performExit,
                directoryRevision = directoryRevision,
                onSortOptionChanged = { newOption ->
                    val updatedSettings = settings.copy(librarySortOption = newOption.name)
                    settings = updatedSettings
                    settingsStore.save(updatedSettings)
                },
                onHomeFolderChanged = { newFolder ->
                    currentLibraryFolder = ""
                    val updatedSettings = settings.copy(homeFolder = newFolder)
                    settings = updatedSettings
                    settingsStore.save(updatedSettings)
                },
                onRetryFailed = { failedPath ->
                    intakePipeline?.retryFailed(failedPath)
                    failedFilesState = intakePipeline?.failedFiles?.toList() ?: emptyList()
                },
                onRetryAllFailed = {
                    intakePipeline?.retryAllFailed()
                    failedFilesState = intakePipeline?.failedFiles?.toList() ?: emptyList()
                },
                onStartSync = {
                    coroutineScope.launch(Dispatchers.IO) {
                        syncStatus = SyncStatus.SYNCING
                        val summary = syncEngine?.syncIncremental { prog ->
                            initialProgress = prog
                            syncStatus = syncEngine.status
                        }
                        syncStatus = syncEngine?.status ?: SyncStatus.IDLE
                        initialProgress = null
                        syncFailedFiles = syncEngine?.failedFiles ?: emptyList()
                        booksData = bookStore.load()

                        if (summary != null && (summary.successCount > 0 || summary.deletedCount > 0)) {
                            val countMsg = when {
                                summary.successCount > 0 && summary.deletedCount > 0 ->
                                    Strings.get("sync_toast_uploaded_and_deleted", summary.successCount, summary.deletedCount)
                                summary.deletedCount > 0 ->
                                    Strings.get("sync_toast_deleted_only", summary.deletedCount)
                                else ->
                                    Strings.get("sync_toast_synced_only", summary.successCount)
                            }
                            syncCompletedMessage = countMsg
                            delay(3000L)
                            if (syncCompletedMessage == countMsg) syncCompletedMessage = null
                        }
                    }
                },
                onStartInitialUpload = {
                    coroutineScope.launch(Dispatchers.IO) {
                        syncStatus = SyncStatus.SYNCING
                        val summary = syncEngine?.startInitialUpload { prog ->
                            initialProgress = prog
                            syncStatus = syncEngine.status
                        }
                        syncStatus = syncEngine?.status ?: SyncStatus.IDLE
                        initialProgress = null
                        syncFailedFiles = syncEngine?.failedFiles ?: emptyList()
                        isInitialUploadRequired = syncEngine?.isInitialUploadRequired ?: false
                        booksData = bookStore.load()

                        if (summary != null && (summary.successCount > 0 || summary.deletedCount > 0)) {
                            val countMsg = when {
                                summary.successCount > 0 && summary.deletedCount > 0 ->
                                    Strings.get("sync_toast_uploaded_and_deleted", summary.successCount, summary.deletedCount)
                                summary.deletedCount > 0 ->
                                    Strings.get("sync_toast_deleted_only", summary.deletedCount)
                                else ->
                                    Strings.get("sync_toast_synced_only", summary.successCount)
                            }
                            syncCompletedMessage = countMsg
                            delay(3000L)
                            if (syncCompletedMessage == countMsg) syncCompletedMessage = null
                        }
                    }
                },
                onPauseInitialUpload = {
                    syncEngine?.pause()
                    syncStatus = SyncStatus.PAUSED
                },
                onResumeInitialUpload = {
                    coroutineScope.launch(Dispatchers.IO) {
                        syncStatus = SyncStatus.SYNCING
                        syncEngine?.resume()
                        syncStatus = syncEngine?.status ?: SyncStatus.IDLE
                        syncFailedFiles = syncEngine?.failedFiles ?: emptyList()
                        booksData = bookStore.load()
                    }
                },
                onStartDropboxOAuth = {
                    startDropboxAuthAction()
                },
                onOpenSettings = {
                    showLibrarySettingsDialog = true
                },
                isExternalDialogOpen = showLibrarySettingsDialog || dropboxAuthErrorMessage != null || dropboxManualAuthUrl != null,
                onRegisterKeyDispatcher = { windowKeyDispatcher = it },
                onBookSelected = { item ->
                    val bookFolder = item.relativePath.replace('\\', '/').substringBeforeLast('/', "")
                    currentLibraryFolder = bookFolder
                    val path = item.file.toPath()
                    if (Files.exists(path) && Files.isReadable(path)) {
                        val loaded = try { TextLoader.load(path) } catch (_: Exception) { null }
                        if (loaded != null) {
                            val record = item.bookRecord ?: BookRecord(
                                path = item.relativePath,
                                key = item.key,
                                displayName = item.displayName,
                                sizeBytes = item.sizeBytes,
                                totalCharCount = loaded.text.length,
                                detectedEncoding = loaded.charset.name(),
                                anchor = 0,
                                progress = 0.0,
                                addedAt = System.currentTimeMillis(),
                                lastOpenedAt = System.currentTimeMillis(),
                            )
                            val safeAnchor = record.anchor.coerceIn(0, loaded.text.length)
                            bookStore.updateReadingPosition(
                                bookKey = record.key,
                                anchor = safeAnchor,
                                totalCharCount = loaded.text.length,
                            )
                            readingSyncCoordinator.onBookOpened(record.key, safeAnchor, record.detectedEncoding)
                            val updatedSettings = settings.copy(lastOpenedBookKey = record.key)
                            settings = updatedSettings
                            settingsStore.save(updatedSettings)
                            booksData = bookStore.load()
                            activeTarget = ResumeTarget(
                                book = record,
                                filePath = path,
                                loadedText = loaded,
                                clampedAnchor = safeAnchor,
                            )
                        }
                    }
                },
            )

            if (showLibrarySettingsDialog) {
                SettingsDialog(
                    currentSettings = settings.view,
                    onSettingsChanged = { newViewSettings ->
                        val newSettings = settings.copy(view = newViewSettings)
                        settings = newSettings
                        settingsStore.save(newSettings)
                    },
                    currentKeymap = settings.keymap,
                    onKeymapChanged = { newKeymap ->
                        val newSettings = settings.copy(keymap = newKeymap)
                        settings = newSettings
                        settingsStore.save(newSettings)
                    },
                    currentLanguage = settings.language,
                    onLanguageChanged = { newLang ->
                        val updated = settings.copy(language = newLang)
                        settings = updated
                        settingsStore.save(updated)
                        Strings.applyLanguage(newLang)
                    },
                    onDismiss = { showLibrarySettingsDialog = false },
                    isDropboxLinked = !credentials.dropboxRefreshToken.isNullOrBlank(),
                    cachedSupabaseSecret = credentials.cachedSupabaseSecret,
                    isSupabaseConfigured = SupabaseConfig.isConfigured,
                    isSupabaseVerified = !credentials.cachedSupabaseSecret.isNullOrBlank() && credentials.cachedSupabaseSecret == credentials.verifiedSupabaseSecret,
                    lastSupabaseTestError = lastSupabaseTestError,
                    onStartDropboxLogin = {
                        showLibrarySettingsDialog = false
                        startDropboxAuthAction()
                    },
                    onRegenerateSecret = {
                        coroutineScope.launch(Dispatchers.IO) {
                            secretManager.regenerateSecret()
                            credentials = credentialsStore.load()
                        }
                    },
                    onTestSupabaseConnection = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val secret = credentialsStore.load().cachedSupabaseSecret ?: ""
                            val client = ReadingPositionSyncClient(
                                baseUrl = SupabaseConfig.url,
                                publishableKey = SupabaseConfig.publishableKey,
                                sharedSecret = secret,
                            )
                            val success = client.testConnection()
                            if (success) {
                                credentials = credentialsStore.update { it.copy(verifiedSupabaseSecret = secret) }
                                lastSupabaseTestError = null
                            } else {
                                lastSupabaseTestError = client.lastTestConnectionError
                            }
                        }
                    },
                )
            }
        }

        // Global Overlays: displayed whether in Reader or Library mode
        if (dropboxAuthErrorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { dropboxAuthErrorMessage = null },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(440.dp)
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E22),
                    elevation = 16.dp,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource("dropbox_auth_failed_title"),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = dropboxAuthErrorMessage!!,
                            color = Color(0xFFE5E7EB),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = { dropboxAuthErrorMessage = null },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF2563EB),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("common_confirm"), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Dropbox Manual Auth URL Dialog Overlay (when browser fails to open automatically)
        if (dropboxManualAuthUrl != null) {
            var copied by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable {
                        dropboxManualAuthUrl = null
                        dropboxAuthFailureReason = null
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(540.dp)
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E22),
                    elevation = 16.dp,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource("dropbox_manual_auth_title"),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource("dropbox_manual_auth_desc"),
                            color = Color(0xFFD1D5DB),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                        if (!dropboxAuthFailureReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource("dropbox_manual_auth_reason", dropboxAuthFailureReason ?: ""),
                                color = Color(0xFFF87171),
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF111827), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF374151), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                        ) {
                            Text(
                                text = dropboxManualAuthUrl!!,
                                color = Color(0xFF93C5FD),
                                fontSize = 11.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = {
                                    val selection = java.awt.datatransfer.StringSelection(dropboxManualAuthUrl)
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                                    copied = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = if (copied) Color(0xFF10B981) else Color(0xFF2563EB),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(if (copied) stringResource("dropbox_url_copied") else stringResource("dropbox_copy_url"), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    dropboxManualAuthUrl = null
                                    dropboxAuthFailureReason = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF374151),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("common_close"), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Global Floating Sync Toast Notification (appears in Reader or Library mode)
        AnimatedVisibility(
            visible = syncToastMessage != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                elevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = syncToastMessage ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    } // Box
    } // CompositionLocalProvider
}
}
}


