package com.moonkata.flonovel.android.data.sync

import com.moonkata.flonovel.android.data.db.SyncBaseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Storage mapping for the two-way sync base, and the listing fields it is built from. */
class SyncBaseStorageTest {

    @Test
    fun entityRoundTrip_keepsEveryField() {
        val base = SyncBase("a/책.txt", "A/책.txt", "r1", "h1", 10, 100, BaseState.DOWNLOADING)
        assertEquals(base, SyncBaseEntity.from(base).toBase())
    }

    @Test
    fun unknownState_isDroppedNotGuessed() {
        // Reading it as DOWNLOADING would delete the local file whenever the remote copy is gone.
        val entity = SyncBaseEntity("a.txt", "a.txt", "r", "h", 1, 1, state = "FROM_THE_FUTURE")
        assertNull(entity.toBase())
    }

    @Test
    fun listFolder_readsRevAndContentHash() {
        val body = """
            {"entries":[
              {".tag":"file","name":"a.txt","path_display":"/books/a.txt","path_lower":"/books/a.txt",
               "size":3,"rev":"015f1","content_hash":"abc123"}
            ],"cursor":"c","has_more":false}
        """.trimIndent()
        val file = (parseListFolderBody(body) as DropboxListResult.Success).entries.single() as DropboxEntry.File
        assertEquals("015f1", file.rev)
        assertEquals("abc123", file.contentHash)
    }

    @Test
    fun listFolder_missingRevAndHash_areBlank() {
        val body = """{"entries":[{".tag":"file","path_display":"/books/a.txt","path_lower":"/books/a.txt","size":3}],"cursor":"c","has_more":false}"""
        val file = (parseListFolderBody(body) as DropboxListResult.Success).entries.single() as DropboxEntry.File
        assertEquals("", file.rev)
        assertEquals("", file.contentHash)
    }
}
