package com.moonkata.flonovel.android.ui.library

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moonkata.flonovel.android.data.datastore.ReaderSettingsRepository
import com.moonkata.flonovel.android.data.db.AppDatabase
import com.moonkata.flonovel.android.data.repository.BookRepository
import com.moonkata.flonovel.android.testutil.waitUntilTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for a real-device bug found right after the SAF write-permission fix
 * ([com.moonkata.flonovel.android.util.takePersistableReadWritePermission]): picking a *different*
 * home folder (the exact fix that gap calls for) left the Dropbox cursor untouched. The next sync
 * then ran `list_folder/continue` against a change-stream position the new folder never actually
 * reached — silently downloading only whatever changed on Dropbox *after* that point and reporting
 * "nothing left to do," while the new folder was still missing everything from before it. On one
 * real device this looked like "Dropbox only put 2 folders in, and re-syncing finds nothing more"
 * even though the account's `/books` remote had the full library the whole time.
 *
 * [LibraryViewModel.onRootFolderSelected] now resets the cursor whenever the newly picked URI
 * differs from the previously saved one — mirroring [ReaderSettingsRepository.unlinkDropbox]'s
 * existing reasoning for clearing it on sign-out (a cursor describes a position in someone/
 * something's change stream; changing *which* someone/something invalidates it).
 *
 * [onRootFolderSelected]'s own DataStore writes happen inside `viewModelScope.launch`, so each step
 * below waits for `lastUsedSafTreeUri` to actually reflect the pick before moving on — otherwise the
 * second pick could race the first pick's still-pending write and read a stale (null) previous URI.
 */
@RunWith(AndroidJUnit4::class)
class HomeFolderChangeResetsDropboxCursorTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val settingsRepository = ReaderSettingsRepository(application)

    private fun currentLastUsedSafTreeUri(): String? = runBlocking { settingsRepository.settingsFlow.first().lastUsedSafTreeUri }
    private fun currentCursor(): String = runBlocking { settingsRepository.settingsFlow.first().dropboxCursor }

    @After
    fun cleanup() = runBlocking {
        settingsRepository.updateLastUsedSafTreeUri(null)
        settingsRepository.updateDropboxSyncState(cursor = "", lastSyncAtMillis = 0L)
    }

    @Test
    fun pickingADifferentFolder_resetsTheDropboxCursor() {
        val testDb = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        val bookRepository = BookRepository(application, testDb.bookDao())
        val firstRoot = Uri.parse("content://fake/original-synced-folder")
        val secondRoot = Uri.parse("content://fake/freshly-picked-folder")
        val folderBrowser = FakeFolderBrowser(mapOf(firstRoot to emptyList(), secondRoot to emptyList()))

        try {
            val viewModel = LibraryViewModel(application, bookRepository, settingsRepository, folderBrowser)

            viewModel.onRootFolderSelected(firstRoot)
            waitUntilTrue { currentLastUsedSafTreeUri() == firstRoot.toString() }

            // Simulate a folder that has already been fully synced: a non-blank cursor on file.
            runBlocking { settingsRepository.updateDropboxSyncState(cursor = "some-real-cursor-value", lastSyncAtMillis = 123L) }

            // Switching to a different folder must invalidate that history.
            viewModel.onRootFolderSelected(secondRoot)
            waitUntilTrue { currentLastUsedSafTreeUri() == secondRoot.toString() }

            assertEquals(
                "Picking a different home folder must reset the Dropbox cursor, or the next sync " +
                    "will run an incremental delta against a history the new folder never had",
                "",
                currentCursor(),
            )
        } finally {
            testDb.close()
        }
    }

    @Test
    fun rePickingTheSameFolder_leavesTheDropboxCursorAlone() {
        val testDb = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        val bookRepository = BookRepository(application, testDb.bookDao())
        val root = Uri.parse("content://fake/same-folder-both-times")
        val folderBrowser = FakeFolderBrowser(mapOf(root to emptyList()))

        try {
            val viewModel = LibraryViewModel(application, bookRepository, settingsRepository, folderBrowser)

            viewModel.onRootFolderSelected(root)
            waitUntilTrue { currentLastUsedSafTreeUri() == root.toString() }
            runBlocking { settingsRepository.updateDropboxSyncState(cursor = "some-real-cursor-value", lastSyncAtMillis = 123L) }

            // Re-granting the *same* folder — e.g. recovering from the read-only-grant bug this
            // folder-picker flow exists to fix — must not throw away a cursor that is still valid
            // for it, or every affected user would pay for a full re-download they don't need.
            viewModel.onRootFolderSelected(root)
            // Unlike the other test, there's no state change to poll for here — the URI staying the
            // same is exactly what this case is about, so nothing observable flips. A short fixed
            // pause is the pragmatic option: the launch is a settingsFlow read plus one comparison,
            // no IO, so it completes on the main thread's very next iteration in practice.
            Thread.sleep(300)
        } finally {
            testDb.close()
        }
        assertEquals(
            "Re-picking the same folder must not reset a still-valid cursor",
            "some-real-cursor-value",
            currentCursor(),
        )
    }
}
