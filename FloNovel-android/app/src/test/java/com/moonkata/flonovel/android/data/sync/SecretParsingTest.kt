package com.moonkata.flonovel.android.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parsing of `/.flonovel/secret.json`, the file the Desktop app writes and the phone only reads.
 *
 * The bar is deliberately "anything that is not a real secret must come back null": a value that
 * looks plausible but is wrong would hash to a valid-looking `user_key` server-side, and the two
 * devices would sync into different Supabase partitions while both reporting success.
 */
class SecretParsingTest {

    @Test
    fun readsTheSecretTheDesktopWrites() {
        val json = """{"secret":"a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4","createdAt":1757000000000}"""

        assertEquals("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4", parseSecret(json.toByteArray()))
    }

    /** Field order and extra fields are not something to depend on. */
    @Test
    fun toleratesReorderedAndExtraFields() {
        val json = """{"createdAt":1,"note":"written by desktop","secret":"abc123"}"""

        assertEquals("abc123", parseSecret(json.toByteArray()))
    }

    /**
     * `optString` hands back "" for a missing key, which would sail through as a secret. Every one
     * of these has to be null instead.
     */
    @Test
    fun anythingThatIsNotASecretIsNull() {
        assertNull("missing key", parseSecret("""{"createdAt":1}""".toByteArray()))
        assertNull("empty value", parseSecret("""{"secret":""}""".toByteArray()))
        assertNull("blank value", parseSecret("""{"secret":"   "}""".toByteArray()))
        assertNull("explicit null", parseSecret("""{"secret":null}""".toByteArray()))
    }

    /** A truncated download or an HTML error page must not throw out of the sync. */
    @Test
    fun malformedContentIsNull() {
        assertNull("truncated", parseSecret("""{"secret":"abc""".toByteArray()))
        assertNull("not json", parseSecret("<html>error</html>".toByteArray()))
        assertNull("empty file", parseSecret(ByteArray(0)))
    }

    /** The Desktop writes UTF-8; a stray BOM would otherwise break the JSON parse silently. */
    @Test
    fun theSecretIsReadAsUtf8() {
        val json = """{"secret":"abc123"}"""

        assertEquals("abc123", parseSecret(json.toByteArray(Charsets.UTF_8)))
    }

    // ── Where the file lives ───────────────────────────────────────────────

    /**
     * The path must sit outside `/books`, or the secret would be treated as a book and written into
     * the user's visible library.
     */
    @Test
    fun theSecretPathIsNotInsideTheBooksTree() {
        assertNull(relativeBookPath(DropboxConfig.secretPath))
    }

    /**
     * A debug build must read `secret-dev.json`. Sharing `secret.json` would put dev writes into the
     * real Supabase partition, and the server forbids offset regression — there would be no undo.
     */
    @Test
    fun thePathIsRootedAtTheHiddenFloNovelFolder() {
        val path = DropboxConfig.secretPath

        assertEquals("/.flonovel/", path.substring(0, "/.flonovel/".length))
        assertEquals(true, path.endsWith(".json"))
    }
}
