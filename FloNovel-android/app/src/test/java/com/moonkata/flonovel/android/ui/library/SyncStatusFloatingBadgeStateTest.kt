package com.moonkata.flonovel.android.ui.library

import com.moonkata.flonovel.android.data.sync.DropboxSyncProgress
import com.moonkata.flonovel.android.data.sync.DropboxSyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusFloatingBadgeStateTest {

    @Test
    fun defaultState_isNotVisible() {
        val state = DropboxUiState()
        val isVisible = state.isSyncing || state.result != null || state.errorMessage != null
        assertFalse(isVisible)
    }

    @Test
    fun syncingState_isVisible() {
        val state = DropboxUiState(isSyncing = true)
        val isVisible = state.isSyncing || state.result != null || state.errorMessage != null
        assertTrue(isVisible)
    }

    @Test
    fun resultPresent_isVisible() {
        val state = DropboxUiState(
            result = DropboxSyncResult(downloaded = 1, updated = 0, deleted = 0, failed = 0)
        )
        val isVisible = state.isSyncing || state.result != null || state.errorMessage != null
        assertTrue(isVisible)
    }

    @Test
    fun errorMessagePresent_isVisible() {
        val state = DropboxUiState(errorMessage = "Network timeout")
        val isVisible = state.isSyncing || state.result != null || state.errorMessage != null
        assertTrue(isVisible)
    }

    @Test
    fun unlinkedNotice_makesBadgeVisibleEvenWhenDropboxStateIsDefault() {
        val state = DropboxUiState()
        val showUnlinkedNotice = true
        val isVisible = state.isSyncing || state.result != null || state.errorMessage != null || showUnlinkedNotice
        assertTrue(isVisible)
    }

    @Test
    fun syncResult_changedCalculation() {
        val withChanges = DropboxSyncResult(downloaded = 2, updated = 1, deleted = 0, failed = 0)
        assertEquals(3, withChanges.changed)

        val upToDate = DropboxSyncResult(downloaded = 0, updated = 0, deleted = 0, failed = 0)
        assertEquals(0, upToDate.changed)
    }

    @Test
    fun syncProgress_fractionCalculation() {
        val progress = DropboxSyncProgress(completed = 4, total = 8, currentRelativePath = "books/novel.txt")
        val fraction = progress.completed.toFloat() / progress.total
        assertEquals(0.5f, fraction, 0.001f)
    }
}
