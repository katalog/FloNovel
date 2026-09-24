package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.Credentials
import com.moonkata.flonovel.desktop.library.CredentialsStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

@OptIn(ExperimentalPathApi::class)
class SyncStateStoreTest {

    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("sync_state_test")
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val sample = SyncState(
        cursor = "cursor-1",
        bases = mapOf(
            "a/책.txt" to SyncBase("a/책.txt", "A/책.txt", "r1", "h1", 10, 100),
            "b.txt" to SyncBase("b.txt", "B.txt", "r2", "h2", 20, 200, BaseState.DOWNLOADING),
        ),
    )

    @Test
    fun roundTrip_survivesReopen() {
        val file = dir.resolve("sync-state.json")
        SyncStateStore(file).save(sample)
        assertEquals(sample, SyncStateStore(file).load())
    }

    @Test
    fun missingFile_isEmptyState() {
        assertEquals(SyncState(), SyncStateStore(dir.resolve("none.json")).load())
    }

    @Test
    fun corruptedFile_isEmptyState() {
        val file = dir.resolve("sync-state.json")
        Files.writeString(file, "{ not json")
        assertEquals(SyncState(), SyncStateStore(file).load())
    }

    @Test
    fun unknownState_dropsThatBaseOnly() {
        // A future state name must not be read as DOWNLOADING, which could delete the local file.
        val json = """
            {"version":1,"bases":[
              {"key":"a.txt","pathDisplay":"a.txt","rev":"r","contentHash":"h","localSize":1,"localMtime":1,"state":"FROM_THE_FUTURE"},
              {"key":"b.txt","pathDisplay":"b.txt","rev":"r","contentHash":"h","localSize":1,"localMtime":1,"state":"SYNCED"}
            ]}
        """.trimIndent()
        val file = dir.resolve("sync-state.json")
        Files.writeString(file, json)
        val loaded = SyncStateStore(file).load()
        assertEquals(setOf("b.txt"), loaded.bases.keys)
    }

    @Test
    fun blankCursor_readsAsNull() {
        val file = dir.resolve("sync-state.json")
        Files.writeString(file, """{"version":1,"cursor":"","bases":[]}""")
        assertNull(SyncStateStore(file).load().cursor)
    }

    @Test
    fun update_persists() {
        val file = dir.resolve("sync-state.json")
        val store = SyncStateStore(file)
        store.update { it.copy(cursor = "c2") }
        assertEquals("c2", SyncStateStore(file).load().cursor)
    }

    // ── Listing parse: rev and content_hash are read ────────────────────

    @Test
    fun listFolder_readsRevAndContentHash() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {"entries":[
                      {".tag":"file","name":"a.txt","path_display":"/books/a.txt","path_lower":"/books/a.txt",
                       "size":3,"server_modified":"2026-09-23T00:00:00Z","rev":"015f1","content_hash":"abc123"},
                      {".tag":"deleted","name":"b.txt","path_display":"/books/b.txt","path_lower":"/books/b.txt"}
                    ],"cursor":"c","has_more":false}
                    """.trimIndent(),
                ),
            )
            val creds = CredentialsStore(dir.resolve("credentials.json"))
            creds.save(Credentials(dropboxRefreshToken = "t"))
            val client = DropboxClient(
                appKey = "k",
                credentialsStore = creds,
                apiBaseUrl = server.url("").toString().removeSuffix("/"),
                contentBaseUrl = server.url("").toString().removeSuffix("/"),
            )
            client.setAccessToken("token", 3600)

            val result = client.listFolder("/books")
            assertTrue(result is DropboxListFolderResult.Success)
            val file = result.entries.filterIsInstance<DropboxEntry.FileEntry>().single()
            assertEquals("015f1", file.rev)
            assertEquals("abc123", file.contentHash)
        } finally {
            server.shutdown()
        }
    }
}
