package com.moonkata.flonovel.android.data.sync

import java.io.FilterInputStream
import java.io.InputStream
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Expected values were computed outside this codebase (a short Python script implementing
 * Dropbox's published algorithm). The Desktop test suite pins the same constants, so the two
 * apps agree with each other and with Dropbox.
 */
class ContentHashTest {

    private val block = ContentHash.BLOCK_SIZE

    /** byte i = (i * 31 + 7) mod 251 — deterministic, and never aligned to anything. */
    private fun pattern(size: Int) = ByteArray(size) { i -> ((i.toLong() * 31 + 7) % 251).toByte() }

    @Test
    fun empty() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ContentHash.of(ByteArray(0)))
    }

    @Test
    fun oneByte() {
        assertEquals("b6d58dfa6547c1eb7f0d4ffd3e3bd6452213210ea51baa70b97c31f011187215", ContentHash.of(pattern(1)))
    }

    @Test
    fun exactlyOneBlock() {
        assertEquals("c3e676634f2ffa44c48012c2fd13cd98765cb5b9ccc35f3ff7363f3352d74c9f", ContentHash.of(pattern(block)))
    }

    @Test
    fun oneBlockPlusOneByte() {
        assertEquals("b72986f16bd52c8d15f1eb0bb8433433ded7680313e86e903d8ea86b576a084f", ContentHash.of(pattern(block + 1)))
    }

    @Test
    fun tenMegabytes() {
        assertEquals("06a63998988b6a0fb0adf852ef097c9db6edeaf6b8186bcfb19d8e6a53a91e5a", ContentHash.of(pattern(10 * 1024 * 1024)))
    }

    @Test
    fun utf8Text() {
        assertEquals(
            "c95188ea95fc6997ae05f315137c3662aa9f1f57d054f41ec290c8d97a7fddd4",
            ContentHash.of("FloNovel 한글 테스트\n".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun streamThatReturnsShortReads_hashesTheSame() {
        // Real streams may hand back fewer bytes than asked mid-file; blocks must still be 4 MiB.
        val data = pattern(block + 12345)
        val trickle = object : FilterInputStream(data.inputStream()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 1000))
        }
        assertEquals(ContentHash.of(data), ContentHash.of(trickle as InputStream))
    }

    @Test
    fun hashingOutputStream_matchesOneShotHash_andPassesBytesThrough() {
        val data = pattern(block * 2 + 777)
        val sink = java.io.ByteArrayOutputStream()
        val hashing = HashingOutputStream(sink)
        // Uneven chunks, so block boundaries fall inside writes.
        var pos = 0
        while (pos < data.size) {
            val n = minOf(123_457, data.size - pos)
            hashing.write(data, pos, n)
            pos += n
        }
        assertEquals(ContentHash.of(data), hashing.contentHash())
        assertEquals(data.size, sink.size())
    }

    @Test
    fun hashingOutputStream_emptyStream() {
        assertEquals(ContentHash.of(ByteArray(0)), HashingOutputStream(java.io.ByteArrayOutputStream()).contentHash())
    }
}
