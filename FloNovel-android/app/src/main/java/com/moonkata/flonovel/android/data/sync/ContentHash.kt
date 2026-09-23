package com.moonkata.flonovel.android.data.sync

import java.io.InputStream
import java.security.MessageDigest

/**
 * Dropbox's `content_hash`: SHA-256 of each 4 MiB block, then SHA-256 of those digests
 * concatenated, as lowercase hex.
 *
 * Two-way sync compares file contents with this, never with modification times. Computing it the
 * same way Dropbox does means a local file can be compared against a listing entry without
 * downloading anything. The Desktop app has its own copy; both are pinned by the same test vectors.
 */
object ContentHash {
    const val BLOCK_SIZE = 4 * 1024 * 1024

    fun of(bytes: ByteArray): String = of(bytes.inputStream())

    fun of(input: InputStream): String {
        val overall = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BLOCK_SIZE)
        while (true) {
            val filled = readBlock(input, buffer)
            if (filled == 0) break
            val block = MessageDigest.getInstance("SHA-256")
            block.update(buffer, 0, filled)
            overall.update(block.digest())
            if (filled < BLOCK_SIZE) break
        }
        return overall.digest().joinToString("") { "%02x".format(it) }
    }

    // A single read() may return fewer bytes than asked even mid-file; a short block would hash
    // differently from Dropbox's, so keep reading until the block is full or the stream ends.
    private fun readBlock(input: InputStream, buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val n = input.read(buffer, filled, buffer.size - filled)
            if (n < 0) break
            filled += n
        }
        return filled
    }
}
