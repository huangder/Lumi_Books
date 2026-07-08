package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.huangder.lumibooks.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 随机摘录投影 — Widget 查询用
 */
data class RandomNoteWithBook(
    val selectedText: String,
    val note: String,
    val bookTitle: String,
    val bookAuthor: String
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getNotesByBookId(bookId: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE bookId = :bookId")
    suspend fun deleteAllNotesByBookId(bookId: String)

    /** 随机获取一条摘录及其书名作者，用于桌面小组件 */
    @Query("""
        SELECT notes.selectedText, notes.note, books.title AS bookTitle, books.author AS bookAuthor
        FROM notes
        INNER JOIN books ON notes.bookId = books.id
        ORDER BY RANDOM() LIMIT 1
    """)
    suspend fun getRandomNoteWithBook(): RandomNoteWithBook?
}
