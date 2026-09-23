package com.moonkata.flonovel.android.data.sync

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject

/**
 * An in-memory Dropbox for sync tests: files with revs and content hashes, a change log behind
 * cursors, and the conditional-write refusals (`add` over an existing file, stale `update` rev,
 * stale `parent_rev`) that two-way sync depends on. Paths are case-insensitive, like Dropbox.
 *
 * Tests change the remote "from another device" with [put], [remove] and [removeFolder].
 */
class FakeDropbox : Dispatcher() {

    private class Stored(val pathDisplay: String, val bytes: ByteArray, val rev: String)

    private sealed class Change {
        data class Written(val lower: String) : Change()
        data class Deleted(val pathDisplay: String) : Change()
    }

    private val files = LinkedHashMap<String, Stored>()
    private val log = mutableListOf<Change>()
    private var revCounter = 0

    /** Every request path received, in order, e.g. "/2/files/upload". */
    val calls = mutableListOf<String>()

    /** Upload `mode` values received, e.g. "add", "update:r3". */
    val uploadModes = mutableListOf<String>()

    /** `parent_rev` values received on delete_v2 (null when absent). */
    val deleteParentRevs = mutableListOf<String?>()

    var booksFolderExists = true

    /** Answers writes the way Dropbox does for a token without `files.content.write`. */
    var tokenLacksWriteScope = false
    var insufficientSpace = false
    var resetNextContinue = false
    val failDownloadsOf = mutableSetOf<String>()
    val corruptDownloadsOf = mutableSetOf<String>()

    /** Runs once, just before the next upload is handled: "another device got there first". */
    var beforeNextUpload: (() -> Unit)? = null

    // ── Test-side remote changes ─────────────────────────────────────────

    fun put(path: String, content: String): String {
        val display = "/books/$path"
        val rev = "r${++revCounter}"
        files[display.lowercase()] = Stored(display, content.toByteArray(Charsets.UTF_8), rev)
        log += Change.Written(display.lowercase())
        return rev
    }

    fun remove(path: String) {
        val display = "/books/$path"
        files.remove(display.lowercase())
        log += Change.Deleted(display)
    }

    /** Like Dropbox: the delta carries one deleted entry for the folder, none for its files. */
    fun removeFolder(path: String) {
        val display = "/books/$path"
        files.keys.removeIf { it.startsWith(display.lowercase() + "/") }
        log += Change.Deleted(display)
    }

    fun content(path: String): String? = files["/books/$path".lowercase()]?.bytes?.toString(Charsets.UTF_8)

    fun rev(path: String): String? = files["/books/$path".lowercase()]?.rev

    fun paths(): Set<String> = files.values.map { it.pathDisplay.removePrefix("/books/") }.toSet()

