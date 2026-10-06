package com.moonkata.flonovel.desktop.sync

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.util.UUID

class FolderDeletionSyncTest {
    private var state: String? = null
    private val files = linkedMapOf<String, String>()
    private val folders = mutableSetOf("Series")
    private val remote = mutableListOf<FolderDeletionIntent>()
    private val failedDeletes = mutableSetOf<String>()
    private var openPath: String? = null
    private var failUpload = false
    private var paused = false
    private var unreadable = false
    private var failListing = false
    private val lock = Any()

    private fun journal(scope: String = "test-library") = FolderDeletionSync(
        scope, { state }, { state = it },
        { folder -> check(!unreadable) { "Read failed" }; files.filterKeys { it.startsWith("$folder/") }.map { FolderDeletionFile(it.key, ContentHash.of(it.value.toByteArray())) } },
        { it in folders },
        { path -> if (path in failedDeletes) false else files.remove(path) != null },
        { folder -> if (files.keys.none { it.startsWith("$folder/") }) folders.remove(folder); true },
        { check(!failListing) { "Listing failed" }; remote.toList() },
        { event -> check(!failUpload) { "Upload failed" }; if (remote.none { it.id == event.id }) remote += event },
        { it == openPath },
        lock,
        canApply = { !paused },
    )

    private fun event(vararg paths: String) = FolderDeletionIntent(UUID.randomUUID().toString(), "Series",
        paths.map { FolderDeletionFile(it, ContentHash.of(files.getValue(it).toByteArray())) })

    @Test
    fun explicitDeletionRemovesMatchingTxtEpubZipAndNestedFiles() = runBlocking {
        files["Series/book.txt"] = "text"
        files["Series/book.epub"] = "epub"
        files["Series/Sub/archive.zip"] = "zip"
        remote += event(*files.keys.toTypedArray())
        val sync = journal()
        val result = sync.apply(sync.plan(emptyMap(), true), true)
        assertEquals(3, result.deleted)
        assertTrue(files.isEmpty())
        assertFalse("Series" in folders)
    }

    @Test
    fun modifiedAndNewFilesSurviveFolderDeletion() = runBlocking {
        files["Series/book.epub"] = "old"
        files["Series/archive.zip"] = "zip"
        remote += event(*files.keys.toTypedArray())
        files["Series/book.epub"] = "edited"
        files["Series/new.txt"] = "new"
        val sync = journal()
        assertEquals(1, sync.apply(sync.plan(emptyMap(), true), true).deleted)
        assertEquals(setOf("Series/book.epub", "Series/new.txt"), files.keys)
        assertTrue("Series" in folders)
    }

    @Test
    fun newerRemoteContentWinsOverDeletion() = runBlocking {
        files["Series/book.txt"] = "old"
        remote += event("Series/book.txt")
        val sync = journal()
        val batch = sync.plan(mapOf("series/book.txt" to ContentHash.of("remote edit".toByteArray())), true)
        assertEquals(0, sync.apply(batch, true).deleted)
        assertEquals("old", files["Series/book.txt"])
    }

    @Test
    fun fileEditedAfterPlanningSurvives() = runBlocking {
        files["Series/book.epub"] = "old"
        remote += event("Series/book.epub")
        val sync = journal()
        val batch = sync.plan(emptyMap(), true)
        files["Series/book.epub"] = "new"
        assertEquals(0, sync.apply(batch, true).deleted)
        assertEquals("new", files["Series/book.epub"])
    }

    @Test
    fun openBookDefersEntireFolderIncludingNonBooks() = runBlocking {
        files["Series/book.txt"] = "text"
        files["Series/book.epub"] = "epub"
        remote += event(*files.keys.toTypedArray())
        openPath = "Series/book.txt"
        val sync = journal()
        val batch = sync.plan(emptyMap(), true)
        assertEquals(setOf("Series"), batch.deferredFolders)
        assertEquals(1, sync.apply(batch, true).deferred)
        assertEquals(2, files.size)
        openPath = null
        assertEquals(2, sync.apply(sync.plan(emptyMap(), true), true).deleted)
    }

    @Test
    fun failedRemovalIsRetriedWithoutReplayingCompletedFiles() = runBlocking {
        files["Series/a.epub"] = "a"
        files["Series/b.zip"] = "b"
        remote += event(*files.keys.toTypedArray())
        failedDeletes += "Series/b.zip"
        var sync = journal()
        assertEquals(listOf("Series"), sync.apply(sync.plan(emptyMap(), true), true).failed.map { it.path })
        assertEquals(setOf("Series/b.zip"), files.keys)
        failedDeletes.clear()
        sync = journal()
        assertEquals(1, sync.apply(sync.plan(emptyMap(), true), true).deleted)
        assertTrue(files.isEmpty())
    }

    @Test
    fun receiptsPreventReplayAfterFolderIsRecreated() = runBlocking {
        files["Series/book.epub"] = "epub"
        remote += event("Series/book.epub")
        var sync = journal()
        sync.apply(sync.plan(emptyMap(), true), true)
        files["Series/book.epub"] = "epub"
        folders += "Series"
        sync = journal()
        assertTrue(sync.plan(emptyMap(), true).incoming.isEmpty())
        assertEquals("epub", files["Series/book.epub"])
    }

    @Test
    fun newLibraryIgnoresHistoricalDeletionRecords() = runBlocking {
        files["Series/book.epub"] = "epub"
        remote += event("Series/book.epub")
        val sync = journal("new-library")
        assertTrue(sync.plan(emptyMap(), false).incoming.isEmpty())
        assertEquals("epub", files["Series/book.epub"])
    }

