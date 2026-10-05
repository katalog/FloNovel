package com.moonkata.flonovel.android.data.file

data class RemovalChild<T>(val id: T, val name: String, val isFolder: Boolean)

/** Uses an unfiltered listing because the reader hides files that deletion still removes. */
suspend fun <T> folderRemovalContents(root: T, children: suspend (T) -> List<RemovalChild<T>>): List<String> {
    val paths = mutableListOf<String>()
    val visited = mutableSetOf(root)
    suspend fun visit(parent: T, prefix: String) {
        for (child in children(parent)) {
            check(visited.add(child.id)) { "Repeated document in folder listing" }
            val path = prefix + child.name
            paths += path
            if (child.isFolder) visit(child.id, "$path/")
        }
    }
    visit(root, "")
    return paths.sorted()
}
