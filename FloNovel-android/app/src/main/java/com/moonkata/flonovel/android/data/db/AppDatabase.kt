package com.moonkata.flonovel.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookEntity::class, SyncBaseEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun syncBaseDao(): SyncBaseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Removes the bookmark feature — drops only the now-unused bookmarks table, leaving the rest of the data (library, reading position) untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS bookmarks")
            }
        }

        /** For reading-position sync (AGENTS.md §1) — adds a column storing the path
         * relative to the sync root. Existing rows are filled with an empty string and get populated
         * naturally the next time that book is tapped in the library (no forced backfill). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN relativePath TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Two-way file sync — adds the per-file base table. Starts empty: with no bases, the first
         * two-way sync adopts every file whose content already matches Dropbox, so nothing is
         * re-downloaded or deleted. The SQL must match Room's generated schema exactly or opening
         * the database throws; SyncBaseMigrationTest (androidTest) checks that. */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_base` (`key` TEXT NOT NULL, `pathDisplay` TEXT NOT NULL, " +
                        "`rev` TEXT NOT NULL, `contentHash` TEXT NOT NULL, `localSize` INTEGER NOT NULL, " +
                        "`localMtime` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`key`))",
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flonovel_database",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
