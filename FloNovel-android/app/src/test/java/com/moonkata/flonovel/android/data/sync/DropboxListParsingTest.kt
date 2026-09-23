package com.moonkata.flonovel.android.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing and delta-shaping for Dropbox sync, against recorded response shapes. Pure JVM: nothing
 * here touches the network, a device, or SAF. The parts that do — OAuth's Base64 and the SAF writes —
 * are covered in androidTest.
 */
class DropboxListParsingTest {

    private fun fileEntry(pathDisplay: String, size: Long): String = """
        {".tag":"file","name":"x","path_lower":"${pathDisplay.lowercase()}","path_display":"$pathDisplay","size":$size}
    """.trimIndent()

    private fun body(entries: List<String>, cursor: String = "CUR", hasMore: Boolean = false): String =
        """{"entries":[${entries.joinToString(",")}],"cursor":"$cursor","has_more":$hasMore}"""

    @Test
    fun parsesFilesWithCursorAndHasMore() {
        val result = parseListFolderBody(
            body(listOf(fileEntry("/books/A/One.txt", 120)), cursor = "AAA", hasMore = true),
        )

        val success = result as DropboxListResult.Success
        assertEquals("AAA", success.cursor)
        assertTrue(success.hasMore)
        val file = success.entries.single() as DropboxEntry.File
        assertEquals("/books/A/One.txt", file.pathDisplay)
        assertEquals("/books/a/one.txt", file.pathLower)
        assertEquals(120L, file.size)
    }

    @Test
    fun parsesDeletedEntries() {
        val result = parseListFolderBody(
            """{"entries":[{".tag":"deleted","name":"One.txt","path_lower":"/books/a/one.txt"}],"cursor":"C","has_more":false}""",
        )

        val entry = (result as DropboxListResult.Success).entries.single()
        assertEquals(DropboxEntry.Deleted("/books/a/one.txt"), entry)
    }

    /** Folders arrive in every recursive listing and are not files — carrying them would make the
     *  delta try to download a directory. */
    @Test
    fun folderEntriesAreDropped() {
        val result = parseListFolderBody(
            """{"entries":[{".tag":"folder","name":"A","path_lower":"/books/a"}],"cursor":"C","has_more":false}""",
        )

        assertTrue((result as DropboxListResult.Success).entries.isEmpty())
    }

    /** A truncated or non-JSON body must not crash a background sync. */
    @Test
    fun malformedBodyBecomesAFailure_notAnException() {
        assertTrue(parseListFolderBody("not json at all") is DropboxListResult.Failure)
    }

    @Test
    fun emptyListingIsSuccessWithNoEntries() {
        val result = parseListFolderBody("""{"entries":[],"cursor":"C","has_more":false}""")

        assertTrue((result as DropboxListResult.Success).entries.isEmpty())
    }

    // ── Path mapping ───────────────────────────────────────────────────────

    @Test
    fun stripsTheBooksPrefix() {
        assertEquals("A/One.txt", relativeBookPath("/books/A/One.txt"))
    }

    /**
     * `/.flonovel/secret.json` shares the app folder with the books. Treating it as a book would
     * write the shared secret into the user's visible library.
     */
    @Test
    fun pathsOutsideBooksAreNotBooks() {
        assertEquals(null, relativeBookPath("/.flonovel/secret.json"))
        assertEquals(null, relativeBookPath("/booksellers/One.txt"))
        assertEquals(null, relativeBookPath("/books"))
        assertEquals(null, relativeBookPath("/books/"))
    }

    /** Deleted entries only carry `path_lower`, so the prefix match cannot be case-sensitive. */
    @Test
    fun prefixMatchIgnoresCase() {
        assertEquals("A/One.txt", relativeBookPath("/BOOKS/A/One.txt"))
    }

    // ── Delta shaping ──────────────────────────────────────────────────────

    /**
     * A name typed on macOS arrives NFD-decomposed while the same name on the phone is composed.
     * Without the NFC pass in [syncKeyOf] they never match, and the file is re-downloaded forever.
     */
    @Test
    fun decomposedAndComposedHangulProduceTheSameKey() {
        val composed = "한글/책.txt"
        // Derived rather than typed, so the source file's own encoding cannot quietly compose the
        // fixture and turn this into a test of nothing.
        val decomposed = java.text.Normalizer.normalize(composed, java.text.Normalizer.Form.NFD)

        assertTrue("Fixture is not actually decomposed", composed != decomposed)
        assertEquals(syncKeyOf(composed), syncKeyOf(decomposed))
    }

