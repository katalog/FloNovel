package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.AtomicFile
import com.moonkata.flonovel.desktop.library.DeleteAction
import com.moonkata.flonovel.desktop.library.DeleteSettings
import com.moonkata.flonovel.desktop.library.FileRemover
import com.moonkata.flonovel.desktop.library.RemovalOutcome
import com.moonkata.flonovel.desktop.library.SettingsStore
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

private val folderDeletionStateLock = Any()

fun folderDeletionSync(
    home: Path,
    stateFile: Path,
    client: DropboxClient,
    settings: SettingsStore,
    trash: ((Path) -> Boolean)?,
    canApply: () -> Boolean = { true },
    isInUse: (Path) -> Boolean,
): FolderDeletionSync {
    val root = home.toAbsolutePath().normalize()
    fun resolve(relative: String): Path {
        FolderDeletionIntent.validatePath(relative)
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && path != root) { "Folder deletion outside library" }
        var parent: Path? = path
        while (parent != null && parent != root) {
            require(!Files.isSymbolicLink(parent)) { "Folder deletion cannot follow a symbolic link" }
            parent = parent.parent
        }
        return path
    }
    val scopeKey = ContentHash.of((root.toString() + "\n" + client.credentialsStore.load().dropboxAccountId.orEmpty()).toByteArray(Charsets.UTF_8))
    val scopedState = stateFile.resolveSibling("${stateFile.fileName.toString().removeSuffix(".json")}-$scopeKey.json")
    return FolderDeletionSync(
        scope = scopeKey,
        readState = { AtomicFile.readIfExists(scopedState) },
        writeState = { AtomicFile.writeAtomic(scopedState, it) },
        allFiles = { folder ->
            val target = resolve(folder)
            if (!Files.exists(target, NOFOLLOW_LINKS)) emptyList() else Files.walk(target).use { paths ->
                paths.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }.map {
                    FolderDeletionFile(root.relativize(it).toString().replace('\\', '/'), ContentHash.of(it))
                }.toList()
            }
        },
        folderExists = { Files.isDirectory(resolve(it), NOFOLLOW_LINKS) },
        deleteFile = { relative ->
            val configured = settings.load().delete
            val action = if (configured.action == DeleteAction.MOVE &&
                FileRemover.checkMoveFolder(configured.moveFolder, root) != null) DeleteSettings() else configured
            val result = FileRemover.removeFile(resolve(relative), action, root, trash)
            result is RemovalOutcome.Moved || result is RemovalOutcome.Trashed
        },
        pruneFolder = { folder ->
            val target = resolve(folder)
            if (Files.exists(target, NOFOLLOW_LINKS)) {
                Files.walk(target).use { paths ->
                    paths.filter { Files.isDirectory(it, NOFOLLOW_LINKS) }.sorted(Comparator.reverseOrder()).forEach {
                        if (FileRemover.isEmptyFolder(it)) check(FileRemover.removeEmptyFolder(it) is RemovalOutcome.FolderRemoved)
                    }
                }
            }
            true
        },
        listRemote = client::listFolderDeletionIntents,
        publish = { client.publishFolderDeletion(it) },
        isInUse = { isInUse(resolve(it)) },
        stateLock = folderDeletionStateLock,
        lookupRemoteHash = { client.folderDeletionRemoteHash(it) },
        removeRemoteFile = { client.removeFolderDeletionFile(it) },
        canApply = canApply,
    )
}
