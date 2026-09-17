package com.huangder.lumibooks.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedFolderSortSearchTest {
    @Test
    fun foldersAlwaysLeadAndStaySortedByName() {
        val items = tree().items(tree().initialFolderKey)

        val ascending = sortFolderBooksItems(items, FolderBooksSortMode.SIZE, ascending = true)
        val descending = sortFolderBooksItems(items, FolderBooksSortMode.SIZE, ascending = false)

        assertEquals(listOf("Comics", "Novels"), ascending.take(2).map { it.name })
        assertEquals(listOf("Comics", "Novels"), descending.take(2).map { it.name })
    }

    @Test
    fun nameSortFollowsDirection() {
        val books = booksAt(tree())

        assertEquals(
            listOf("Delta.txt", "Zulu.mobi"),
            sortFolderBooksItems(books, FolderBooksSortMode.NAME, ascending = true)
                .filterNot { it.isFolder }
                .map { it.name }
        )
        assertEquals(
            listOf("Zulu.mobi", "Delta.txt"),
            sortFolderBooksItems(books, FolderBooksSortMode.NAME, ascending = false)
                .filterNot { it.isFolder }
                .map { it.name }
        )
    }

    @Test
    fun sizeAndTimeSortByValueAndKeepNameTieBreak() {
        val books = booksAt(tree())

        assertEquals(
            listOf("Zulu.mobi", "Delta.txt"),
            sortedBookNames(books, FolderBooksSortMode.SIZE, ascending = true)
        )
        assertEquals(
            listOf("Delta.txt", "Zulu.mobi"),
            sortedBookNames(books, FolderBooksSortMode.SIZE, ascending = false)
        )
        assertEquals(
            listOf("Zulu.mobi", "Delta.txt"),
            sortedBookNames(books, FolderBooksSortMode.TIME, ascending = false)
        )
    }

    @Test
    fun formatSortGroupsExtensionsAndBreaksTiesByNameAscending() {
        val tree = tree()
        val novels = tree.items(tree.initialFolderKey).first { it.name == "Novels" }.folder!!
        val items = tree.items(novels.key)

        assertEquals(
            listOf("Alpha.epub", "Omega.epub", "Beta.mobi"),
            sortedBookNames(items, FolderBooksSortMode.FORMAT, ascending = true)
        )
        // The format direction flips, but books sharing a format keep the name tie-break ascending.
        assertEquals(
            listOf("Beta.mobi", "Alpha.epub", "Omega.epub"),
            sortedBookNames(items, FolderBooksSortMode.FORMAT, ascending = false)
        )
    }

    @Test
    fun searchStaysInsideTheRequestedSubtreeAndReportsRelativePaths() {
        val tree = tree()
        val rootKey = tree.initialFolderKey

        val roots = tree.searchSubtree(rootKey, "lph")
        assertEquals(1, roots.size)
        assertEquals("Alpha.epub", roots.single().file.name)
        assertEquals(listOf("Novels"), roots.single().relativePath)

        val direct = tree.searchSubtree(rootKey, "DeltA")
        assertEquals(1, direct.size)
        assertTrue(direct.single().relativePath.isEmpty())

        val novels = tree.items(rootKey).first { it.name == "Novels" }.folder!!
        assertEquals(1, tree.searchSubtree(novels.key, "lph").size)
        assertTrue(tree.searchSubtree(novels.key, "Delta").isEmpty())
    }

    @Test
    fun searchIgnoresBlankQueriesAndMissingMatches() {
        val tree = tree()

        assertTrue(tree.searchSubtree(tree.initialFolderKey, "   ").isEmpty())
        assertTrue(tree.searchSubtree(tree.initialFolderKey, "nothing-here").isEmpty())
    }

    @Test
    fun searchHitsUseTheSameSortingRule() {
        val tree = tree()
        val hits = tree.searchSubtree(tree.initialFolderKey, ".")

        val bySizeDescending = sortFolderBooksSearchHits(
            hits,
            FolderBooksSortMode.SIZE,
            ascending = false
        )

        assertEquals(
            listOf("Delta.txt", "Alpha.epub", "Omega.epub", "Gamma.pdf", "Zulu.mobi", "Beta.mobi"),
            bySizeDescending.map { it.file.name }
        )
    }

    private fun tree() = buildAuthorizedFolderTree(files = FILES, folders = FOLDERS)

    private fun booksAt(tree: AuthorizedFolderTree) = tree.items(tree.initialFolderKey)

    private fun sortedBookNames(
        items: List<FolderBooksItem>,
        mode: FolderBooksSortMode,
        ascending: Boolean
    ) = sortFolderBooksItems(items, mode, ascending)
        .filterNot { it.isFolder }
        .map { it.name }

    private companion object {
        const val TREE = "content://provider/tree/lumi"

        val FOLDERS = listOf(
            AuthorizedFolderFolder(treeUri = TREE, name = "lumi", relativePath = null),
            AuthorizedFolderFolder(treeUri = TREE, name = "Novels", relativePath = "Novels"),
            AuthorizedFolderFolder(treeUri = TREE, name = "Comics", relativePath = "Comics")
        )

        val FILES = listOf(
            file("Alpha.epub", "Novels", size = 300L, lastModified = 3L),
            file("Omega.epub", "Novels", size = 250L, lastModified = 5L),
            file("Beta.mobi", "Novels", size = 100L, lastModified = 1L),
            file("Gamma.pdf", "Comics", size = 200L, lastModified = 2L),
            file("Delta.txt", null, size = 400L, lastModified = 4L),
            file("Zulu.mobi", null, size = 150L, lastModified = 6L)
        )

        fun file(
            name: String,
            relativeDirectory: String?,
            size: Long,
            lastModified: Long
        ) = FolderBooksFile(
            key = "file:content://provider/document/$name",
            name = name,
            treeUri = TREE,
            folderName = "lumi",
            relativeDirectory = relativeDirectory,
            size = size,
            lastModified = lastModified
        )
    }
}
