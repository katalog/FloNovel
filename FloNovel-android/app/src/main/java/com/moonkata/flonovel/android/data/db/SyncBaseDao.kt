package com.moonkata.flonovel.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncBaseDao {
    @Query("SELECT * FROM sync_base")
    suspend fun getAll(): List<SyncBaseEntity>

    @Upsert
    suspend fun upsert(entity: SyncBaseEntity)

    @Query("DELETE FROM sync_base WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    /** Bases describe one folder against one account; a new folder or account starts over. */
    @Query("DELETE FROM sync_base")
    suspend fun deleteAll()
}
