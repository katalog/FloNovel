package com.moonkata.flonovel.android.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The PostgREST endpoint the reader talks to. Pure string work, so it is checked here rather than
 * through MockWebServer — `ReadingPositionSyncClientTest` (androidTest) still proves the built URL
 * is what actually goes on the wire.
 */
class SupabaseEndpointTest {

    private val base = "https://abc.supabase.co"

    /**
     * Renamed from `reading_positions` in T-26 to match what the Desktop app already writes. If the
     * two ever drift apart, both apps keep working and simply never see each other's positions —
     * there is no error anywhere to notice.
     */
    @Test
    fun theTableIsFloNovelSync() {
        assertEquals("flonovel_sync", SUPABASE_TABLE)
        assertEquals("$base/rest/v1/flonovel_sync", supabaseRestEndpoint(base))
    }

    /**
     * A GitHub secret was once stored with `/rest/v1` already on the end, producing
     * `.../rest/v1/rest/v1/...` — rejected as "PGRST125: invalid path specified in request url".
     */
    @Test
    fun anAlreadySuffixedBaseUrlDoesNotDuplicateThePath() {
        assertEquals("$base/rest/v1/flonovel_sync", supabaseRestEndpoint("$base/rest/v1"))
    }

    /** A trailing newline from a pasted secret survives into the request line and breaks it. */
    @Test
    fun surroundingWhitespaceIsStripped() {
        assertEquals("$base/rest/v1/flonovel_sync", supabaseRestEndpoint("  $base \n"))
        assertEquals("$base/rest/v1/flonovel_sync", supabaseRestEndpoint("$base/rest/v1 \n"))
    }

    @Test
    fun aTrailingSlashDoesNotDoubleUp() {
        assertEquals("$base/rest/v1/flonovel_sync", supabaseRestEndpoint("$base/"))
    }
}
