package com.huangder.lumibooks.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationEditTransactionTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun applyAnnotationEditDeletesUpdatesAndInsertsInOneOperation() = runBlocking {
        val dao = database.noteDao()
        dao.insertNote(note(id = 1, start = 0, end = 10, color = "red"))
        dao.insertNote(note(id = 2, start = 12, end = 20, color = "green"))

        dao.applyAnnotationEdit(
            deleteIds = listOf(2),
            updates = listOf(note(id = 1, start = 0, end = 5, color = "red")),
            inserts = listOf(note(id = 0, start = 5, end = 20, color = "blue"))
        )

        val result = dao.getNotesByBookId("book").first().sortedBy { it.startPosition }
        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals(0, result[0].startPosition)
        assertEquals(5, result[0].endPosition)
        assertEquals("red", result[0].color)
        assertEquals(5, result[1].startPosition)
        assertEquals(20, result[1].endPosition)
        assertEquals("blue", result[1].color)
    }

    private fun note(id: Long, start: Int, end: Int, color: String) = NoteEntity(
        id = id,
        bookId = "book",
        chapterIndex = 0,
        startPosition = start,
        endPosition = end,
        selectedText = "$start-$end",
        note = "",
        color = color,
        createdAt = id.coerceAtLeast(1),
        type = "highlight"
    )
}
