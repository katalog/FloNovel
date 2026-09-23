package com.moonkata.flonovel.desktop.platform

import java.awt.Desktop
import java.nio.file.Path

object SystemTrash {
    /**
     * True when the OS exposes a recycle bin / trash through AWT. Windows and macOS do;
     * many Linux desktops do not, and there we refuse rather than silently deleting for good.
     */
    val isSupported: Boolean
        get() = runCatching {
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
        }.getOrDefault(false)

    /** Returns true only when the OS reports the file was moved to the trash. */
    fun moveToTrash(path: Path): Boolean =
        runCatching { Desktop.getDesktop().moveToTrash(path.toFile()) }.getOrDefault(false)
}
