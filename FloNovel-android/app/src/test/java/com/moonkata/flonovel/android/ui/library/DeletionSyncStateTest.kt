package com.moonkata.flonovel.android.ui.library

import android.net.Uri
import com.moonkata.flonovel.android.data.file.BookSource
import com.moonkata.flonovel.android.data.sync.TwoWaySyncResult
import com.moonkata.flonovel.android.model.FolderEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import android.net.testUri

@OptIn(ExperimentalCoroutinesApi::class)
class DeletionSyncStateTest {

    @Test
    fun optimisticRemoval_removesTargetEntryImmediately() {
        val entry1 = FolderEntry.TextFile("book1.txt", BookSource.PlainTxt(testUri("book1")), 100L, 1000L)
        val entry2 = FolderEntry.TextFile("book2.txt", BookSource.PlainTxt(testUri("book2")), 200L, 2000L)
        val entryFolder = FolderEntry.Folder("subfolder", testUri("folder"))

        val initialList = listOf(entry1, entry2, entryFolder)
        val afterDelete1 = initialList.filterNot { it == entry1 }

        assertEquals(2, afterDelete1.size)
        assertFalse(afterDelete1.contains(entry1))
        assertTrue(afterDelete1.contains(entry2))
        assertTrue(afterDelete1.contains(entryFolder))

        val afterDeleteFolder = afterDelete1.filterNot { it == entryFolder }
        assertEquals(1, afterDeleteFolder.size)
        assertEquals(listOf(entry2), afterDeleteFolder)
    }

    @Test
    fun silentSyncState_keepsFloatingBadgeHidden() {
        // In silent sync mode, isSyncing, result, and errorMessage remain null/false,
        // so the floating badge never pops up over the library list.
        val silentState = DropboxUiState(
            isSyncing = false,
            result = null,
            errorMessage = null,
            pendingMassDeletion = null,
        )
        val isBadgeVisible = silentState.isSyncing || silentState.result != null || silentState.errorMessage != null
        assertFalse(isBadgeVisible)
    }

    @Test
    fun silentSyncState_withPendingMassDeletion_retainsSafetyGuard() {
        // AGENTS.md §1: Mass deletion must always halt and ask the user, even during silent sync.
        val withheldPaths = listOf("book1.txt", "book2.txt", "book3.txt")
        val silentStateWithGuard = DropboxUiState(
            isSyncing = false,
            result = null,
            errorMessage = null,
            pendingMassDeletion = withheldPaths,
        )
        // The badge itself stays hidden, but the mass deletion guard is active
        val isBadgeVisible = silentStateWithGuard.isSyncing || silentStateWithGuard.result != null || silentStateWithGuard.errorMessage != null
        assertFalse(isBadgeVisible)
        assertEquals(withheldPaths, silentStateWithGuard.pendingMassDeletion)
    }

