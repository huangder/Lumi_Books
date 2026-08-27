package com.huangder.lumibooks.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderCoverFileTest {
    private lateinit var context: Context
    private val testFolderIds = listOf("file-test-folder", "file-test-parent", "file-test-child")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testFolderIds.forEach { FileUtils.deleteFolderCustomCover(context, it) }
    }

    @After
    fun tearDown() {
        testFolderIds.forEach { FileUtils.deleteFolderCustomCover(context, it) }
        File(context.cacheDir, "folder-cover-source.bin").delete()
    }

    @Test
    fun settingReplacingAndRemovingFolderCoverCleansOwnedFiles() {
        val source = File(context.cacheDir, "folder-cover-source.bin").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val first = requireNotNull(
            FileUtils.copyFolderCoverImage(context, Uri.fromFile(source), "file-test-folder")
        )
        Thread.sleep(2)
        val second = requireNotNull(
            FileUtils.copyFolderCoverImage(context, Uri.fromFile(source), "file-test-folder")
        )

        assertNotEquals(first, second)
        assertTrue(File(first).exists())
        assertTrue(File(second).exists())

        FileUtils.deleteOtherFolderCustomCovers(context, "file-test-folder", second)
        assertFalse(File(first).exists())
        assertTrue(File(second).exists())

        FileUtils.deleteFolderCustomCover(context, "file-test-folder")
        assertFalse(File(second).exists())
    }

    @Test
    fun recursiveDeleteCoverPathsRemovesEveryReturnedTreeCover() {
        val coversDirectory = FileUtils.getCoversDirectory(context)
        val parent = File(coversDirectory, "folder_custom_file-test-parent_1.jpg").apply {
            writeBytes(byteArrayOf(1))
        }
        val child = File(coversDirectory, "folder_custom_file-test-child_1.jpg").apply {
            writeBytes(byteArrayOf(2))
        }

        FileUtils.deleteFolderCoverPaths(context, listOf(parent.absolutePath, child.absolutePath))

        assertFalse(parent.exists())
        assertFalse(child.exists())
    }
}
