package com.moonkata.flonovel.android.data.file

// Internal backups and synchronization metadata are not browsing destinations.
fun isVisibleLibraryFolder(name: String): Boolean = !name.equals(".flonovel", ignoreCase = true)
