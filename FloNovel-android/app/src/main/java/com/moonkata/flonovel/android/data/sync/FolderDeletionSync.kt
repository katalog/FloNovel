package com.moonkata.flonovel.android.data.sync

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.UUID

data class FolderDeletionFile(val path: String, val hash: String)

data class FolderDeletionIntent(val id: String, val folder: String, val files: List<FolderDeletionFile>) {
    fun encode(): String = JSONObject().put("version", 1).put("id", id).put("folder", folder)
        .put("files", JSONArray(files.map { JSONObject().put("path", it.path).put("hash", it.hash) })).toString()

    companion object {
        const val REMOTE_ROOT = "/books/.flonovel/folder-deletions-v1"
        fun key(path: String): String = Normalizer.normalize(path.replace('\\', '/'), Normalizer.Form.NFC).lowercase()
        fun validatePath(path: String) {
            require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path &&
                path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Invalid folder deletion path" }
        }
        fun decode(text: String): FolderDeletionIntent {
            val json = JSONObject(text)
            require(json.getInt("version") == 1) { "Unsupported folder deletion version" }
            val id = json.getString("id")
            require(UUID.fromString(id).toString() == id) { "Invalid folder deletion ID" }
            val folder = json.getString("folder")
            validatePath(folder)
            val array = json.getJSONArray("files")
            val seen = mutableSetOf<String>()
            val files = (0 until array.length()).map { index ->
                val file = array.getJSONObject(index)
                val path = file.getString("path")
                validatePath(path)
                require(key(path).startsWith(key(folder) + "/") && seen.add(key(path))) { "Invalid folder manifest" }
                val hash = file.getString("hash")
                require(hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }) { "Invalid content hash" }
                FolderDeletionFile(path, hash)
            }
            return FolderDeletionIntent(id, folder, files)
        }
    }
}

data class FolderDeletionBatch(
    val outgoing: List<FolderDeletionIntent>,
    val incoming: List<FolderDeletionIntent>,
    val candidates: Map<String, List<FolderDeletionFile>>,
    val deferredFolders: Set<String>,
    val emptyFolders: Set<String>,
    val planningFailures: List<FolderDeletionFailure> = emptyList(),
    val listingFailed: Boolean = false,
) {
    val blockedFolders: Set<String> get() = deferredFolders + planningFailures.map { it.path }.filter { it.isNotBlank() }
    val deletionPaths: List<String> get() = (outgoing.flatMap { it.files.map { file -> file.path }.ifEmpty { listOf(it.folder + "/") } } +
        incoming.filter { it.folder !in blockedFolders }.flatMap { event ->
            candidates.getValue(event.id).map { it.path }.ifEmpty {
                if (event.folder in emptyFolders) listOf(event.folder + "/") else emptyList()
            }
        }).distinctBy(FolderDeletionIntent::key)
}

data class FolderDeletionFailure(val path: String, val reason: String)
data class FolderDeletionResult(val deleted: Int = 0, val failed: List<FolderDeletionFailure> = emptyList(), val deferred: Int = 0)

private class FolderDeletionDeferred : RuntimeException()

/**
 * An explicit folder deletion is different from deleting the last TXT. EPUB and ZIP were left
 * behind on the PC because individual TXT deltas carried no evidence of the user's folder action.
 * Immutable manifests preserve modified/unlisted local files and receipts prevent old event replay.
 */
