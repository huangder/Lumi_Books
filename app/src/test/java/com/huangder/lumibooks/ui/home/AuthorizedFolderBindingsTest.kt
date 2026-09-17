package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.util.AuthorizedFolderDirectorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedFolderBindingsTest {
    @Test
    fun bindingsFollowTheRootToNestedDirectoryOrder() {
        val directories = listOf(
            directory(relativePath = null, name = "Books", uri = "$TREE/root"),
            directory(relativePath = "Novels", name = "Novels", uri = "$TREE/novels"),
            directory(relativePath = "Novels/Short", name = "Short", uri = "$TREE/short")
        )

        val bindings = authorizedStorageBindingsByPath(TREE, directories)

        assertEquals(listOf("Books"), bindings[""].orEmpty().map { it.name })
        assertEquals(listOf("Books", "Novels"), bindings["Novels"].orEmpty().map { it.name })
        assertEquals(
            listOf("Books", "Novels", "Short"),
            bindings["Novels/Short"].orEmpty().map { it.name }
        )
        assertEquals(
            listOf("$TREE/root", "$TREE/novels", "$TREE/short"),
            bindings["Novels/Short"].orEmpty().map { it.documentUri }
        )
        assertTrue(bindings["Novels/Short"].orEmpty().all { it.treeUri == TREE })
    }

    @Test
    fun unknownDirectoryPathResolvesToNoBindings() {
        val directories = listOf(
            directory(relativePath = null, name = "Books", uri = "$TREE/root")
        )

        val bindings = authorizedStorageBindingsByPath(TREE, directories)

        assertEquals(listOf("Books"), bindings[""].orEmpty().map { it.name })
        assertEquals(null, bindings["Missing"])
    }

    @Test
    fun directoriesWithoutRootProduceNoBindings() {
        val directories = listOf(
            directory(relativePath = "Novels", name = "Novels", uri = "$TREE/novels")
        )

        assertTrue(authorizedStorageBindingsByPath(TREE, directories).isEmpty())
    }

    private fun directory(
        relativePath: String?,
        name: String,
        uri: String
    ) = AuthorizedFolderDirectorySnapshot(
        documentUri = uri,
        parentDocumentUri = null,
        name = name,
        relativePath = relativePath
    )

    private companion object {
        const val TREE = "content://provider/tree/books"
    }
}
