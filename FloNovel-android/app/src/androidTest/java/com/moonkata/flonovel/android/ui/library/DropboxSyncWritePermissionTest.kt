package com.moonkata.flonovel.android.ui.library

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moonkata.flonovel.android.R
import com.moonkata.flonovel.android.data.datastore.ReaderSettingsRepository
import com.moonkata.flonovel.android.data.db.AppDatabase
import com.moonkata.flonovel.android.data.repository.BookRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for a real-device bug: [com.moonkata.flonovel.android.util.takePersistableReadWritePermission]
 * used to persist read only. Browsing/opening books kept working forever after that (they only need
 * read), so the gap went unnoticed until a *second* device tried Dropbox sync after a cold start —
 * every download and delete failed silently ("N failed. Try syncing again."), and no amount of
 * retrying could ever fix it, because retrying doesn't restore the missing write grant.
 *
 * [LibraryViewModel.syncFromDropbox] now checks [com.moonkata.flonovel.android.util.hasPersistedWritePermission]
 * before attempting anything, so this exact situation gets one clear, actionable message instead.
 *
 * A fake `content://` URI never has a real OS-level grant of any kind (read or write) — the same
 * limitation [LibraryHomeFolderAccessTest] documents for the read-permission case, since a real
 * grant can only come from an actual SAF picker round trip. That makes every fake URI here behave
 * exactly like the real bug's read-only grant *for this check specifically*: present enough to open
 * and browse (the test drives that through [FolderBrowser][com.moonkata.flonovel.android.data.file.FolderBrowser]
 * directly, bypassing real SAF), but never write-persisted. The "actually has write access" path is
 * exercised by manual real-device verification, same as the read-only counterpart.
 */
@RunWith(AndroidJUnit4::class)
class DropboxSyncWritePermissionTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val settingsRepository = ReaderSettingsRepository(application)

    @After
    fun cleanup() = runBlocking {
        settingsRepository.updateLastUsedSafTreeUri(null)
    }

    @Test
    fun syncWithoutPersistedWritePermission_reportsClearMessage_withoutAttemptingAnyWrites() {
        val testDb = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        val bookRepository = BookRepository(application, testDb.bookDao())
        val fakeRoot = Uri.parse("content://fake/write-permission-missing-root")
        val folderBrowser = FakeFolderBrowser(mapOf(fakeRoot to emptyList()))

        try {
            val viewModel = LibraryViewModel(application, bookRepository, settingsRepository, folderBrowser)

            // A folder pick still succeeds and the library opens fine — this check must never block
            // browsing/reading, only the sync attempt that would otherwise fail file-by-file.
            viewModel.onRootFolderSelected(fakeRoot)

            viewModel.syncFromDropbox()

            assertEquals(
                "Must show the specific 're-pick the folder' message, not the generic per-file failure text",
                application.getString(R.string.dropbox_write_permission_missing),
                viewModel.dropboxState.value.errorMessage,
            )
            assertFalse(
                "Must not enter the syncing state at all — there is nothing retrying could fix",
                viewModel.dropboxState.value.isSyncing,
            )
            assertNull(
                "Must not produce a per-file result (e.g. 'N failed') for a sync that never actually ran",
                viewModel.dropboxState.value.result,
            )
        } finally {
            testDb.close()
        }
    }

    @Test
    fun syncWithNoFolderSelectedAtAll_stillReportsTheOriginalMessage_notTheWritePermissionOne() {
        val testDb = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        val bookRepository = BookRepository(application, testDb.bookDao())
        val folderBrowser = FakeFolderBrowser(emptyMap())

        try {
            val viewModel = LibraryViewModel(application, bookRepository, settingsRepository, folderBrowser)

            viewModel.syncFromDropbox()

            assertEquals(
                "The write-permission check must only run once a folder is actually open",
                application.getString(R.string.dropbox_select_folder_first),
                viewModel.dropboxState.value.errorMessage,
            )
        } finally {
            testDb.close()
        }
    }
}
