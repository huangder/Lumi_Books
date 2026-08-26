package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFolderLink
import com.huangder.lumibooks.domain.model.LibraryFolder

internal fun directChildFolders(
    folders: List<LibraryFolder>,
    parentId: String?
): List<LibraryFolder> = folders.filter { it.parentId == parentId }

internal fun booksAtFolderLevel(
    books: List<Book>,
    links: List<BookFolderLink>,
    folderId: String?
): List<Book> {
    val folderByBook = links.associate { it.bookId to it.folderId }
    return books.filter { book -> folderByBook[book.id] == folderId }
}

internal fun folderPath(
    folders: List<LibraryFolder>,
    folderId: String?
): List<LibraryFolder> {
    val byId = folders.associateBy { it.id }
    val reversed = mutableListOf<LibraryFolder>()
    val visited = mutableSetOf<String>()
    var cursor = folderId
    while (cursor != null && visited.add(cursor)) {
        val folder = byId[cursor] ?: break
        reversed += folder
        cursor = folder.parentId
    }
    return reversed.asReversed()
}

internal fun descendantFolderIds(
    folders: List<LibraryFolder>,
    folderId: String
): Set<String> {
    val children = folders.groupBy { it.parentId }
    val result = linkedSetOf(folderId)
    val pending = ArrayDeque<String>().apply { add(folderId) }
    while (pending.isNotEmpty()) {
        children[pending.removeFirst()].orEmpty().forEach { child ->
            if (result.add(child.id)) pending.add(child.id)
        }
    }
    return result
}

internal fun folderBookCounts(
    folders: List<LibraryFolder>,
    links: List<BookFolderLink>
): Map<String, Int> = folders.associate { folder ->
    val subtree = descendantFolderIds(folders, folder.id)
    folder.id to links.count { it.folderId in subtree }
}

internal fun booksInFolderTree(
    books: List<Book>,
    links: List<BookFolderLink>,
    folders: List<LibraryFolder>,
    folderId: String
): List<Book> {
    val subtree = descendantFolderIds(folders, folderId)
    val bookIds = links.asSequence()
        .filter { it.folderId in subtree }
        .mapTo(mutableSetOf()) { it.bookId }
    return books.filter { it.id in bookIds }
}
