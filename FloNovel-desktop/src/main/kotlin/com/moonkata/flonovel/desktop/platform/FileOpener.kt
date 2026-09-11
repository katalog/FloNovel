package com.moonkata.flonovel.desktop.platform

import java.awt.Desktop
import java.nio.file.Path

/**
 * Reveals or opens a book file from the reader — the two actions a "where is this on disk" and
 * "open this with something else" shortcut need. Both are genuinely platform-specific (there is no
 * cross-platform "select this file in the file manager" API), which is why this lives in
 * `platform/` rather than being called directly from `ReaderView.kt`.
 */
object FileOpener {

    private val osName: String by lazy { System.getProperty("os.name")?.lowercase() ?: "" }

    /**
     * Opens the OS file manager with [file] pre-selected, so the user lands on it directly instead
     * of just the containing folder.
     *
     * Windows and macOS both have a real "reveal" primitive; Linux file managers don't share one
     * (GNOME, KDE, XFCE... each would need its own command), so the fallback there is opening the
     * parent folder — still useful, just without the highlight.
     */
    fun revealInFileManager(file: Path): Boolean {
        val absolute = file.toAbsolutePath()
        return try {
            when {
                osName.contains("windows") -> {
                    ProcessBuilder("explorer.exe", "/select,${absolute}").start()
                    true
                }
                osName.contains("mac") -> {
                    ProcessBuilder("open", "-R", absolute.toString()).start()
                    true
                }
                else -> {
                    val parent = absolute.parent ?: return false
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(parent.toFile())
                        true
                    } else {
                        false
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Opens [file] with whatever application the OS has associated with its extension. */
    fun openWithDefaultApp(file: Path): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile())
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