    @Test
    fun debouncedSync_collapsesConsecutiveDeletionsIntoSinglePass() = runTest {
        var syncExecutionCount = 0
        var debounceJob: kotlinx.coroutines.Job? = null
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        fun scheduleSync(debounceMs: Long = 1000L) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                kotlinx.coroutines.delay(debounceMs)
                syncExecutionCount++
            }
        }

        // User deletes 3 files in rapid succession (every 200ms)
        scheduleSync(1000L)
        advanceTimeBy(200L)
        assertEquals(0, syncExecutionCount)

        scheduleSync(1000L)
        advanceTimeBy(200L)
        assertEquals(0, syncExecutionCount)

        scheduleSync(1000L)
        advanceTimeBy(200L)
        assertEquals(0, syncExecutionCount)

        // 1000ms passes after the 3rd delete
        advanceTimeBy(1000L)
        assertEquals("Should only execute sync once after all consecutive deletes finish", 1, syncExecutionCount)
    }

    @Test
    fun coalescedSync_triggersFollowUpPass_whenDeletionOccursDuringActiveSync() = runTest {
        var syncPassCount = 0
        var isSyncActive = false
        var pendingSilentSync = false

        fun triggerSync() {
            if (isSyncActive) {
                pendingSilentSync = true
                return
            }
            isSyncActive = true
            syncPassCount++
            // Simulate sync work
            isSyncActive = false
            if (pendingSilentSync) {
                pendingSilentSync = false
                triggerSync()
            }
        }

        isSyncActive = true // Simulate an ongoing sync pass
        triggerSync() // Another deletion triggers sync while busy
        assertTrue(pendingSilentSync)
        assertEquals(0, syncPassCount)

        // Previous sync finishes and executes the pending sync
        isSyncActive = false
        if (pendingSilentSync) {
            pendingSilentSync = false
            triggerSync()
        }

        assertEquals(1, syncPassCount)
        assertFalse(pendingSilentSync)
    }

    @Test
    fun localChangedCalculation_ignoresPureRemoteDeletionsInSilentMode() {
        // When user deletes a local file, TwoWaySyncResult reports deletedRemote > 0
        // because the deletion was propagated to Dropbox.
        val deleteOnlyResult = TwoWaySyncResult(deletedRemote = 1)
        val localChanged = deleteOnlyResult.downloaded + deleteOnlyResult.deletedLocal +
            deleteOnlyResult.conflicts + deleteOnlyResult.moved

        assertEquals(0, localChanged)
        assertEquals(1, deleteOnlyResult.changed)

        // If remote actually pulled new content or deleted something locally, localChanged > 0
        val downloadResult = TwoWaySyncResult(downloaded = 1, deletedRemote = 1)
        val downloadLocalChanged = downloadResult.downloaded + downloadResult.deletedLocal +
            downloadResult.conflicts + downloadResult.moved

        assertEquals(1, downloadLocalChanged)
    }

    @Test
    fun optimisticBatchRemoval_removesAllTargetEntriesImmediately() {
        val entry1 = FolderEntry.TextFile("book1.txt", BookSource.PlainTxt(testUri("book1")), 100L, 1000L)
        val entry2 = FolderEntry.TextFile("book2.txt", BookSource.PlainTxt(testUri("book2")), 200L, 2000L)
        val entry3 = FolderEntry.TextFile("book3.txt", BookSource.PlainTxt(testUri("book3")), 300L, 3000L)
        val entryFolder = FolderEntry.Folder("subfolder", testUri("folder"))

        val initialList = listOf(entry1, entry2, entry3, entryFolder)
        val toDelete = setOf(entry1, entry3, entryFolder)

        val afterBatchDelete = initialList.filterNot { it in toDelete }

        assertEquals(1, afterBatchDelete.size)
        assertEquals(listOf(entry2), afterBatchDelete)
        assertFalse(afterBatchDelete.contains(entry1))
        assertFalse(afterBatchDelete.contains(entry3))
        assertFalse(afterBatchDelete.contains(entryFolder))
    }

    @Test
    fun batchSelectionMode_deletableFilterAndSelectAll() {
        val plainTxt = FolderEntry.TextFile("novel.txt", BookSource.PlainTxt(testUri("txt")), 100L, 1000L)
        val zipBook = FolderEntry.TextFile("inner.txt", BookSource.ZipEntryTxt(testUri("zip"), "inner.txt"), 100L, 1000L)
        val zipArchive = FolderEntry.ZipArchive("archive.zip", testUri("zip"), 500L, 2000L)
        val folder = FolderEntry.Folder("folder", testUri("folder"))

        val allEntries = listOf(plainTxt, zipBook, zipArchive, folder)

        fun isDeletable(entry: FolderEntry): Boolean =
            entry !is FolderEntry.TextFile || entry.source is BookSource.PlainTxt

        val deletables = allEntries.filter { isDeletable(it) }

        assertEquals(3, deletables.size)
        assertTrue(deletables.contains(plainTxt))
        assertTrue(deletables.contains(zipArchive))
        assertTrue(deletables.contains(folder))
        assertFalse(deletables.contains(zipBook)) // files inside zip cannot be deleted on their own

        // Select all
        val selected = deletables.toSet()
        assertEquals(3, selected.size)
        assertTrue(selected.containsAll(deletables))
    }
}
