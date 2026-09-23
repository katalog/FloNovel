package com.moonkata.flonovel.android.data.sync

import java.io.InputStream
import java.io.OutputStream
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

/**
 * Passes bytes through to [target] while computing their `content_hash`, so a download can be
 * checked without reading the written file back. SAF has no atomic replace, so the bytes are
 * verified on the way in instead.
 */
class HashingOutputStream(private val target: OutputStream) : OutputStream() {
    private val overall = MessageDigest.getInstance("SHA-256")
    private var block = MessageDigest.getInstance("SHA-256")
    private var inBlock = 0

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        target.write(b, off, len)
        var pos = off
        var remaining = len
        while (remaining > 0) {
            val take = minOf(remaining, ContentHash.BLOCK_SIZE - inBlock)
            block.update(b, pos, take)
            inBlock += take
            pos += take
            remaining -= take
            if (inBlock == ContentHash.BLOCK_SIZE) finishBlock()
        }
    }

    override fun flush() = target.flush()

    /** The hash of everything written so far. Call once, after the last write. */
    fun contentHash(): String {
        if (inBlock > 0) finishBlock()
        return overall.digest().joinToString("") { "%02x".format(it) }
    }

    private fun finishBlock() {
        overall.update(block.digest())
        block = MessageDigest.getInstance("SHA-256")
        inBlock = 0
    }
}
