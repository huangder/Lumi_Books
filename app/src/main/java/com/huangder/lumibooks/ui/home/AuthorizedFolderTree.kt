package com.huangder.lumibooks.ui.home

/** Key of the level above the authorized roots. It is only visible with several roots. */
internal const val VIRTUAL_ROOT_KEY = ""

/** A directory of the remembered authorized folders. */
internal data class FolderBooksNode(
    val key: String,
    val name: String,
    val treeUri: String,
    /** Null for the authorized root itself; otherwise a '/'-separated relative path. */
    val relativePath: String?,
    val parentKey: String,
    val depth: Int,
    /** Books inside this directory and every nested directory. */
    val bookCount: Int
)

/**
 * Android-free description of one remembered book. The page keeps the matching
 * [AuthorizedFolderFile] so this tree stays unit testable without a device.
 */
internal data class FolderBooksFile(
    val key: String,
    val name: String,
    val treeUri: String,
    val folderName: String,
    val relativeDirectory: String?,
    /** Required on purpose: forgetting them silently breaks size/time sorting. */
    val size: Long,
    val lastModified: Long
)

/** Sort keys offered by the folder books page. */
enum class FolderBooksSortMode(val defaultAscending: Boolean) {
    NAME(true),
    SIZE(false),
    TIME(false),
    FORMAT(true)
}

/** One row of a directory level: either a folder to enter or a book to select. */
internal data class FolderBooksItem(
    val key: String,
    val name: String,
    val folder: FolderBooksNode? = null,
    val file: FolderBooksFile? = null
) {
    val isFolder: Boolean get() = folder != null
}

/** One filename match inside the searched folder, with its path relative to that folder. */
internal data class FolderBooksSearchHit(
    val file: FolderBooksFile,
    /** Folder names from the searched folder down to the file's own folder; empty at the top. */
    val relativePath: List<String>
)

/**
 * Directory tree of the remembered authorized folders, built for file-manager style browsing.
 * Folders and books are never mixed: a level lists its folders first, then its books, each
 * sorted by name.
 */
internal class AuthorizedFolderTree(
    private val nodes: Map<String, FolderBooksNode>,
    private val itemsByFolder: Map<String, List<FolderBooksItem>>,
    /** Level shown when the page opens; the single authorized root, or the virtual root. */
    val initialFolderKey: String
) {
    fun items(folderKey: String): List<FolderBooksItem> = itemsByFolder[folderKey].orEmpty()

    fun hasItems(folderKey: String): Boolean = itemsByFolder[folderKey].orEmpty().isNotEmpty()

    fun folder(folderKey: String): FolderBooksNode? = nodes[folderKey]

    /**
     * Name matches inside [folderKey] and every nested folder. An empty query returns nothing.
     */
    fun searchSubtree(folderKey: String, query: String): List<FolderBooksSearchHit> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val hits = mutableListOf<FolderBooksSearchHit>()
        fun walk(key: String, path: List<String>) {
            itemsByFolder[key].orEmpty().forEach { item ->
                val folder = item.folder
                if (folder != null) {
                    walk(folder.key, path + folder.name)
                    return@forEach
                }
                val file = item.file ?: return@forEach
                if (file.name.lowercase().contains(needle)) {
                    hits += FolderBooksSearchHit(file = file, relativePath = path)
                }
            }
        }
        walk(folderKey, emptyList())
        return hits
    }

    /** Breadcrumb from the top visible level down to [folderKey]; empty at the virtual root. */
    fun breadcrumb(folderKey: String): List<FolderBooksNode> {
        val chain = ArrayDeque<FolderBooksNode>()
        var key: String? = folderKey
        while (key != null && key != VIRTUAL_ROOT_KEY) {
            val node = nodes[key] ?: break
            chain.addFirst(node)
            key = node.parentKey.takeIf { it != VIRTUAL_ROOT_KEY }
        }
        return chain.toList()
    }

    /** Null means the page should close instead of navigating to a parent level. */
    fun parentOf(folderKey: String): String? {
        if (folderKey == initialFolderKey) return null
        return nodes[folderKey]?.parentKey ?: parentKeyOf(folderKey)
    }

    /**
     * Keeps the level the user is on across refreshes: a folder that no longer exists falls back
     * to its nearest surviving ancestor, and finally to the initial level.
     */
    fun resolve(folderKey: String?): String {
        var key = folderKey
        while (key != null && key != VIRTUAL_ROOT_KEY) {
            if (nodes.containsKey(key)) return key
            key = parentKeyOf(key)
        }
        return initialFolderKey
    }

    private fun parentKeyOf(folderKey: String): String? = when {
        folderKey == VIRTUAL_ROOT_KEY -> null
        folderKey.contains(DIR_SEPARATOR) -> {
            val treeUri = folderKey.substringAfter("tree:").substringBefore(DIR_SEPARATOR)
            val path = folderKey.substringAfter(DIR_SEPARATOR)
            val parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
                .takeIf { it.isNotEmpty() }
            if (parentPath == null) "tree:$treeUri" else "tree:$treeUri$DIR_SEPARATOR$parentPath"
        }
        else -> VIRTUAL_ROOT_KEY
    }

    companion object {
        private const val DIR_SEPARATOR = "|dir:"
    }
}

