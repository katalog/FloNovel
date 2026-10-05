package com.moonkata.flonovel.android.data.sync

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File

private val folderDeletionStateLock = Any()

fun folderDeletionSync(
    context: Context,
    rootUri: Uri,
    files: LibraryFiles,
    client: DropboxClient,
    accountKey: String,
    isInUse: (String) -> Boolean,
): FolderDeletionSync {
    val scopeKey = ContentHash.of((rootUri.toString() + "\n" + accountKey).toByteArray(Charsets.UTF_8))
    val state = AtomicFile(File(context.filesDir, "folder-deletions-$scopeKey.json"))
    return FolderDeletionSync(
        scope = scopeKey,
        readState = { try { state.openRead().bufferedReader().use { it.readText() } } catch (_: java.io.FileNotFoundException) { null } },
        writeState = { text ->
            val output = state.startWrite()
            try {
                output.write(text.toByteArray(Charsets.UTF_8))
                state.finishWrite(output)
            } catch (e: Exception) {
                state.failWrite(output)
                throw e
            }
        },
        allFiles = { folder -> files.folderFiles(folder).map { file ->
            val hash = files.openRead(file.relativePath)?.use { ContentHash.of(it) }
                ?: error("Folder deletion file could not be read")
            FolderDeletionFile(file.relativePath, hash)
        } },
        folderExists = files::folderExists,
        deleteFile = files::delete,
        pruneFolder = files::pruneFolder,
        listRemote = client::listFolderDeletionIntents,
        publish = client::publishFolderDeletion,
        isInUse = isInUse,
        stateLock = folderDeletionStateLock,
        lookupRemoteHash = client::folderDeletionRemoteHash,
        removeRemoteFile = client::removeFolderDeletionFile,
    )
}
