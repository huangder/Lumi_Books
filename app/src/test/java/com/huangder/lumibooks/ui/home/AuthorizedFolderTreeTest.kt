package com.huangder.lumibooks.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedFolderTreeTest {
    @Test
    fun singleRootOpensDirectlyAndListsFoldersBeforeBooks() {
        val tree = buildAuthorizedFolderTree(
            files = listOf(
                file("b.epub", relativeDirectory = null),
                file("a.epub", relativeDirectory = null)
            ),
            folders = listOf(
                folder("lumi", relativePath = null),
                folder("Novels", relativePath = "Novels")
            )
        )

        assertEquals(authorizedFolderDirectoryKey(TREE, null), tree.initialFolderKey)
        val items = tree.items(tree.initialFolderKey)
        assertEquals(listOf("Novels", "a.epub", "b.epub"), items.map { it.name })
        assertTrue(items.first().isFolder)
        assertFalse(items[1].isFolder)
        assertEquals(listOf("lumi"), tree.breadcrumb(tree.initialFolderKey).map { it.name })
    }

    @Test
    fun severalRootsStartOnAVirtualRootAndCloseWhenGoingUp() {
        val tree = buildAuthorizedFolderTree(
            files = listOf(file("a.epub", treeUri = TREE)),
            folders = listOf(
                folder("lumi", relativePath = null),
                folder("Others", relativePath = null, treeUri = OTHER_TREE)
            )
        )

        assertEquals(VIRTUAL_ROOT_KEY, tree.initialFolderKey)
        assertNull(tree.parentOf(VIRTUAL_ROOT_KEY))
        assertEquals(listOf("lumi", "Others"), tree.items(VIRTUAL_ROOT_KEY).map { it.name })
        val rootKey = tree.items(VIRTUAL_ROOT_KEY).first().folder!!.key
        assertEquals(VIRTUAL_ROOT_KEY, tree.parentOf(rootKey))
        assertEquals(listOf("a.epub"), tree.items(rootKey).map { it.name })
    }

    @Test
    fun nestedFoldersKeepBreadcrumbsAndRecursiveBookCounts() {
        val tree = buildAuthorizedFolderTree(
            files = listOf(
                file("deep.epub", relativeDirectory = "分类/小说"),
                file("root.epub", relativeDirectory = null)
            ),
            folders = listOf(
                folder("lumi", relativePath = null),
                folder("分类", relativePath = "分类"),
                folder("小说", relativePath = "分类/小说"),
                folder("空目录", relativePath = "空目录")
            )
        )

        val rootItems = tree.items(tree.initialFolderKey)
        val category = rootItems.first { it.name == "分类" }.folder!!
        val novels = tree.items(category.key).single().folder!!
        val empty = rootItems.first { it.name == "空目录" }

        assertEquals(1, category.bookCount)
        assertEquals(1, novels.bookCount)
        assertEquals(0, empty.folder!!.bookCount)
        assertTrue(tree.items(empty.folder!!.key).isEmpty())
        assertEquals(listOf("分类", "空目录", "root.epub"), rootItems.map { it.name })
        assertEquals(2, tree.folder(tree.initialFolderKey)!!.bookCount)
        assertNull(tree.parentOf(tree.initialFolderKey))
        assertEquals(listOf("lumi", "分类", "小说"), tree.breadcrumb(novels.key).map { it.name })
    }

    @Test
    fun foldersMissingFromTheScanAreSynthesizedForTheirBooks() {
        val tree = buildAuthorizedFolderTree(
            files = listOf(file("orphan.epub", relativeDirectory = "缺失/深层")),
            folders = listOf(folder("lumi", relativePath = null))
        )

        val rootItems = tree.items(tree.initialFolderKey)
        val synthesized = rootItems.single().folder!!
        assertEquals("缺失", synthesized.name)
        assertEquals(1, synthesized.bookCount)
        val deepest = tree.items(synthesized.key).single()
        assertEquals("深层", deepest.name)
        assertEquals("orphan.epub", tree.items(deepest.folder!!.key).single().name)
    }

    @Test
    fun refreshFallsBackToTheNearestSurvivingFolder() {
        val tree = buildAuthorizedFolderTree(
            files = emptyList(),
            folders = listOf(
                folder("lumi", relativePath = null),
                folder("分类", relativePath = "分类")
            )
        )

        assertEquals(
            authorizedFolderDirectoryKey(TREE, "分类"),
            tree.resolve(authorizedFolderDirectoryKey(TREE, "分类/已删除"))
        )
        assertEquals(
            authorizedFolderDirectoryKey(TREE, null),
            tree.resolve(authorizedFolderDirectoryKey(OTHER_TREE, "不存在"))
        )
        assertEquals(tree.initialFolderKey, tree.resolve(null))
    }

    private fun file(
        name: String,
        relativeDirectory: String? = null,
        treeUri: String = TREE
    ) = FolderBooksFile(
        key = "file:content://provider/document/$name",
        name = name,
        treeUri = treeUri,
        folderName = "lumi",
        relativeDirectory = relativeDirectory,
        size = 1024L,
        lastModified = 1L
    )

    private fun folder(
        name: String,
        relativePath: String?,
        treeUri: String = TREE
    ) = AuthorizedFolderFolder(
        treeUri = treeUri,
        name = name,
        relativePath = relativePath
    )

    private companion object {
        const val TREE = "content://provider/tree/lumi"
        const val OTHER_TREE = "content://provider/tree/others"
    }
}
