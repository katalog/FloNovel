package com.moonkata.flonovel.desktop.font

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the font catalog against the mistakes that broke its first revision.
 *
 * These are contract tests, not network tests: no test here downloads anything.
 * A previous revision pointed six entries at `fonts.google.com/download?family=`,
 * which returns 200 + text/html for ANY family name — the failure was invisible
 * because nothing checked what the URL actually was.
 */
class FontCatalogTest {

    @Test
    fun catalogHasThirteenCuratedFonts() {
        assertEquals(13, FontCatalog.fonts.size)
    }

    @Test
    fun displayNamesAreUnique() {
        val names = FontCatalog.fonts.map { it.displayName }
        assertEquals(names.size, names.toSet().size, "Duplicate displayName in catalog")
    }

    @Test
    fun familyNamesAreUnique() {
        val names = FontCatalog.fonts.map { it.familyName }
        assertEquals(names.size, names.toSet().size, "Duplicate familyName in catalog")
    }

    /**
     * The exact bug from the first revision: two different fonts sharing one URL
     * means at least one of them is pointing at the wrong file.
     */
    @Test
    fun everyDownloadUrlIsUsedByExactlyOneFont() {
        val urls = FontCatalog.downloadable.map { (it.source as FontSource.Download).url }
        assertEquals(urls.size, urls.toSet().size, "Two fonts share the same download URL")
    }

    /** `fonts.google.com/download` is a web page, not a download endpoint. */
    @Test
    fun noDownloadUrlPointsAtTheGoogleFontsWebPage() {
        FontCatalog.downloadable.forEach { font ->
            val url = (font.source as FontSource.Download).url
            assertFalse(
                url.contains("fonts.google.com/download", ignoreCase = true),
                "${font.displayName} uses the Google Fonts web page, which returns HTML for any name",
            )
        }
    }

    /**
     * Personal mirror repositories are not vendor distribution: they can vanish
     * or serve altered files.
     */
    @Test
    fun noDownloadUrlUsesAThirdPartyMirror() {
        FontCatalog.downloadable.forEach { font ->
            val url = (font.source as FontSource.Download).url
            assertFalse(
                url.contains("fonts-archive", ignoreCase = true),
                "${font.displayName} downloads from a third-party mirror",
            )
        }
    }

    @Test
    fun downloadUrlsUseHttps() {
        FontCatalog.downloadable.forEach { font ->
            val url = (font.source as FontSource.Download).url
            assertTrue(url.startsWith("https://"), "${font.displayName} is not served over HTTPS")
        }
    }

    /**
     * Georgia / Palatino / MS Gothic ship with the OS under commercial licenses.
     * Giving them a download URL would be a license violation, so the type system
     * and this test both have to keep them out of Group A.
     */
    @Test
    fun commercialSystemFontsAreNeverDownloadable() {
        val mustBeSystemOnly = listOf("Georgia", "Palatino Linotype", "MS Gothic")
        mustBeSystemOnly.forEach { name ->
            val font = FontCatalog.fonts.firstOrNull { it.familyName == name }
            assertNotNull(font, "$name missing from catalog")
            assertTrue(
                font.source is FontSource.SystemOnly,
                "$name must be SystemOnly — it cannot be redistributed",
            )
        }
    }

    /** Only fonts we actually write to disk may carry a file name. */
    @Test
    fun onlyDownloadableFontsHaveFileNames() {
        FontCatalog.fonts.forEach { font ->
            if (font.source is FontSource.Download) {
                assertTrue(font.fileName.isNotBlank(), "${font.displayName} has no fileName")
                assertFalse(
                    font.fileName.contains('/') || font.fileName.contains('\\'),
                    "${font.displayName} fileName must not contain a path separator",
                )
            } else {
                assertTrue(
                    font.fileName.isBlank(),
                    "${font.displayName} is not downloadable but declares a fileName",
                )
            }
        }
    }

    @Test
    fun nonDownloadableFontsExplainWhy() {
        FontCatalog.fonts
            .filter { it.source !is FontSource.Download }
            .forEach { assertTrue(it.note.isNotBlank(), "${it.displayName} has no explanatory note") }
    }

    @Test
    fun officialPageFontsCarryAPageUrl() {
        FontCatalog.fonts
            .mapNotNull { it.source as? FontSource.OfficialPage }
            .forEach { assertTrue(it.pageUrl.startsWith("https://"), "Bad page URL: ${it.pageUrl}") }
    }

    // ── Status resolution ──────────────────────────────────────────────────

