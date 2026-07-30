package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.Update
import com.huangder.lumibooks.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: String): BookEntity?

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Upsert
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteBookmarksByBookId(bookId: String)

    @Query("DELETE FROM notes WHERE bookId = :bookId")
    suspend fun deleteNotesByBookId(bookId: String)

    @Query("DELETE FROM reading_records WHERE bookId = :bookId")
    suspend fun deleteReadingRecordsByBookId(bookId: String)

    @Query("DELETE FROM book_tag_cross_refs WHERE bookId = :bookId")
    suspend fun deleteTagLinksByBookId(bookId: String)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: String)

    @Transaction
    suspend fun deleteBookWithRelatedData(bookId: String) {
        deleteBookmarksByBookId(bookId)
        deleteNotesByBookId(bookId)
        deleteReadingRecordsByBookId(bookId)
        deleteTagLinksByBookId(bookId)
        deleteBookById(bookId)
    }

    @Query("UPDATE books SET lastReadTime = :timestamp WHERE id = :bookId")
    suspend fun updateLastReadTime(bookId: String, timestamp: Long)

    @Query("UPDATE books SET readingProgress = :progress, locatorJson = COALESCE(:locatorJson, locatorJson) WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: String, progress: Float, locatorJson: String?)
}
