package com.moonkata.flonovel.desktop.platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

object FolderPicker {
    /**
     * Opens a native system directory selection dialog.
     * Returns the selected folder's absolute path, or null if cancelled.
     */
    fun chooseFolder(initialFolder: String? = null): String? {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Throwable) {
        }
        val chooser = JFileChooser().apply {
            dialogTitle = com.moonkata.flonovel.desktop.i18n.Strings.get("folder_picker_title")
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            if (!initialFolder.isNullOrBlank()) {
                val file = File(initialFolder)
                if (file.exists()) {
                    currentDirectory = if (file.isDirectory) file else file.parentFile
                    selectedFile = file
                }
            }
        }
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { resolvePickedFolder(it, chooser.currentDirectory) }?.absolutePath
        } else {
            null
        }
    }
}

/**
 * Undoes the GTK chooser's doubled path. On Linux, clicking a folder and then entering it leaves
 * its name in the file-name field, so OK returns `<folder>/<folder>`, a path that does not exist.
 * The first Linux run saved `~/문서/FloNovel/FloNovel` as the home folder this way and sync then
 * skipped every pass because the folder was missing.
 */
internal fun resolvePickedFolder(selected: File, currentDirectory: File?): File {
    val doubled = currentDirectory != null &&
        !selected.exists() &&
        selected.parentFile == currentDirectory &&
        selected.name == currentDirectory.name
    return if (doubled) currentDirectory!! else selected
}
