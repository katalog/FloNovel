package com.moonkata.flonovel.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moonkata.flonovel.android.data.sync.BaseState
import com.moonkata.flonovel.android.data.sync.SyncBase

/**
 * One row per synced file: the last state this phone and Dropbox agreed on (two-way sync base).
 * [key] is the normalized relative path (separators, NFC, lowercase), the same key both apps use.
 */
@Entity(tableName = "sync_base")
data class SyncBaseEntity(
    @PrimaryKey val key: String,
    val pathDisplay: String,
    val rev: String,
    val contentHash: String,
    val localSize: Long,
    val localMtime: Long,
    /** A [BaseState] name, stored as text so an unknown value can be recognised and dropped. */
    val state: String,
) {
    /**
     * Null for a state this version does not know. No base is the safe reading (adopt or
     * conflict-copy, never delete); guessing DOWNLOADING would delete the local file whenever the
     * remote copy is gone.
     */
    fun toBase(): SyncBase? {
        val parsed = BaseState.entries.firstOrNull { it.name == state } ?: return null
        return SyncBase(key, pathDisplay, rev, contentHash, localSize, localMtime, parsed)
    }

    companion object {
        fun from(base: SyncBase) = SyncBaseEntity(
            key = base.key,
            pathDisplay = base.pathDisplay,
            rev = base.rev,
            contentHash = base.contentHash,
            localSize = base.localSize,
            localMtime = base.localMtime,
            state = base.state.name,
        )
    }
}
