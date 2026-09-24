package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.AtomicFile
import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONArray
import org.json.JSONObject

/**
 * The base of every synced file plus the Dropbox cursor, in one file.
 *
 * They live together so they are written together: a cursor saved ahead of the bases it covers
 * would make the next sync skip changes that were never applied (CLAUDE.md §1, cursor rule).
 */
data class SyncState(
    val cursor: String? = null,
    val bases: Map<String, SyncBase> = emptyMap(),
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("version", VERSION)
        if (cursor != null) obj.put("cursor", cursor)
        val arr = JSONArray()
        for (base in bases.values.sortedBy { it.key }) {
            arr.put(
                JSONObject()
                    .put("key", base.key)
                    .put("pathDisplay", base.pathDisplay)
                    .put("rev", base.rev)
                    .put("contentHash", base.contentHash)
                    .put("localSize", base.localSize)
                    .put("localMtime", base.localMtime)
                    .put("state", base.state.name),
            )
        }
        obj.put("bases", arr)
        return obj.toString(2)
    }

    companion object {
        const val VERSION = 1

        fun fromJsonString(json: String): SyncState {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("bases") ?: JSONArray()
            val bases = LinkedHashMap<String, SyncBase>()
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                // An entry this version cannot read is dropped, not guessed at. No base is the
                // safe default (adopt or conflict-copy, never delete); guessing DOWNLOADING would
                // delete the local file whenever the remote copy is gone.
                val state = runCatching { BaseState.valueOf(b.getString("state")) }.getOrNull() ?: continue
                val base = SyncBase(
                    key = b.getString("key"),
                    pathDisplay = b.getString("pathDisplay"),
                    rev = b.getString("rev"),
                    contentHash = b.getString("contentHash"),
                    localSize = b.getLong("localSize"),
                    localMtime = b.getLong("localMtime"),
                    state = state,
                )
                bases[base.key] = base
            }
            return SyncState(
                cursor = obj.optString("cursor", "").ifBlank { null },
                bases = bases,
            )
        }
    }
}

class SyncStateStore(val filePath: Path) {
    private val lock = Any()
    private var cached: SyncState = loadInitial()

    /**
     * A corrupted file starts from an empty state rather than failing sync forever. That is safe:
     * with no bases every file counts as added on both sides, identical ones are adopted by hash
     * and differing ones become conflict copies, so nothing is deleted.
     */
    private fun loadInitial(): SyncState {
        val content = AtomicFile.readIfExists(filePath) ?: return SyncState()
        return runCatching { SyncState.fromJsonString(content) }.getOrDefault(SyncState())
    }

    /**
     * False only before the first two-way sync has saved anything. That moment, and not a
     * corrupted file, is what marks an upgrade from one-way sync (see DropboxSyncEngine).
     */
    val fileExists: Boolean
        get() = Files.exists(filePath)

    fun load(): SyncState = synchronized(lock) { cached }

    fun save(state: SyncState) {
        synchronized(lock) {
            AtomicFile.writeAtomic(filePath, state.toJsonString())
            cached = state
        }
    }

    fun update(transform: (SyncState) -> SyncState): SyncState = synchronized(lock) {
        transform(cached).also { save(it) }
    }
}