internal fun authorizedFolderDirectoryKey(treeUri: String, relativePath: String?): String =
    if (relativePath == null) "tree:$treeUri" else "tree:$treeUri|dir:$relativePath"

/**
 * Folders always lead and stay sorted by name; books follow in the requested order.
 * The name comparison is always the final tie-break, so equal sizes or timestamps stay stable.
 */
internal fun sortFolderBooksItems(
    items: List<FolderBooksItem>,
    mode: FolderBooksSortMode,
    ascending: Boolean
): List<FolderBooksItem> {
    val folders = items.filter { it.isFolder }.sortedBy { it.name.lowercase() }
    val comparator = folderBooksComparator(mode, ascending)
    val books = items
        .mapNotNull { item -> item.file?.let { file -> item to file } }
        .sortedWith(Comparator { left, right -> comparator.compare(left.second, right.second) })
        .map { it.first }
    return folders + books
}

internal fun sortFolderBooksSearchHits(
    hits: List<FolderBooksSearchHit>,
    mode: FolderBooksSortMode,
    ascending: Boolean
): List<FolderBooksSearchHit> =
    hits.sortedWith(compareBy(folderBooksComparator(mode, ascending)) { it.file })

private fun folderBooksComparator(
    mode: FolderBooksSortMode,
    ascending: Boolean
): Comparator<FolderBooksFile> {
    val nameAscending = compareBy<FolderBooksFile> { it.name.lowercase() }.thenBy { it.name }
    if (mode == FolderBooksSortMode.NAME) {
        return if (ascending) nameAscending else nameAscending.reversed()
    }
    val primary: Comparator<FolderBooksFile> = when (mode) {
        FolderBooksSortMode.SIZE -> compareBy { it.size }
        FolderBooksSortMode.TIME -> compareBy { it.lastModified }
        FolderBooksSortMode.FORMAT -> compareBy { it.extensionName() }
        FolderBooksSortMode.NAME -> nameAscending
    }
    val ordered = if (ascending) primary else primary.reversed()
    return ordered.then(nameAscending)
}

internal fun FolderBooksFile.extensionName(): String =
    name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

private fun parentPathOf(path: String?): String? = path
    ?.substringBeforeLast('/', missingDelimiterValue = "")
    ?.takeIf { it.isNotEmpty() }

/**
 * Builds the browsing tree from the remembered snapshot. Directories come from the scan, so empty
 * folders stay visible; a directory that is missing from the scan but owns files is synthesized
 * so no book can disappear from the page.
 */
