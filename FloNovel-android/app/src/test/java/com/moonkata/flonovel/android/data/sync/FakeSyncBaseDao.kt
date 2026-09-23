package com.moonkata.flonovel.android.data.sync

import com.moonkata.flonovel.android.data.db.SyncBaseDao
import com.moonkata.flonovel.android.data.db.SyncBaseEntity

class FakeSyncBaseDao : SyncBaseDao {
    val rows = LinkedHashMap<String, SyncBaseEntity>()

    override suspend fun getAll(): List<SyncBaseEntity> = rows.values.toList()

    override suspend fun getByKey(key: String): SyncBaseEntity? = rows[key]

    override suspend fun upsert(entity: SyncBaseEntity) {
        rows[entity.key] = entity
    }

    override suspend fun deleteByKey(key: String) {
        rows.remove(key)
    }

    override suspend fun deleteAll() {
        rows.clear()
    }
}