    // ── HTTP ─────────────────────────────────────────────────────────────

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path ?: ""
        calls += path
        return when (path) {
            "/2/files/list_folder" -> listFolder()
            "/2/files/list_folder/continue" -> listContinue(JSONObject(request.body.readUtf8()).getString("cursor"))
            "/2/files/upload" -> upload(request)
            "/2/files/download" -> download(JSONObject(request.getHeader("Dropbox-API-Arg")!!).getString("path"))
            "/2/files/delete_v2" -> delete(JSONObject(request.body.readUtf8()))
            "/2/files/get_metadata" -> metadata(JSONObject(request.body.readUtf8()).getString("path"))
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun listFolder(): MockResponse {
        if (!booksFolderExists && files.isEmpty()) return error409("path/not_found/")
        val entries = JSONArray()
        files.values.forEach { entries.put(fileJson(it)) }
        return ok(JSONObject().put("entries", entries).put("cursor", "c${log.size}").put("has_more", false))
    }

    private fun listContinue(cursor: String): MockResponse {
        if (resetNextContinue) {
            resetNextContinue = false
            return MockResponse().setResponseCode(409).setBody(
                JSONObject().put("error_summary", "reset/").put("error", JSONObject().put(".tag", "reset")).toString(),
            )
        }
        val from = cursor.removePrefix("c").toInt()
        val entries = JSONArray()
        for (change in log.drop(from)) {
            when (change) {
                is Change.Written -> files[change.lower]?.let { entries.put(fileJson(it)) }
                is Change.Deleted -> entries.put(
                    JSONObject().put(".tag", "deleted").put("name", change.pathDisplay.substringAfterLast('/'))
                        .put("path_display", change.pathDisplay).put("path_lower", change.pathDisplay.lowercase()),
                )
            }
        }
        return ok(JSONObject().put("entries", entries).put("cursor", "c${log.size}").put("has_more", false))
    }

    private fun upload(request: RecordedRequest): MockResponse {
        beforeNextUpload?.let {
            beforeNextUpload = null
            it()
        }
        val arg = JSONObject(request.getHeader("Dropbox-API-Arg")!!)
        val display = arg.getString("path")
        val lower = display.lowercase()
        val bytes = request.body.readByteArray()
        val existing = files[lower]
        val mode = arg.get("mode")
        val modeText = if (mode is JSONObject) "update:${mode.getString("update")}" else mode.toString()
        uploadModes += modeText

        if (tokenLacksWriteScope) return missingScope()
        if (insufficientSpace) return error409("path/insufficient_space/")
        when {
            modeText == "add" && existing != null -> return error409("path/conflict/file/")
            modeText.startsWith("update:") && existing?.rev != modeText.removePrefix("update:") ->
                return error409("path/conflict/file/")
        }
        val stored = Stored(existing?.pathDisplay ?: display, bytes, "r${++revCounter}")
        files[lower] = stored
        log += Change.Written(lower)
        return ok(fileJson(stored))
    }

    private fun download(display: String): MockResponse {
        val stored = files[display.lowercase()] ?: return error409("path/not_found/")
        val rel = stored.pathDisplay.removePrefix("/books/")
        if (rel in failDownloadsOf) return MockResponse().setResponseCode(500)
        val body = if (rel in corruptDownloadsOf) stored.bytes + "garbage".toByteArray() else stored.bytes
        return MockResponse().setResponseCode(200).setBody(okio.Buffer().write(body))
    }

    private fun delete(body: JSONObject): MockResponse {
        val display = body.getString("path")
        val lower = display.lowercase()
        val parentRev = if (body.has("parent_rev")) body.getString("parent_rev") else null
        deleteParentRevs += parentRev
        if (tokenLacksWriteScope) return missingScope()
        val existing = files[lower]
        if (existing == null) {
            val underFolder = files.keys.filter { it.startsWith("$lower/") }
            if (underFolder.isEmpty()) return error409("path_lookup/not_found/")
            underFolder.forEach { files.remove(it) }
            log += Change.Deleted(display)
            return ok(JSONObject().put("metadata", JSONObject().put(".tag", "folder")))
        }
        if (parentRev != null && parentRev != existing.rev) return error409("path_write/conflict/file/")
        files.remove(lower)
        log += Change.Deleted(existing.pathDisplay)
        return ok(JSONObject().put("metadata", fileJson(existing)))
    }

    private fun metadata(display: String): MockResponse {
        val stored = files[display.lowercase()] ?: return error409("path/not_found/")
        return ok(fileJson(stored))
    }

    private fun fileJson(f: Stored) = JSONObject()
        .put(".tag", "file")
        .put("name", f.pathDisplay.substringAfterLast('/'))
        .put("path_display", f.pathDisplay)
        .put("path_lower", f.pathDisplay.lowercase())
        .put("size", f.bytes.size)
        .put("rev", f.rev)
        .put("content_hash", ContentHash.of(f.bytes))
        .put("server_modified", "2026-09-23T00:00:00Z")

    private fun ok(json: JSONObject) = MockResponse().setResponseCode(200).setBody(json.toString())

    private fun missingScope() = MockResponse().setResponseCode(401)
        .setBody(JSONObject().put("error_summary", "missing_scope/").toString())

    private fun error409(summary: String) = MockResponse().setResponseCode(409)
        .setBody(JSONObject().put("error_summary", summary).toString())
}