class FolderDeletionSync(
    private val scope: String,
    private val readState: () -> String?,
    private val writeState: (String) -> Unit,
    private val allFiles: (String) -> List<FolderDeletionFile>,
    private val folderExists: (String) -> Boolean,
    private val deleteFile: (String) -> Boolean,
    private val pruneFolder: (String) -> Boolean,
    private val listRemote: suspend (Set<String>) -> List<FolderDeletionIntent>,
    private val publish: suspend (FolderDeletionIntent) -> Unit,
    private val isInUse: (String) -> Boolean,
    private val stateLock: Any = Any(),
    private val lookupRemoteHash: (suspend (String) -> String?)? = null,
    private val removeRemoteFile: suspend (FolderDeletionFile) -> Unit = {},
    private val canApply: () -> Boolean = { true },
) {
    private var initialized = false
    private val seen = mutableSetOf<String>()
    private val pending = mutableListOf<FolderDeletionIntent>()
    private val ready = mutableSetOf<String>()

    init { synchronized(stateLock) { reload() } }

    private fun reload() {
        initialized = false
        seen.clear()
        pending.clear()
        ready.clear()
        readState()?.let { text ->
            val json = JSONObject(text)
            require(json.getInt("version") == 1) { "Unsupported folder deletion state" }
            if (json.getString("scope") == scope) {
                initialized = json.getBoolean("initialized")
                val receipts = json.getJSONArray("seen")
                for (index in 0 until receipts.length()) seen += receipts.getString(index)
                val queued = json.getJSONArray("pending")
                for (index in 0 until queued.length()) pending += FolderDeletionIntent.decode(queued.getString(index))
                val confirmed = json.optJSONArray("ready") ?: JSONArray()
                for (index in 0 until confirmed.length()) ready += confirmed.getString(index)
            }
        }
    }

    private fun save() {
        writeState(JSONObject().put("version", 1).put("scope", scope).put("initialized", initialized)
            .put("seen", JSONArray(seen.sorted())).put("pending", JSONArray(pending.map { it.encode() }))
            .put("ready", JSONArray(ready.sorted())).toString())
    }

    fun prepare(folder: String): String = synchronized(stateLock) {
        reload()
        FolderDeletionIntent.validatePath(folder)
        require(folderExists(folder)) { "Folder no longer exists" }
        val prefix = FolderDeletionIntent.key(folder) + "/"
        val event = FolderDeletionIntent(UUID.randomUUID().toString(), folder,
            allFiles(folder).filter { FolderDeletionIntent.key(it.path).startsWith(prefix) }.sortedBy { it.path })
        FolderDeletionIntent.decode(event.encode())
        // Persist before the local move/delete; a crash afterwards still leaves the deletion intent.
        pending += event
        save()
        event.id
    }

    fun cancel(id: String) {
        synchronized(stateLock) {
            reload()
            pending.removeAll { it.id == id }
            ready.remove(id)
            save()
        }
    }

    suspend fun plan(remoteHashes: Map<String, String>, hasBaseline: Boolean, newIds: Set<String>? = null): FolderDeletionBatch {
        val receipts = synchronized(stateLock) { reload(); seen.toSet() }
        val remote = try { listRemote(receipts) } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return FolderDeletionBatch(emptyList(), emptyList(), emptyMap(), emptySet(), emptySet(),
                listOf(FolderDeletionFailure(FolderDeletionIntent.REMOTE_ROOT, e.message ?: e.javaClass.simpleName)), listingFailed = true)
        }
        require(remote.map { it.id }.distinct().size == remote.size) { "Duplicate folder deletion IDs" }
        val snapshot = synchronized(stateLock) {
            reload()
            if (!initialized) {
                // A new library must not execute historical tombstones against unrelated local files.
                if (!hasBaseline || newIds != null) seen += remote.filter { !hasBaseline || it.id !in newIds.orEmpty() }.map { it.id }
                initialized = true
                save()
            }
            val outgoing = pending.filter { it.id in ready || !folderExists(it.folder) }
            if (ready.addAll(outgoing.map { it.id })) save()
            val ownIds = pending.map { it.id }.toSet()
            val incoming = remote.filter { it.id !in seen && it.id !in ownIds }
            outgoing to incoming
        }
        val (outgoing, incoming) = snapshot
        val planningFailures = mutableListOf<FolderDeletionFailure>()
        val local = incoming.flatMap { event ->
            try { allFiles(event.folder) } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                planningFailures += FolderDeletionFailure(event.folder, e.message ?: e.javaClass.simpleName)
                emptyList()
            }
        }.distinctBy { FolderDeletionIntent.key(it.path) }
        val byKey = local.associateBy { FolderDeletionIntent.key(it.path) }
        val deferred = incoming.filter { event ->
            local.any { FolderDeletionIntent.key(it.path).startsWith(FolderDeletionIntent.key(event.folder) + "/") && isInUse(it.path) }
        }.map { it.folder }.toSet()
        val candidates = incoming.associate { event ->
            try { event.id to event.files.mapNotNull { expected ->
                val key = FolderDeletionIntent.key(expected.path)
                val file = byKey[key] ?: return@mapNotNull null
                val remoteHash = if (lookupRemoteHash != null) lookupRemoteHash.invoke(expected.path) else remoteHashes[key]
                file.takeIf { it.hash == expected.hash && (remoteHash == null || remoteHash == expected.hash) }
            } } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                planningFailures += FolderDeletionFailure(event.folder, e.message ?: e.javaClass.simpleName)
                event.id to emptyList()
            }
        }
        val emptyFolders = incoming.filter { event ->
            planningFailures.none { it.path == event.folder } && folderExists(event.folder) &&
                local.none { FolderDeletionIntent.key(it.path).startsWith(FolderDeletionIntent.key(event.folder) + "/") }
        }.map { it.folder }.toSet()
        return FolderDeletionBatch(outgoing, incoming, candidates, deferred, emptyFolders, planningFailures)
    }

    suspend fun apply(batch: FolderDeletionBatch, canWrite: Boolean): FolderDeletionResult {
        val failed = mutableListOf<FolderDeletionFailure>()
        failed += batch.planningFailures
        var deleted = 0
        var deferred = batch.deferredFolders.size
        for (event in batch.outgoing) {
            try {
                if (!canApply()) throw FolderDeletionDeferred()
                check(canWrite) { "Folder deletion requires Dropbox write access" }
                for (file in event.files) {
                    if (!canApply()) throw FolderDeletionDeferred()
                    removeRemoteFile(file)
                }
                if (!canApply()) throw FolderDeletionDeferred()
                publish(event)
                synchronized(stateLock) {
                    reload()
                    seen += event.id
                    pending.removeAll { it.id == event.id }
                    ready.remove(event.id)
                    save()
                }
            } catch (_: FolderDeletionDeferred) {
                deferred++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += FolderDeletionFailure(event.folder, e.message ?: e.javaClass.simpleName)
            }
        }
        for (event in batch.incoming) {
            if (event.folder in batch.blockedFolders) continue
            try {
                if (!canApply()) throw FolderDeletionDeferred()
                val existed = folderExists(event.folder)
                val deletedBefore = deleted
                // Rehash at application time: a file edited while confirmation/sync ran must survive.
                val current = allFiles(event.folder).associateBy { FolderDeletionIntent.key(it.path) }
                for (candidate in batch.candidates.getValue(event.id)) {
                    if (!canApply() || current.values.any { isInUse(it.path) }) throw FolderDeletionDeferred()
                    val file = current[FolderDeletionIntent.key(candidate.path)] ?: continue
                    if (file.hash != candidate.hash) continue
                    check(deleteFile(file.path)) { "Folder file removal failed" }
                    deleted++
                }
                if (!canApply() || current.values.any { isInUse(it.path) }) throw FolderDeletionDeferred()
                check(pruneFolder(event.folder)) { "Empty folder removal failed" }
                if (existed && !folderExists(event.folder) && deleted == deletedBefore) deleted++
                synchronized(stateLock) {
                    reload()
                    seen += event.id
                    save()
                }
            } catch (_: FolderDeletionDeferred) {
                deferred++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += FolderDeletionFailure(event.folder, e.message ?: e.javaClass.simpleName)
            }
        }
        return FolderDeletionResult(deleted, failed, deferred)
    }
}
