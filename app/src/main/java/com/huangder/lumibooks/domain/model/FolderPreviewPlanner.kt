package com.huangder.lumibooks.domain.model

/** Selects the first four books for a folder snapshot without changing their source order. */
object FolderPreviewPlanner {
    fun selectBookIds(
        booksInLibraryOrder: List<Book>,
        folders: List<LibraryFolder>,
        links: List<BookFolderLink>,
        folderId: String
    ): List<String> {
        val subtreeIds = descendantFolderIds(folders, folderId)
        val folderByBook = links.associate { it.bookId to it.folderId }
        return booksInLibraryOrder.asSequence()
            .filter { folderByBook[it.id] in subtreeIds }
            .map { it.id }
            .take(4)
            .toList()
    }

    fun slots(
        previewBookIds: List<String>?,
        booksById: Map<String, Book>,
        presentBookIds: Set<String>? = null
    ): List<Book?> = List(4) { index ->
        previewBookIds?.getOrNull(index)
            ?.takeIf { presentBookIds == null || it in presentBookIds }
            ?.let(booksById::get)
    }

    private fun descendantFolderIds(
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
}