    @Test
    fun pendingIntentSurvivesRestartAndPublicationFailure() = runBlocking {
        files["Series/book.epub"] = "epub"
        var sync = journal()
        sync.prepare("Series")
        files.clear()
        folders.clear()
        failUpload = true
        sync = journal()
        assertEquals(listOf("Series"), sync.apply(sync.plan(emptyMap(), true), true).failed.map { it.path })
        assertTrue(remote.isEmpty())
        failUpload = false
        // A remote edit may restore a TXT before the failed manifest publication is retried.
        folders += "Series"
        files["Series/new.txt"] = "new remote content"
        sync = journal()
        assertTrue(sync.apply(sync.plan(emptyMap(), true), true).failed.isEmpty())
        assertEquals(1, remote.size)
        assertTrue(journal().plan(emptyMap(), true).outgoing.isEmpty())
    }

    @Test
    fun cancelAndExistingSourceNeverPublishAnIntent() = runBlocking {
        files["Series/book.txt"] = "text"
        val sync = journal()
        val id = sync.prepare("Series")
        assertTrue(sync.plan(emptyMap(), true).outgoing.isEmpty())
        sync.cancel(id)
        files.clear()
        folders.clear()
        assertTrue(journal().plan(emptyMap(), true).outgoing.isEmpty())
    }

    @Test
    fun emptyFolderDeletionAndNonBookMassDeletionAreCounted() = runBlocking {
        val sync = journal()
        sync.plan(emptyMap(), false)
        remote += FolderDeletionIntent(UUID.randomUUID().toString(), "Series", emptyList())
        assertEquals(listOf("Series/"), sync.plan(emptyMap(), true).deletionPaths)
        sync.apply(sync.plan(emptyMap(), true), true)
        assertFalse("Series" in folders)
        folders += "Series"
        repeat(20) { files["Series/$it.epub"] = "$it" }
        remote += event(*files.keys.toTypedArray())
        assertEquals(20, sync.plan(emptyMap(), true).deletionPaths.size)
    }

    @Test
    fun concurrentJournalInstancesDoNotLoseQueuedIntents() = runBlocking {
        files["Series/book.txt"] = "text"
        val first = journal()
        val second = journal()
        first.plan(emptyMap(), true)
        val id = second.prepare("Series")
        first.plan(emptyMap(), true)
        files.clear()
        folders.clear()
        assertEquals(id, journal().plan(emptyMap(), true).outgoing.single().id)
    }

    @Test
    fun sharedFixturePinsManifestVersionAndAllFileTypes() {
        val fixture = javaClass.getResource("/fixtures/folder-deletion-v1.json")!!.readText()
        val decoded = FolderDeletionIntent.decode(fixture)
        assertEquals("Series", decoded.folder)
        assertEquals(listOf("Series/book.txt", "Series/book.epub", "Series/Sub/archive.zip"), decoded.files.map { it.path })
        assertEquals(decoded, FolderDeletionIntent.decode(decoded.encode()))
    }

    @Test
    fun firstIncrementalSyncOnlyAppliesNewDeletionRecords() = runBlocking {
        files["Series/old.epub"] = "old"
        files["Series/new.zip"] = "new"
        val historical = event("Series/old.epub")
        val recent = event("Series/new.zip")
        remote += historical
        remote += recent
        val sync = journal()
        val batch = sync.plan(emptyMap(), true, setOf(recent.id))
        assertEquals(listOf(recent.id), batch.incoming.map { it.id })
        assertEquals(1, sync.apply(batch, true).deleted)
        assertEquals(setOf("Series/old.epub"), files.keys)
    }

    @Test
    fun pauseAfterPlanningDefersEmptyFolderPruning() = runBlocking {
        remote += FolderDeletionIntent(UUID.randomUUID().toString(), "Series", emptyList())
        val sync = journal()
        val batch = sync.plan(emptyMap(), true)
        paused = true
        assertEquals(1, sync.apply(batch, true).deferred)
        assertTrue("Series" in folders)
        paused = false
        assertEquals(1, sync.apply(sync.plan(emptyMap(), true), true).deleted)
    }

    @Test
    fun openingBookAfterPlanningDefersWholeFolder() = runBlocking {
        files["Series/book.txt"] = "text"
        files["Series/book.epub"] = "epub"
        remote += event(*files.keys.toTypedArray())
        val sync = journal()
        val batch = sync.plan(emptyMap(), true)
        openPath = "Series/book.txt"
        assertEquals(1, sync.apply(batch, true).deferred)
        assertEquals(2, files.size)
    }

    @Test
    fun unreadableFolderReportsCauseAndRemainsRetryable() = runBlocking {
        files["Series/book.epub"] = "epub"
        remote += event("Series/book.epub")
        val sync = journal()
        unreadable = true
        val batch = sync.plan(emptyMap(), true)
        assertEquals(setOf("Series"), batch.blockedFolders)
        assertEquals("Read failed", sync.apply(batch, true).failed.single().reason)
        unreadable = false
        assertEquals(1, sync.apply(sync.plan(emptyMap(), true), true).deleted)
    }

    @Test
    fun failedListingPreservesPendingPublicationUntilRetry() = runBlocking {
        files["Series/book.epub"] = "epub"
        val sync = journal()
        sync.prepare("Series")
        files.clear()
        folders.clear()
        failListing = true
        val batch = sync.plan(emptyMap(), true)
        assertTrue(batch.listingFailed)
        assertEquals("Listing failed", sync.apply(batch, true).failed.single().reason)
        assertTrue(remote.isEmpty())
        failListing = false
        sync.apply(sync.plan(emptyMap(), true), true)
        assertEquals(1, remote.size)
    }

    @Test
    fun pathTraversalIsRejected() {
        var rejected = false
        try { FolderDeletionIntent.validatePath("Series/../Other") } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
