package com.moonkata.flonovel.android.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens a version-3 database through Room with MIGRATION_3_4. Room validates the migrated schema
 * against the entities on open, so a migration SQL that differs from the generated schema in any
 * column type, nullability or key fails here instead of crashing the app on update.
 *
 * Uses a separate database file, never the app's real `flonovel_database`.
 */
@RunWith(AndroidJUnit4::class)
class SyncBaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "sync-base-migration-test.db"

    @Before
    fun createVersion3() {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            // The version-3 schema exactly as Room generated it.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`documentUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `detectedEncoding` TEXT NOT NULL, " +
                    "`totalCharCount` INTEGER NOT NULL, `lastReadCharOffset` INTEGER NOT NULL, " +
                    "`lastReadProgressPercent` REAL NOT NULL, `fileSizeBytes` INTEGER NOT NULL, " +
                    "`addedAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER, `relativePath` TEXT NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_documentUri` ON `books` (`documentUri`)")
            db.execSQL(
                "INSERT INTO books (documentUri, displayName, detectedEncoding, totalCharCount, lastReadCharOffset, " +
                    "lastReadProgressPercent, fileSizeBytes, addedAt, relativePath) " +
                    "VALUES ('content://x', 'x', 'UTF-8', 100, 42, 0.42, 100, 1, 'x.txt')",
            )
            db.version = 3
        }
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun migrate3To4_keepsBooksAndAddsWorkingSyncBaseTable() = runBlocking {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .build()
        try {
            assertEquals(42, db.bookDao().findByUri("content://x")?.lastReadCharOffset)

            val row = SyncBaseEntity("x.txt", "x.txt", "r1", "h1", 100, 5, "SYNCED")
            db.syncBaseDao().upsert(row)
            db.syncBaseDao().upsert(row.copy(rev = "r2"))
            assertEquals(listOf(row.copy(rev = "r2")), db.syncBaseDao().getAll())

            db.syncBaseDao().deleteByKey("x.txt")
            assertEquals(emptyList<SyncBaseEntity>(), db.syncBaseDao().getAll())
        } finally {
            db.close()
        }
    }
}
