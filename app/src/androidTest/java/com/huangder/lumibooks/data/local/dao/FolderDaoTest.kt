package com.huangder.lumibooks.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: FolderDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.folderDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun siblingNamesAreCaseInsensitiveButSameNameIsAllowedAtAnotherLevel() = runBlocking {
        val root = folder("root", "Science Fiction", "science fiction")
        assertNotNull(dao.createFolderIfAvailable(root))
        assertNull(
            dao.createFolderIfAvailable(folder("duplicate", "SCIENCE FICTION", "science fiction"))
        )

        assertNotNull(
            dao.createFolderIfAvailable(
                folder("child", "Science Fiction", "science fiction", parentId = root.id)
            )
        )
    }

    @Test
    fun batchMoveReplacesOwnershipAndCanReturnBooksToRoot() = runBlocking {
        val first = folder("first", "First", "first")
        val second = folder("second", "Second", "second")
        dao.createFolderIfAvailable(first)
        dao.createFolderIfAvailable(second)
        insertBook("book-1")
        insertBook("book-2")

        dao.moveBooks(setOf("book-1", "book-2"), first.id)
        dao.moveBooks(setOf("book-1"), second.id)

        assertEquals(
            mapOf("book-1" to second.id, "book-2" to first.id),
            dao.getAllBookFolderLinks().first().associate { it.bookId to it.folderId }
        )

        dao.moveBooks(setOf("book-1", "book-2"), null)
        assertEquals(emptyList<Any>(), dao.getAllBookFolderLinks().first())
    }

    @Test
    fun deletingFolderTreeKeepsBooksAndRemovesFolderLinks() = runBlocking {
        val root = folder("root", "Root", "root")
        val child = folder("child", "Child", "child", parentId = root.id)
        dao.createFolderIfAvailable(root)
        dao.createFolderIfAvailable(child)
        insertBook("book-1")
        dao.moveBooks(setOf("book-1"), child.id)

        dao.deleteFolder(root.id)

        assertEquals(emptyList<Any>(), dao.getAllFolders().first())
        assertEquals(emptyList<Any>(), dao.getAllBookFolderLinks().first())
        assertNotNull(database.bookDao().getBookById("book-1"))
    }

    private suspend fun insertBook(id: String) {
        database.bookDao().insertBook(
            BookEntity(
                id = id,
                title = id,
                author = "Author",
                filePath = "/$id.epub",
                coverPath = null,
                format = "EPUB",
                lastReadTime = 0L,
                readingProgress = 0f,
                createdAt = 0L
            )
        )
    }

    private fun folder(
        id: String,
        name: String,
        normalizedName: String,
        parentId: String? = null
    ) = FolderEntity(
        id = id,
        name = name,
        normalizedName = normalizedName,
        parentId = parentId,
        createdAt = 0L
    )
}