    @Test
    fun statusIsInstalledWhenTheSystemReportsTheFamily() {
        val georgia = FontCatalog.fonts.first { it.familyName == "Georgia" }
        val dir = Files.createTempDirectory("fonts-none")
        assertEquals(
            FontStatus.INSTALLED,
            FontManager.statusOf(georgia, setOf("Georgia"), dir),
        )
    }

    /** The OS may report "NanumGothic" where the catalog says "Nanum Gothic". */
    @Test
    fun familyMatchingIgnoresSpacingAndCase() {
        val nanum = FontCatalog.fonts.first { it.familyName == "Nanum Gothic" }
        val dir = Files.createTempDirectory("fonts-none")
        assertEquals(
            FontStatus.INSTALLED,
            FontManager.statusOf(nanum, setOf("nanumgothic"), dir),
        )
    }

    @Test
    fun eachSourceKindMapsToItsOwnMissingStatus() {
        val dir = Files.createTempDirectory("fonts-none")
        val none = emptySet<String>()

        val downloadable = FontCatalog.fonts.first { it.source is FontSource.Download }
        val manual = FontCatalog.fonts.first { it.source is FontSource.OfficialPage }
        val systemOnly = FontCatalog.fonts.first { it.source is FontSource.SystemOnly }

        assertEquals(FontStatus.DOWNLOADABLE, FontManager.statusOf(downloadable, none, dir))
        assertEquals(FontStatus.MANUAL_INSTALL, FontManager.statusOf(manual, none, dir))
        assertEquals(FontStatus.UNAVAILABLE, FontManager.statusOf(systemOnly, none, dir))
    }

    @Test
    fun aDownloadedFileCountsAsInstalled() {
        val font = FontCatalog.downloadable.first()
        val dir = Files.createTempDirectory("fonts-have")
        Files.write(dir.resolve(font.fileName), ByteArray(2048))

        assertEquals(FontStatus.INSTALLED, FontManager.statusOf(font, emptySet(), dir))
        assertNotNull(FontManager.downloadedFile(font, dir))
    }

    /**
     * A crash mid-download leaves a `.part` file. Treating that as installed
     * would render garbage and offer no way to retry.
     */
    @Test
    fun aPartialDownloadIsNotInstalled() {
        val font = FontCatalog.downloadable.first()
        val dir = Files.createTempDirectory("fonts-part")
        Files.write(dir.resolve("${font.fileName}.part"), ByteArray(2048))

        assertEquals(FontStatus.DOWNLOADABLE, FontManager.statusOf(font, emptySet(), dir))
        assertNull(FontManager.downloadedFile(font, dir))
    }

    /** A zero-byte file left by a failed write is not a usable font either. */
    @Test
    fun anEmptyFileIsNotInstalled() {
        val font = FontCatalog.downloadable.first()
        val dir = Files.createTempDirectory("fonts-empty")
        Files.createFile(dir.resolve(font.fileName))

        assertEquals(FontStatus.DOWNLOADABLE, FontManager.statusOf(font, emptySet(), dir))
    }

    @Test
    fun statesCoverEveryCatalogEntry() {
        val dir = Files.createTempDirectory("fonts-none")
        assertEquals(FontCatalog.fonts.size, FontManager.states(emptySet(), dir).size)
    }

    @Test
    fun maruBuriIsDownloadableGroupA() {
        val maruBuri = FontCatalog.fonts.firstOrNull { it.familyName == "MaruBuri" }
        assertNotNull(maruBuri, "MaruBuri must be present in FontCatalog")
        assertTrue(maruBuri.source is FontSource.Download, "MaruBuri must be in Group A (Download)")
        val download = maruBuri.source as FontSource.Download
        assertEquals("MaruBuri-Regular.ttf", maruBuri.fileName)
        assertEquals("MaruBuri-Regular.ttf", download.zipEntryPattern)
        assertTrue(download.url.startsWith("https://github.com/naver/maruburi/releases/download/"))
        assertEquals(FontCategory.SERIF, maruBuri.category)
    }

    @Test
    fun everyFontHasCategory() {
        FontCatalog.fonts.forEach { font ->
            assertNotNull(font.category, "${font.displayName} must have a valid FontCategory")
        }
    }

    @Test
    fun categoryDistributionHasSerifSansAndLatin() {
        val categories = FontCatalog.fonts.map { it.category }.toSet()
        assertTrue(categories.contains(FontCategory.SERIF), "Catalog must contain SERIF fonts")
        assertTrue(categories.contains(FontCategory.SANS), "Catalog must contain SANS fonts")
        assertTrue(categories.contains(FontCategory.LATIN), "Catalog must contain LATIN fonts")
    }
}

