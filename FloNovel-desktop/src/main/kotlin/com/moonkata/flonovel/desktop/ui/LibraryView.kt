package com.moonkata.flonovel.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.i18n.stringResource
import com.moonkata.flonovel.desktop.library.BooksData
import com.moonkata.flonovel.desktop.library.FolderContent
import com.moonkata.flonovel.desktop.library.LibraryBookItem
import com.moonkata.flonovel.desktop.library.LibraryFolderEntry
import com.moonkata.flonovel.desktop.library.LibraryScanner
import com.moonkata.flonovel.desktop.library.LibrarySortOption
import com.moonkata.flonovel.desktop.platform.FolderPicker
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.launch

import com.moonkata.flonovel.desktop.preprocess.IntakeFailure
import com.moonkata.flonovel.desktop.sync.InitialUploadProgress
import com.moonkata.flonovel.desktop.sync.SyncFileFailure
import com.moonkata.flonovel.desktop.sync.SyncStatus

data class DisplayItem(
    val type: Int, // 0 = PARENT, 1 = FOLDER, 2 = BOOK
    val folder: LibraryFolderEntry? = null,
    val book: LibraryBookItem? = null,
)

@Composable
fun LibraryView(
    homeFolder: String,
    booksData: BooksData,
    failedFiles: List<IntakeFailure> = emptyList(),
    isDropboxLinked: Boolean = false,
    isInitialUploadRequired: Boolean = false,
    syncStatus: SyncStatus = SyncStatus.IDLE,
    initialProgress: InitialUploadProgress? = null,
    syncCompletedMessage: String? = null,
    syncFailedFiles: List<SyncFileFailure> = emptyList(),
    onHomeFolderChanged: (String) -> Unit,
    onBookSelected: (LibraryBookItem) -> Unit,
    onRetryFailed: ((Path) -> Unit)? = null,
    onRetryAllFailed: (() -> Unit)? = null,
    onStartSync: (() -> Unit)? = null,
    onStartInitialUpload: (() -> Unit)? = null,
    onPauseInitialUpload: (() -> Unit)? = null,
    onResumeInitialUpload: (() -> Unit)? = null,
    onStartDropboxOAuth: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    initialSortOption: LibrarySortOption = LibrarySortOption.RECENT,
    onSortOptionChanged: ((LibrarySortOption) -> Unit)? = null,
    initialRelativePath: String = "",
    onRelativePathChanged: ((String) -> Unit)? = null,
    directoryRevision: Long = 0L,
    isExternalDialogOpen: Boolean = false,
    onRegisterKeyDispatcher: (((KeyEvent) -> Boolean)?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var sortOption by remember(initialSortOption) { mutableStateOf(initialSortOption) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showFailureDialog by remember { mutableStateOf(false) }
    var showDropboxAuthDialog by remember { mutableStateOf(false) }
    var showSyncFailureDialog by remember { mutableStateOf(false) }
    var pendingAuthAfterFolder by remember { mutableStateOf(false) }
    var folderInputText by remember { mutableStateOf(homeFolder) }
    var currentRelativePath by remember(homeFolder) { mutableStateOf(initialRelativePath) }
    var selectedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    fun navigateToFolder(newFolder: String) {
        val root = if (homeFolder.isNotBlank()) runCatching { Path.of(homeFolder) }.getOrNull() else null
        val scanned = if (root != null) {
            runCatching { LibraryScanner.scanDirectory(root, newFolder, booksData) }.getOrNull()
        } else null
        val hasContent = scanned != null && (scanned.subfolders.isNotEmpty() || scanned.books.isNotEmpty())
        val hasParentEntry = newFolder.isNotBlank()
        currentRelativePath = newFolder
        onRelativePathChanged?.invoke(newFolder)
        selectedIndex = if (hasParentEntry && hasContent) 1 else 0
        coroutineScope.launch { listState.scrollToItem(selectedIndex) }
    }

    fun navigateToParent() {
        val oldFolder = currentRelativePath.replace('\\', '/').trim('/')
        val lastSlash = oldFolder.lastIndexOf('/')
        val parentFolder = if (lastSlash >= 0) oldFolder.substring(0, lastSlash) else ""
        val exitingFolder = if (lastSlash >= 0) oldFolder.substring(lastSlash + 1) else oldFolder
        currentRelativePath = parentFolder
        onRelativePathChanged?.invoke(parentFolder)

        val root = if (homeFolder.isNotBlank()) runCatching { Path.of(homeFolder) }.getOrNull() else null
        val parentContent = if (root != null) {
            runCatching { LibraryScanner.scanDirectory(root, parentFolder, booksData) }.getOrNull()
        } else null
        val subfolders = parentContent?.subfolders ?: emptyList()
        val hasParentEntry = parentFolder.isNotBlank()
        val folderIdx = subfolders.indexOfFirst { it.name.equals(exitingFolder, ignoreCase = true) }
        selectedIndex = when {
            folderIdx >= 0 -> if (hasParentEntry) folderIdx + 1 else folderIdx
            hasParentEntry && (subfolders.isNotEmpty() || (parentContent?.books?.isNotEmpty() == true)) -> 1
            else -> 0
        }
        coroutineScope.launch { listState.scrollToItem(selectedIndex) }
    }

    fun navigateToHome() {
        if (currentRelativePath.isNotBlank()) {
            currentRelativePath = ""
            onRelativePathChanged?.invoke("")
            selectedIndex = 0
            coroutineScope.launch { listState.scrollToItem(0) }
        }
    }

    LaunchedEffect(currentRelativePath) {
        onRelativePathChanged?.invoke(currentRelativePath)
    }

    LaunchedEffect(currentRelativePath, homeFolder, directoryRevision) {
        if (currentRelativePath.isNotBlank() && homeFolder.isNotBlank()) {
            val path = runCatching { Path.of(homeFolder).resolve(currentRelativePath) }.getOrNull()
            if (path != null && (!Files.exists(path) || !Files.isDirectory(path))) {
                // If browsed subfolder was deleted on disk, navigate to parent folder
                navigateToParent()
            }
        }
    }

    val folderContent = remember(homeFolder, currentRelativePath, booksData, directoryRevision) {
        if (homeFolder.isNotBlank()) {
            val path = runCatching { Path.of(homeFolder) }.getOrNull()
            if (path != null) {
                LibraryScanner.scanDirectory(path, currentRelativePath, booksData)
            } else FolderContent("", emptyList(), emptyList())
        } else FolderContent("", emptyList(), emptyList())
    }

    val sortedBooks = remember(folderContent.books, sortOption) {
        LibraryScanner.sort(folderContent.books, sortOption)
    }

    val recentItems = remember(homeFolder, booksData, directoryRevision) {
        if (homeFolder.isNotBlank()) {
            val root = runCatching { Path.of(homeFolder) }.getOrNull()
            if (root != null) {
                booksData.books
                    .filter { it.lastOpenedAt != null }
                    .sortedByDescending { it.lastOpenedAt!! }
                    .take(5)
                    .mapNotNull { record ->
                        val file = root.resolve(record.path).toFile()
                        if (file.exists()) {
                            LibraryBookItem(
                                file = file,
                                relativePath = record.path,
                                key = record.key,
                                displayName = record.displayName,
                                sizeBytes = record.sizeBytes,
                                lastModified = file.lastModified(),
                                bookRecord = record,
                            )
                        } else null
                    }
            } else emptyList()
        } else emptyList()
    }

    val displayItems = remember(folderContent.currentRelativePath, folderContent.subfolders, sortedBooks) {
        val list = mutableListOf<DisplayItem>()
        if (folderContent.currentRelativePath.isNotBlank()) {
            list.add(DisplayItem(type = 0))
        }
        for (subfolder in folderContent.subfolders) {
            list.add(DisplayItem(type = 1, folder = subfolder))
        }
        for (book in sortedBooks) {
            list.add(DisplayItem(type = 2, book = book))
        }
        list
    }

    val isAnyDialogOpen = showFolderDialog || showFailureDialog || showDropboxAuthDialog ||
        showSyncFailureDialog || isExternalDialogOpen

    val currentIsAnyDialogOpen by rememberUpdatedState(isAnyDialogOpen)
    val currentDisplayItems by rememberUpdatedState(displayItems)
    val currentPath by rememberUpdatedState(currentRelativePath)

    LaunchedEffect(displayItems) {
        if (displayItems.isNotEmpty() && selectedIndex >= displayItems.size) {
            selectedIndex = (displayItems.size - 1).coerceAtLeast(0)
        }
    }

    // Window-level key event dispatcher registration for Library:
    // Stays continuously registered across directory changes without re-registering and dropping keystrokes.
    DisposableEffect(Unit) {
        val dispatcher: (KeyEvent) -> Boolean = { event ->
            val items = currentDisplayItems
            if (currentIsAnyDialogOpen) {
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    if (showFolderDialog) showFolderDialog = false
                    else if (showFailureDialog) showFailureDialog = false
                    else if (showDropboxAuthDialog) showDropboxAuthDialog = false
                    else if (showSyncFailureDialog) showSyncFailureDialog = false
                    true
                } else {
                    // Let keys pass through to dialog / text inputs
                    false
                }
            } else if (event.type == KeyEventType.KeyDown && items.isNotEmpty()) {
                when (event.key) {
                    Key.DirectionDown -> {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(items.size - 1)
                        coroutineScope.launch { listState.animateScrollToItem(selectedIndex) }
                        true
                    }
                    Key.DirectionUp -> {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        coroutineScope.launch { listState.animateScrollToItem(selectedIndex) }
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        if (selectedIndex in items.indices) {
                            val item = items[selectedIndex]
                            when (item.type) {
                                0 -> navigateToParent()
                                1 -> navigateToFolder(item.folder!!.relativePath)
                                2 -> onBookSelected(item.book!!)
                            }
                        }
                        true
                    }
                    Key.Backspace, Key.Escape -> {
                        if (currentPath.isNotBlank()) {
                            navigateToParent()
                            true
                        } else false
                    }
                    Key.F1 -> {
                        if (currentPath.isNotBlank()) {
                            navigateToHome()
                            true
                        } else false
                    }
                    Key.F4 -> {
                        onOpenSettings?.invoke()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        onRegisterKeyDispatcher(dispatcher)
        onDispose {
            onRegisterKeyDispatcher(null)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141416)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            // Header: Title & Home Folder Path
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource("library_title"),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (homeFolder.isNotBlank()) stringResource("library_home_folder_prefix", homeFolder) else stringResource("library_home_folder_not_set"),
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (homeFolder.isBlank()) {
                                pendingAuthAfterFolder = true
                                folderInputText = homeFolder
                                showFolderDialog = true
                            } else if (!isDropboxLinked) {
                                showDropboxAuthDialog = true
                            } else if (isInitialUploadRequired) {
                                onStartInitialUpload?.invoke()
                            } else {
                                onStartSync?.invoke()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = when {
                                !isDropboxLinked -> Color(0xFF4B5563)
                                syncStatus == SyncStatus.INSUFFICIENT_SPACE -> Color(0xFFDC2626)
                                syncCompletedMessage != null -> Color(0xFF059669)
                                isInitialUploadRequired -> Color(0xFF2563EB)
                                else -> Color(0xFF10B981)
                            },
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = when {
                                !isDropboxLinked -> stringResource("library_sync_button")
                                syncCompletedMessage != null -> syncCompletedMessage
                                isInitialUploadRequired -> stringResource("library_start_initial_sync")
                                syncStatus == SyncStatus.SYNCING -> {
                                    if (initialProgress != null && initialProgress.totalFiles > 0) {
                                        stringResource("library_syncing_progress", initialProgress.processedFiles, initialProgress.totalFiles)
                                    } else {
                                        stringResource("library_syncing")
                                    }
                                }
                                syncStatus == SyncStatus.PAUSED -> stringResource("library_sync_paused")
                                syncStatus == SyncStatus.INSUFFICIENT_SPACE -> stringResource("library_insufficient_space")
                                else -> stringResource("library_sync_button")
                            },
                            fontSize = 13.sp,
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            val chosen = FolderPicker.chooseFolder(homeFolder)
                            if (chosen != null) {
                                onHomeFolderChanged(chosen)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF2563EB),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(stringResource("library_set_home_folder"), fontSize = 13.sp)
                    }

                    if (onOpenSettings != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF374151),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringResource("common_settings"), fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dropbox Initial Upload Banner
            if (isDropboxLinked && isInitialUploadRequired && syncStatus != SyncStatus.SYNCING && syncStatus != SyncStatus.PAUSED) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.6f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource("library_initial_upload_banner_title"),
                                color = Color(0xFF93C5FD),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource("library_initial_upload_banner_desc"),
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { onStartInitialUpload?.invoke() },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF2563EB),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(stringResource("library_initial_upload_start_button"), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Sync In-Progress / Paused Banner
            if ((syncStatus == SyncStatus.SYNCING || syncStatus == SyncStatus.PAUSED) && initialProgress != null && initialProgress.totalFiles > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (syncStatus == SyncStatus.PAUSED) stringResource("library_sync_paused") else stringResource("library_syncing_in_progress"),
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            val mbProcessed = initialProgress.processedBytes / (1024 * 1024)
                            val mbTotal = initialProgress.totalBytes / (1024 * 1024)
                            Text(
                                text = stringResource("library_sync_progress_detail", initialProgress.processedFiles, initialProgress.totalFiles, mbProcessed, mbTotal, initialProgress.currentFileName),
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        if (syncStatus == SyncStatus.SYNCING) {
                            Button(
                                onClick = { onPauseInitialUpload?.invoke() },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF475569), contentColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("library_pause"), fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = { onResumeInitialUpload?.invoke() },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF0284C7), contentColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("library_resume"), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Insufficient Space Alert Banner
            if (syncStatus == SyncStatus.INSUFFICIENT_SPACE || syncFailedFiles.any { it.isInsufficientSpace }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF3B1818),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.8f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("⚠️", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource("library_dropbox_space_shortage_title"),
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource("library_dropbox_space_shortage_desc", syncFailedFiles.size),
                                    color = Color(0xFFF87171),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        Button(
                            onClick = { showSyncFailureDialog = true },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF7F1D1D), contentColor = Color(0xFFFECACA)),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(stringResource("library_failed_files_button"), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Preprocessing Failure Alert Banner
            if (failedFiles.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF3B1818),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(text = "⚠️", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                val hasDiskSpaceError = failedFiles.any { it.isDiskSpaceError }
                                Text(
                                    text = stringResource("library_preprocess_failed_title", failedFiles.size),
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (hasDiskSpaceError) {
                                        stringResource("library_preprocess_failed_disk_space")
                                    } else {
                                        stringResource("library_preprocess_failed_general")
                                    },
                                    color = Color(0xFFF87171),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource("common_details"),
                                color = Color(0xFF93C5FD),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { showFailureDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { onRetryAllFailed?.invoke() },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFFDC2626),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(stringResource("common_retry_all"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Recent Books Section (if any)
            if (recentItems.isNotEmpty()) {
                Text(
                    text = stringResource("library_recent_books"),
                    color = Color(0xFF93C5FD),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 10.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    recentItems.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(88.dp)
                                .clickable { onBookSelected(item) },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF202024),
                            elevation = 2.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = item.displayName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = item.formattedProgress,
                                        color = Color(0xFF60A5FA),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = item.formattedSize,
                                        color = Color(0xFF6B7280),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Folder Breadcrumb Navigation + Sort Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                ) {
                    if (currentRelativePath.isNotBlank()) {
                        Text(
                            text = "←",
                            color = Color(0xFF60A5FA),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { navigateToParent() }
                                .padding(end = 10.dp),
                        )
                    }

                    val homeBreadcrumb = stringResource("library_home_breadcrumb")
                    val segments = if (currentRelativePath.isBlank()) {
                        listOf(homeBreadcrumb to "")
                    } else {
                        val parts = currentRelativePath.replace('\\', '/').split('/').filter { it.isNotBlank() }
                        val list = mutableListOf(homeBreadcrumb to "")
                        var acc = ""
                        for (part in parts) {
                            acc = if (acc.isEmpty()) part else "$acc/$part"
                            list.add(part to acc)
                        }
                        list
                    }

                    segments.forEachIndexed { idx, (label, path) ->
                        if (idx > 0) {
                            Text(" / ", color = Color(0xFF6B7280), fontSize = 14.sp)
                        }
                        val isLast = idx == segments.lastIndex
                        Text(
                            text = label,
                            color = if (isLast) Color.White else Color(0xFF60A5FA),
                            fontSize = 15.sp,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                            modifier = if (!isLast) Modifier.clickable {
                                navigateToFolder(path)
                            } else Modifier,
                        )
                    }
                }

                // Sort Options
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        LibrarySortOption.RECENT to stringResource("library_sort_recent"),
                        LibrarySortOption.NAME to stringResource("library_sort_name"),
                        LibrarySortOption.DATE to stringResource("library_sort_date"),
                        LibrarySortOption.SIZE to stringResource("library_sort_size"),
                    ).forEach { (opt, label) ->
                        val isSelected = sortOption == opt
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) Color(0xFF2563EB) else Color(0xFF252528),
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    sortOption = opt
                                    onSortOptionChanged?.invoke(opt)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF9CA3AF),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color(0xFF2D2D32))
            Spacer(modifier = Modifier.height(8.dp))

            // Folder & Book List
            if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (homeFolder.isBlank()) stringResource("library_no_home_folder_title") else stringResource("library_empty_folder_title"),
                            color = Color(0xFF9CA3AF),
                            fontSize = 15.sp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (homeFolder.isBlank()) stringResource("library_no_home_folder_desc") else stringResource("library_empty_folder_desc"),
                            color = Color(0xFF6B7280),
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(displayItems) { index, item ->
                        val isSelected = index == selectedIndex
                        val itemModifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color.Transparent,
                                RoundedCornerShape(6.dp),
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) Color(0xFF60A5FA).copy(alpha = 0.6f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clickable {
                                selectedIndex = index
                                when (item.type) {
                                    0 -> navigateToParent()
                                    1 -> navigateToFolder(item.folder!!.relativePath)
                                    2 -> onBookSelected(item.book!!)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)

                        when (item.type) {
                            0 -> {
                                // Parent directory
                                Row(
                                    modifier = itemModifier,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("📁", fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
                                    Text(
                                        text = stringResource("library_parent_folder"),
                                        color = if (isSelected) Color(0xFF93C5FD) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            1 -> {
                                // Subfolder
                                val folder = item.folder!!
                                Row(
                                    modifier = itemModifier,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                        Text("📁", fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
                                        Column {
                                            Text(
                                                text = folder.name,
                                                color = if (isSelected) Color(0xFF93C5FD) else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = folder.relativePath,
                                                color = Color(0xFF6B7280),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource("common_folder"),
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            2 -> {
                                // Book
                                val book = item.book!!
                                Row(
                                    modifier = itemModifier,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                        Text("📄", fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
                                        Column {
                                            Text(
                                                text = book.displayName,
                                                color = if (isSelected) Color(0xFF93C5FD) else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = book.relativePath,
                                                color = Color(0xFF6B7280),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = book.formattedSize,
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(end = 18.dp),
                                        )
                                        Text(
                                            text = book.formattedProgress,
                                            color = if (book.bookRecord != null) Color(0xFF60A5FA) else Color(0xFF6B7280),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(44.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Folder Setting Dialog
        if (showFolderDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showFolderDialog = false },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(520.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF232326),
                    elevation = 16.dp,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource("library_folder_dialog_title"),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF18181A), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF444448), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                BasicTextField(
                                    value = folderInputText,
                                    onValueChange = { folderInputText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                    cursorBrush = SolidColor(Color(0xFF60A5FA)),
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    val chosen = FolderPicker.chooseFolder(folderInputText.ifBlank { homeFolder })
                                    if (chosen != null) {
                                        folderInputText = chosen
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF374151),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("library_select_folder_button"), fontSize = 13.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = { showFolderDialog = false },
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
                                onClick = {
                                    val trimmed = folderInputText.trim()
                                    onHomeFolderChanged(trimmed)
                                    showFolderDialog = false
                                    if (pendingAuthAfterFolder) {
                                        pendingAuthAfterFolder = false
                                        if (!isDropboxLinked) {
                                            showDropboxAuthDialog = true
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF2563EB),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("common_save"), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Failure Details Dialog Overlay
        if (showFailureDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showFailureDialog = false },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(560.dp)
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E22),
                    elevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource("library_preprocess_failed_dialog_title", failedFiles.size),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "✕",
                                color = Color(0xFF9CA3AF),
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { showFailureDialog = false }
                                    .padding(4.dp),
                            )
                        }

                        Divider(color = Color(0xFF333338), modifier = Modifier.padding(vertical = 12.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                        ) {
                            items(failedFiles.size) { index ->
                                val failure = failedFiles[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .background(Color(0xFF27272A), RoundedCornerShape(6.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            text = failure.path.fileName.toString(),
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = failure.reason,
                                            color = Color(0xFFEF4444),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onRetryFailed?.invoke(failure.path)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color(0xFF2563EB),
                                            contentColor = Color.White,
                                        ),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(stringResource("common_retry"), fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = { showFailureDialog = false },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF374151),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("common_close"), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    onRetryAllFailed?.invoke()
                                    showFailureDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFFDC2626),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("common_retry_all"), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Dropbox Authentication Dialog Overlay
        if (showDropboxAuthDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showDropboxAuthDialog = false },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(480.dp)
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E22),
                    elevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource("library_dropbox_auth_dialog_title"),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource("library_dropbox_auth_dialog_desc"),
                            color = Color(0xFFD1D5DB),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = { showDropboxAuthDialog = false },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF374151), contentColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("common_cancel"), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    showDropboxAuthDialog = false
                                    onStartDropboxOAuth?.invoke()
                                },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2563EB), contentColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(stringResource("library_dropbox_auth_login_browser"), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Sync Failure Details Dialog Overlay
        if (showSyncFailureDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showSyncFailureDialog = false },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(560.dp)
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E22),
                    elevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource("library_sync_failed_dialog_title", syncFailedFiles.size),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "✕",
                                color = Color(0xFF9CA3AF),
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { showSyncFailureDialog = false }
                                    .padding(4.dp),
                            )
                        }

                        Divider(color = Color(0xFF333338), modifier = Modifier.padding(vertical = 12.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                        ) {
                            items(syncFailedFiles.size) { index ->
                                val failure = syncFailedFiles[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .background(Color(0xFF27272A), RoundedCornerShape(6.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = failure.relativePath,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = failure.error,
                                            color = Color(0xFFEF4444),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = { showSyncFailureDialog = false },
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
    }
}