    @Test
    fun remotePathIsRootedAtBooks() {
        assertEquals("/books/A/One.txt", remotePathOf("A/One.txt"))
    }

    // ── Authorize URL ──────────────────────────────────────────────────────

    /**
     * Without `token_access_type=offline` Dropbox returns no refresh token, and the link dies a few
     * hours after login — the kind of failure that gets reported as "it worked yesterday".
     */
    @Test
    fun authorizeUrlRequestsOfflineAccessAndS256() {
        val url = DropboxOAuth.buildAuthorizeUrl(
            appKey = "KEY",
            codeChallenge = "CHALLENGE",
            redirectUri = "db-KEY://1/connect",
            scopes = "files.content.read",
        )

        assertTrue(url.startsWith("https://www.dropbox.com/oauth2/authorize?"))
        assertTrue(url.contains("token_access_type=offline"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=KEY"))
    }

    /** `db-KEY://1/connect` has to survive as a redirect_uri parameter intact. */
    @Test
    fun authorizeUrlPercentEncodesTheRedirectUri() {
        val url = DropboxOAuth.buildAuthorizeUrl("KEY", "CHALLENGE", "db-KEY://1/connect")

        assertTrue(url.contains("redirect_uri=db-KEY%3A%2F%2F1%2Fconnect"))
    }

    // ── Redirect handling ──────────────────────────────────────────────────

    @Test
    fun extractsTheAuthorizationCode() {
        val code = DropboxOAuth.extractAuthorizationCode(
            "db-KEY://1/connect?code=ABC123",
            "db-KEY://1/connect",
        )

        assertEquals("ABC123", code)
    }

    /** Denying consent must read as "cancelled", not as a code. */
    @Test
    fun deniedConsentYieldsNoCode() {
        assertEquals(
            null,
            DropboxOAuth.extractAuthorizationCode("db-KEY://1/connect?error=access_denied", "db-KEY://1/connect"),
        )
    }

    /** Another app's deep link must never be mistaken for our redirect. */
    @Test
    fun aForeignRedirectYieldsNoCode() {
        assertEquals(
            null,
            DropboxOAuth.extractAuthorizationCode("db-OTHER://1/connect?code=ABC123", "db-KEY://1/connect"),
        )
    }

    // ── T-29: remote paths become local folder and file names ─────────────

    @Test
    fun ordinaryRelativePathsAreSafe() {
        assertTrue(isSafeRelativePath("One.txt"))
        assertTrue(isSafeRelativePath("Folder/One.txt"))
        assertTrue(isSafeRelativePath("한글 폴더/책.txt"))
    }

    /**
     * Every segment is handed to createDirectory/createFile. Dropbox should never send these, but
     * "the server is well behaved" is not a control.
     */
    @Test
    fun traversalAndEmptySegmentsAreRejected() {
        assertFalse("parent traversal", isSafeRelativePath("../evil.txt"))
        assertFalse("traversal mid-path", isSafeRelativePath("a/../../evil.txt"))
        assertFalse("current dir", isSafeRelativePath("./evil.txt"))
        assertFalse("empty segment", isSafeRelativePath("a//b.txt"))
        assertFalse("absolute", isSafeRelativePath("/etc/passwd"))
        assertFalse("blank", isSafeRelativePath("   "))
        assertFalse("empty", isSafeRelativePath(""))
    }

    /** The rejection has to happen where the path enters the app, not only in the helper. */
    @Test
    fun aTraversalPathNeverBecomesABook() {
        assertEquals(null, relativeBookPath("/books/../../evil.txt"))
        assertEquals(null, relativeBookPath("/books/a/../../evil.txt"))
    }

    /**
     * A backslash is an ordinary character in an Android file name. Rejecting it would silently
     * drop real books whose titles contain one.
     */
    @Test
    fun aBackslashInANameIsStillAllowed() {
        assertTrue(isSafeRelativePath("AC\\DC 전기.txt"))
    }

    @Test
    fun asciiSafeDropboxApiArg_escapesKoreanAndSpecialCharacters() {
        val rawJson = """{"path":"/books/0829/[AI번역]＜R18＞소설〜1-8.txt"}"""
        val safeHeader = asciiSafeDropboxApiArg(rawJson)

        assertTrue(safeHeader.all { it.code in 32..126 })
        assertTrue(safeHeader.contains("\\ubc88\\uc5ed"))
        assertTrue(safeHeader.contains("\\uff1c")) // ＜
        assertTrue(safeHeader.contains("\\u301c")) // 〜
    }
}

