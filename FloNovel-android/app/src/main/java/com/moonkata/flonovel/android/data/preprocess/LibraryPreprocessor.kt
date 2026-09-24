package com.moonkata.flonovel.android.data.preprocess

import com.moonkata.flonovel.android.data.file.EncodingDetector
import com.moonkata.flonovel.android.data.sync.LibraryFiles

/**
 * Preprocesses a book file in the library in place, the phone's side of CLAUDE.md §1
 * "전처리 → 등록 → 업로드".
 *
 * SAF cannot replace a file atomically, and the contract forbids overwriting an original
 * non-atomically. So the original is first copied to `.flonovel/original/` (once, like the
 * Desktop), the result is written to a hidden temporary file next to it, the original is deleted
 * and the temporary file renamed into place. The file's document URI changes, which is why this
 * runs before a book is first opened: nothing is keyed by that URI yet.
 *
 * A run cut short leaves the temporary file behind; [recover] finishes or discards it.
 */
class LibraryPreprocessor(private val files: LibraryFiles) {

    sealed class Result {
        data class Unchanged(val relativePath: String) : Result()

        /** [charCount] is the length of the preprocessed text, to clamp a saved reading position. */
        data class Processed(val from: String, val to: String, val charCount: Int) : Result()
        data class Failed(val relativePath: String) : Result()
    }

    suspend fun preprocess(relativePath: String): Result {
        val original = files.openRead(relativePath)?.use { it.readBytes() } ?: return Result.Failed(relativePath)
        val text = TextPreprocessor.normalizeContent(EncodingDetector.decode(original))
        val processed = text.toByteArray(Charsets.UTF_8)

        val folder = relativePath.substringBeforeLast('/', "")
        val name = relativePath.substringAfterLast('/')
        val finalName = finalNameFor(folder, name, relativePath)

        // Already preprocessed: writing identical bytes would only bump the mtime and look like an
        // edit to sync.
        if (finalName == name && processed.contentEquals(original)) return Result.Unchanged(relativePath)

        val backup = "$BACKUP_DIR/$relativePath"
        if (files.stat(backup) == null && !files.write(backup) { it.write(original); true }) return Result.Failed(relativePath)

        val temp = join(folder, temporaryName(name))
        if (!files.write(temp) { it.write(processed); true } || files.stat(temp)?.sizeBytes != processed.size.toLong()) {
            files.delete(temp)
            return Result.Failed(relativePath)
        }
        if (!files.delete(relativePath)) {
            files.delete(temp)
            return Result.Failed(relativePath)
        }
        // From here the original is gone and the temporary file is the book; if the rename fails,
        // recover() retries it on the next run.
        if (!files.rename(temp, finalName)) return Result.Failed(relativePath)
        return Result.Processed(relativePath, join(folder, finalName), text.length)
    }

    /**
     * Finishes runs that were cut short. The temporary file carries the original's name: if the
     * original is still there, the run stopped before it was replaced and simply starts over later;
     * if the original is gone, the temporary file is the book and gets its final name.
     */
    fun recover(): List<Result.Processed> {
        val finished = mutableListOf<Result.Processed>()
        for (file in files.list(includeHidden = true)) {
            val rel = file.relativePath
            if (rel.startsWith("$BACKUP_DIR/")) continue
            val tempName = rel.substringAfterLast('/')
            if (!tempName.startsWith(".") || !tempName.endsWith(TEMP_SUFFIX)) continue
            val folder = rel.substringBeforeLast('/', "")
            val originalName = tempName.removePrefix(".").removeSuffix(TEMP_SUFFIX)
            val originalRel = join(folder, originalName)
            if (files.stat(originalRel) != null) {
                files.delete(rel)
            } else {
                val finalName = finalNameFor(folder, originalName, originalRel)
                if (files.rename(rel, finalName)) {
                    finished += Result.Processed(originalRel, join(folder, finalName), charCount = -1)
                }
            }
        }
        return finished
    }

    /** The cleaned name, with `_1`, `_2`, ... if another file already has it (as on the Desktop). */
    private fun finalNameFor(folder: String, name: String, currentRel: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        val cleaned = TextPreprocessor.cleanFileName(base)
        if (cleaned == base) return name
        fun taken(candidate: String): Boolean {
            val rel = join(folder, candidate)
            return rel != currentRel && files.stat(rel) != null
        }
        if (!taken(cleaned + ext)) return cleaned + ext
        var n = 1
        while (taken("${cleaned}_$n$ext")) n++
        return "${cleaned}_$n$ext"
    }

    private fun temporaryName(originalName: String) = ".$originalName$TEMP_SUFFIX"

    private fun join(folder: String, name: String) = if (folder.isEmpty()) name else "$folder/$name"

    companion object {
        /** The Desktop keeps originals at the same place relative to the library root. */
        const val BACKUP_DIR = ".flonovel/original"

        /** Ends in .txt so a listing of `.txt` files finds leftovers. */
        const val TEMP_SUFFIX = ".flonovel-tmp.txt"
    }
}
