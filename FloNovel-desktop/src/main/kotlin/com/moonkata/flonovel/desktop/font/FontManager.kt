package com.moonkata.flonovel.desktop.font

import com.moonkata.flonovel.desktop.platform.configDir
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * What the Settings screen shows for one catalog entry.
 *
 * Four states, derived from [FontSource] plus whether the font is actually
 * present. Only [Downloadable] gets a download button — offering one for
 * [FontSource.SystemOnly] or [FontSource.OfficialPage] would either be
 * impossible or a license violation.
 */
enum class FontStatus {
    /** Present — on the system, or already downloaded by us. */
    INSTALLED,

    /** Group A, not present yet — we can fetch it. */
    DOWNLOADABLE,

    /** Group C, not present — vendor has no direct URL; open their page. */
    MANUAL_INSTALL,

    /** Group B, not present — bundled with another OS. Nothing we can do. */
    UNAVAILABLE,
}

data class FontState(
    val font: CatalogFont,
    val status: FontStatus,
)

data class CustomFont(
    val displayName: String,
    val familyName: String,
    val fileName: String,
    val filePath: Path,
)

/**
 * Resolves catalog entries against what is actually available.
 *
 * A font counts as installed when either
 *  - the OS reports the family name (system fonts, or one the user installed
 *    manually after visiting a Group C page), or
 *  - we previously downloaded it into [fontsDir].
 *
 * A leftover `.part` file is never treated as installed — see [FontDownloader].
 */
object FontManager {

    /** configDir()/fonts — where Group A downloads land and user custom fonts reside. */
    fun fontsDir(): Path = configDir().resolve("fonts")

    /**
     * Opens the fonts folder in the OS file manager (Explorer, Finder, etc.).
     */
    fun openFontsFolder(fontsDir: Path = fontsDir()): Boolean {
        return try {
            Files.createDirectories(fontsDir)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(fontsDir.toFile())
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Scans [fontsDir] for custom font files (.ttf, .otf) not part of the standard catalog.
     */
    fun customFonts(fontsDir: Path = fontsDir()): List<CustomFont> {
        if (!Files.isDirectory(fontsDir)) return emptyList()
        val catalogFileNames = FontCatalog.fonts.map { it.fileName.lowercase() }.toSet()
        val results = mutableListOf<CustomFont>()
        try {
            Files.list(fontsDir).use { stream ->
                stream.filter { file ->
                    val name = file.fileName.toString().lowercase()
                    (name.endsWith(".ttf") || name.endsWith(".otf")) && !name.endsWith(".part")
                }.forEach { file ->
                    val fileName = file.fileName.toString()
                    if (fileName.lowercase() !in catalogFileNames && Files.size(file) > 0) {
                        var familyName = fileName.substringBeforeLast('.')
                        try {
                            val skiaTypeface = org.jetbrains.skia.FontMgr.default.makeFromFile(file.toString(), 0)
                            if (skiaTypeface != null && skiaTypeface.familyName.isNotBlank()) {
                                familyName = skiaTypeface.familyName
                            }
                        } catch (_: Throwable) {}
                        results.add(
                            CustomFont(
                                displayName = familyName,
                                familyName = familyName,
                                fileName = fileName,
                                filePath = file,
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {}
        return results.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Finds a font file on disk matching [familyName] (catalog downloaded font or custom font).
     */
    fun findFontFile(familyName: String, fontsDir: Path = fontsDir()): Path? {
        val trimmed = familyName.trim()
        if (trimmed.isBlank() || !Files.isDirectory(fontsDir)) return null

        // 1. Catalog font matching
        val catalogEntry = FontCatalog.fonts.firstOrNull { it.familyName.equals(trimmed, ignoreCase = true) }
        if (catalogEntry != null && catalogEntry.fileName.isNotBlank()) {
            val file = fontsDir.resolve(catalogEntry.fileName)
            if (Files.isRegularFile(file) && Files.size(file) > 0) return file
        }

        // 2. Custom font matching
        val custom = customFonts(fontsDir).firstOrNull {
            it.familyName.equals(trimmed, ignoreCase = true) ||
            it.fileName.equals(trimmed, ignoreCase = true) ||
            it.fileName.substringBeforeLast('.').equals(trimmed, ignoreCase = true)
        }
        return custom?.filePath
    }

    /**
     * Family names the OS knows about. Passed in so tests can supply a fixed
     * set instead of depending on the machine's installed fonts.
     */
    fun statusOf(
        font: CatalogFont,
        systemFamilies: Set<String>,
        fontsDir: Path = fontsDir(),
    ): FontStatus {
        if (isPresent(font, systemFamilies, fontsDir)) return FontStatus.INSTALLED

        return when (font.source) {
            is FontSource.Download -> FontStatus.DOWNLOADABLE
            is FontSource.OfficialPage -> FontStatus.MANUAL_INSTALL
            is FontSource.SystemOnly -> FontStatus.UNAVAILABLE
        }
    }

    fun states(
        systemFamilies: Set<String>,
        fontsDir: Path = fontsDir(),
    ): List<FontState> = FontCatalog.fonts.map { FontState(it, statusOf(it, systemFamilies, fontsDir)) }

    private fun isPresent(font: CatalogFont, systemFamilies: Set<String>, fontsDir: Path): Boolean {
        // Family-name match is case-insensitive: the OS may report
        // "Nanum Gothic" where the vendor writes "NanumGothic".
        val normalized = font.familyName.replace(" ", "").lowercase()
        val onSystem = systemFamilies.any { it.replace(" ", "").lowercase() == normalized }
        if (onSystem) return true

        if (font.fileName.isBlank()) return false
        val file = fontsDir.resolve(font.fileName)
        // Size guard: a 0-byte file left by a failed write is not "installed".
        return Files.isRegularFile(file) && Files.size(file) > 0
    }

    /** Absolute path of a downloaded font file, or null if we do not have it. */
    fun downloadedFile(font: CatalogFont, fontsDir: Path = fontsDir()): Path? {
        if (font.fileName.isBlank()) return null
        val file = fontsDir.resolve(font.fileName)
        return if (Files.isRegularFile(file) && Files.size(file) > 0) file else null
    }

    /** All downloaded font files, for loading into the text renderer at startup. */
    fun downloadedFiles(fontsDir: Path = fontsDir()): List<Path> =
        FontCatalog.downloadable.mapNotNull { downloadedFile(it, fontsDir) }

    /**
     * Opens the vendor page for a Group C font. Returns false when the platform
     * cannot open a browser, so the caller can show the URL for manual copying
     * instead of failing silently.
     */
    fun openOfficialPage(font: CatalogFont): Boolean {
        val source = font.source as? FontSource.OfficialPage ?: return false
        return try {
            if (Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
            ) {
                Desktop.getDesktop().browse(URI.create(source.pageUrl))
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** The page URL for a Group C font, so the UI can show it when opening fails. */
    fun officialPageUrl(font: CatalogFont): String? =
        (font.source as? FontSource.OfficialPage)?.pageUrl
}
