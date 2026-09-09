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
     * `path_display` carries the author's casing and `path_lower` does not, so building from the
     * wrong one silently renames every downloaded file to lowercase on the phone.
     */
    @Test
    fun remoteBooksKeepOriginalCasingButMatchCaseInsensitively() {
        val entries = listOf(DropboxEntry.File("/books/a/one.txt", "/books/A/One.txt", 10))

        val books = remoteBooksByKey(entries)

        assertEquals("A/One.txt", books.getValue("a/one.txt").relativePath)
    }

    @Test
    fun aDeleteAfterAnAddRemovesTheFileFromTheDelta() {
        val entries = listOf(
            DropboxEntry.File("/books/a/one.txt", "/books/A/One.txt", 10),
            DropboxEntry.Deleted("/books/a/one.txt"),
        )

        assertTrue(remoteBooksByKey(entries).isEmpty())
    }

    /** Re-adding after a delete in the same window means the file exists — the add is the truth. */
    @Test
    fun anAddAfterADeleteKeepsTheFile() {
        val entries = listOf(
            DropboxEntry.Deleted("/books/a/one.txt"),
            DropboxEntry.File("/books/a/one.txt", "/books/A/One.txt", 99),
        )

        assertEquals(99L, remoteBooksByKey(entries).getValue("a/one.txt").sizeBytes)
    }

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

    // ── Which deletions a delta page actually applies ──────────────────────

    @Test
    fun aPlainDeletionIsApplied() {
        val entries = listOf(DropboxEntry.Deleted("/books/a/one.txt"))

        assertEquals(listOf("a/one.txt"), deletionKeysToApply(entries, remoteBooksByKey(entries)))
    }

    /**
     * The write pass runs before the delete pass, so a file deleted and re-added in the same window
     * would otherwise be downloaded and then thrown away in the same sync.
     */
    @Test
    fun aDeletionCancelledByALaterAddIsNotApplied() {
        val entries = listOf(
            DropboxEntry.Deleted("/books/a/one.txt"),
            DropboxEntry.File("/books/a/one.txt", "/books/A/One.txt", 99),
        )

        assertTrue(deletionKeysToApply(entries, remoteBooksByKey(entries)).isEmpty())
    }

    /**
     * The re-added copy may be byte-identical, so it never enters the download list. Deciding
     * against the download list instead of the full remote picture would delete a file that exists.
     */
    @Test
    fun aDeletionCancelledByAnIdenticalReAddIsNotApplied() {
        val entries = listOf(
            DropboxEntry.Deleted("/books/a/one.txt"),
            DropboxEntry.File("/books/a/one.txt", "/books/A/One.txt", 10),
        )
        val remote = remoteBooksByKey(entries)

        assertTrue("The file is present remotely, so it must survive", deletionKeysToApply(entries, remote).isEmpty())
    }

    /** Nothing outside /books is a book, so a secret-file deletion must not touch the library. */
    @Test
    fun deletionsOutsideBooksAreIgnored() {
        val entries = listOf(DropboxEntry.Deleted("/.flonovel/secret.json"))

        assertTrue(deletionKeysToApply(entries, emptyMap()).isEmpty())
    }

    @Test
    fun repeatedDeletionsOfTheSamePathCollapseToOne() {
        val entries = listOf(
            DropboxEntry.Deleted("/books/a/one.txt"),
            DropboxEntry.Deleted("/books/a/one.txt"),
        )

        assertEquals(listOf("a/one.txt"), deletionKeysToApply(entries, emptyMap()))
    }

    // ── Which cursor survives a sync pass ──────────────────────────────────

    /**
     * The rule that keeps a failed download from being lost forever. Dropbox only reports changes
     * *since* the cursor, so advancing past a file that never landed means it is never mentioned
     * again — the phone would be permanently missing a book with no error anywhere.
     */
    @Test
    fun aFailedFileHoldsTheCursorWhereItWas() {
        val result = DropboxSyncResult(downloaded = 5, updated = 0, deleted = 0, failed = 1)

        assertEquals("OLD", cursorAfterSync(previousCursor = "OLD", listingCursor = "NEW", result = result))
    }

    @Test
    fun aCleanPassAdvancesTheCursor() {
        val result = DropboxSyncResult(downloaded = 5, updated = 2, deleted = 1, failed = 0)

        assertEquals("NEW", cursorAfterSync(previousCursor = "OLD", listingCursor = "NEW", result = result))
    }

    /** Nothing to do is still a clean pass — the listing was read, so its cursor is current. */
    @Test
    fun aPassThatChangedNothingStillAdvancesTheCursor() {
        val result = DropboxSyncResult(downloaded = 0, updated = 0, deleted = 0, failed = 0)

        assertEquals("NEW", cursorAfterSync(previousCursor = "OLD", listingCursor = "NEW", result = result))
    }

    /**
     * First sync ever: the previous cursor is blank. A failure must keep it blank so the next run is
     * another full listing — which reconciles by content and picks the failed file back up.
     */
    @Test
    fun aFailureOnTheFirstSyncKeepsTheCursorBlank() {
        val result = DropboxSyncResult(downloaded = 3, updated = 0, deleted = 0, failed = 2)

        assertEquals("", cursorAfterSync(previousCursor = "", listingCursor = "NEW", result = result))
    }

    /** One failure out of many is still a failure — partial success must not advance. */
    @Test
    fun oneFailureAmongManySuccessesIsStillAFailure() {
        val result = DropboxSyncResult(downloaded = 200, updated = 50, deleted = 10, failed = 1)

        assertEquals("OLD", cursorAfterSync(previousCursor = "OLD", listingCursor = "NEW", result = result))
    }

    // ── T-29: the remote must not be able to wipe the library ─────────────

    /**
     * The dangerous case: the remote listing came back empty while the phone holds books. That is
     * far more likely to be the wrong account, or a Desktop app that has never run, than a real
     * "delete everything" — and acting on it is unrecoverable, because sync is one-way and there is
     * no second copy to restore from.
     */
    @Test
    fun anEmptyRemoteWithBooksOnTheDeviceIsTreatedAsSuspicious() {
        assertTrue(isSuspiciousWipe(remoteCount = 0, localCount = 120))
    }

    /** A genuinely empty device has nothing to lose, so there is nothing to withhold. */
    @Test
    fun anEmptyRemoteAndAnEmptyDeviceIsNotSuspicious() {
        assertFalse(isSuspiciousWipe(remoteCount = 0, localCount = 0))
    }

    /** One remote book proves the listing is real, so ordinary deletions go ahead. */
    @Test
    fun aRemoteHoldingAnythingAtAllIsNotSuspicious() {
        assertFalse(isSuspiciousWipe(remoteCount = 1, localCount = 120))
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

