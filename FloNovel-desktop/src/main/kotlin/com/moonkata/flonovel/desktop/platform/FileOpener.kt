package com.moonkata.flonovel.desktop.platform

import java.awt.Desktop
import java.nio.file.Files
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
                osName.contains("windows") -> revealOnWindows(absolute)
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

    /**
     * Windows' java.lang.ProcessBuilder builds the native command line through the system's legacy
     * ANSI code page (`sun.jnu.encoding`, e.g. MS949 on a Korean Windows install) rather than
     * Unicode, no matter what `file.encoding` is set to. A book title with a character outside that
     * code page (found in this project's own library: the wave-dash "〜" in "days before goodbye")
     * gets silently replaced with "?" before explorer.exe ever sees the path, so `/select` targets a
     * file that doesn't exist and explorer falls back to opening its default folder instead.
     *
     * Process *environment variables* don't go through that conversion — Windows transmits them as
     * UTF-16 regardless of the active code page — so the real path is passed that way instead of as
     * a command-line argument, and a tiny VBScript (itself pure ASCII, so its own argv is safe) reads
     * it back out and hands it to explorer.
     */
    private fun revealOnWindows(absolute: Path): Boolean {
        val script = Files.createTempFile("flonovel-reveal", ".vbs")
        script.toFile().deleteOnExit()
        Files.writeString(
            script,
            "Set WshShell = CreateObject(\"WScript.Shell\")\n" +
                "targetPath = WshShell.ExpandEnvironmentStrings(\"%FLONOVEL_REVEAL_PATH%\")\n" +
                "WshShell.Run \"explorer.exe /select,\"\"\" & targetPath & \"\"\"\", 1, False\n",
        )
        val pb = ProcessBuilder("wscript.exe", script.toString())
        pb.environment()["FLONOVEL_REVEAL_PATH"] = absolute.toString()
        pb.start()
        return true
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