internal fun buildAuthorizedFolderTree(
    files: List<FolderBooksFile>,
    folders: List<AuthorizedFolderFolder>
): AuthorizedFolderTree {
    val treeUris = LinkedHashSet<String>()
    folders.mapTo(treeUris) { it.treeUri }
    files.mapTo(treeUris) { it.treeUri }

    val rootNames = HashMap<String, String>()
    folders.forEach { folder ->
        if (folder.relativePath == null) rootNames[folder.treeUri] = folder.name
    }
    files.forEach { file -> rootNames.putIfAbsent(file.treeUri, file.folderName) }

    val nodes = LinkedHashMap<String, FolderBooksNode>()
    treeUris.forEach { treeUri ->
        val key = authorizedFolderDirectoryKey(treeUri, null)
        nodes[key] = FolderBooksNode(
            key = key,
            name = rootNames[treeUri]?.takeIf { it.isNotBlank() } ?: treeUri,
            treeUri = treeUri,
            relativePath = null,
            parentKey = VIRTUAL_ROOT_KEY,
            depth = 1,
            bookCount = 0
        )
    }
    folders.filter { it.relativePath != null }.forEach { folder ->
        val path = folder.relativePath.orEmpty()
        val key = authorizedFolderDirectoryKey(folder.treeUri, path)
        nodes.putIfAbsent(
            key,
            FolderBooksNode(
                key = key,
                name = folder.name.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/'),
                treeUri = folder.treeUri,
                relativePath = path,
                parentKey = authorizedFolderDirectoryKey(folder.treeUri, parentPathOf(path)),
                depth = 1,
                bookCount = 0
            )
        )
    }

    // Every book points at a directory; synthesize missing ancestors so nothing is orphaned.
    val placements = files.map { file ->
        val ownKey = authorizedFolderDirectoryKey(file.treeUri, file.relativeDirectory)
        var path = file.relativeDirectory
        var key = ownKey
        while (!nodes.containsKey(key)) {
            val parentPath = parentPathOf(path)
            nodes[key] = FolderBooksNode(
                key = key,
                name = path?.substringAfterLast('/').orEmpty().takeIf { it.isNotBlank() }
                    ?: file.folderName,
                treeUri = file.treeUri,
                relativePath = path,
                parentKey = authorizedFolderDirectoryKey(file.treeUri, parentPath),
                depth = 1,
                bookCount = 0
            )
            path = parentPath
            key = nodes.getValue(key).parentKey
        }
        file to ownKey
    }

    val depths = HashMap<String, Int>(nodes.size)
    nodes.keys.forEach { key ->
        var depth = 1
        var parent = nodes[key]?.parentKey
        while (parent != null && parent != VIRTUAL_ROOT_KEY) {
            depth++
            parent = nodes[parent]?.parentKey ?: break
        }
        depths[key] = depth
    }

    val bookCounts = HashMap<String, Int>(nodes.size)
    placements.forEach { (_, folderKey) ->
        var key: String? = folderKey
        while (key != null && key != VIRTUAL_ROOT_KEY) {
            bookCounts[key] = (bookCounts[key] ?: 0) + 1
            key = nodes[key]?.parentKey
        }
    }

    val resolvedNodes = nodes.mapValues { (key, node) ->
        node.copy(depth = depths[key] ?: 1, bookCount = bookCounts[key] ?: 0)
    }

    val itemsByFolder = LinkedHashMap<String, MutableList<FolderBooksItem>>()
    resolvedNodes.values
        .sortedBy { it.name.lowercase() }
        .forEach { node ->
            itemsByFolder.getOrPut(node.parentKey) { mutableListOf() } += FolderBooksItem(
                key = node.key,
                name = node.name,
                folder = node
            )
        }
    placements
        .sortedBy { (file, _) -> file.name.lowercase() }
        .forEach { (file, folderKey) ->
            itemsByFolder.getOrPut(folderKey) { mutableListOf() } += FolderBooksItem(
                key = file.key,
                name = file.name,
                file = file
            )
        }

    val initialFolderKey = treeUris.singleOrNull()
        ?.let { authorizedFolderDirectoryKey(it, null) }
        ?: VIRTUAL_ROOT_KEY
    return AuthorizedFolderTree(
        nodes = resolvedNodes,
        itemsByFolder = itemsByFolder.mapValues { (_, items) -> items.toList() },
        initialFolderKey = initialFolderKey
    )
}
