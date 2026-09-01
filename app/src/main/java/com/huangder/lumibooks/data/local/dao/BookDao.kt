package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.Update
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.model.ContinueReadingWidgetData
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    suspend fun getAllBooksSnapshot(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: String): BookEntity?

    @Query("SELECT * FROM books WHERE sourceDocumentKey = :key LIMIT 1")
    suspend fun getBookBySourceDocumentKey(key: String): BookEntity?

    @Query("SELECT * FROM books WHERE sourceUri = :uri LIMIT 1")
    suspend fun getBookBySourceUri(uri: String): BookEntity?

    @Query("SELECT * FROM books WHERE sourceSha256 = :sha256")
    suspend fun getBooksBySourceSha256(sha256: String): List<BookEntity>

    @Query(
        "SELECT id AS bookId, title, author, coverPath, readingProgress " +
            "FROM books ORDER BY lastReadTime DESC LIMIT 1"
    )
    suspend fun getContinueReadingWidgetData(): ContinueReadingWidgetData?

    @Query(
        "SELECT id AS bookId, title, author, coverPath, readingProgress " +
            "FROM books ORDER BY lastReadTime DESC LIMIT 1"
    )
    fun observeContinueReadingWidgetData(): Flow<ContinueReadingWidgetData?>

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Upsert
    suspend fun insertBook(book: BookEntity)

    @Upsert
    suspend fun upsertBooks(books: List<BookEntity>)

    @Query("DELETE FROM books")
    suspend fun clearBooks()

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET sourceSha256 = :sha256 WHERE id = :bookId")
    suspend fun updateSourceSha256(bookId: String, sha256: String)

    @Query("UPDATE books SET filePath = '', isCloudOnly = 1 WHERE id = :bookId")
    suspend fun markBookCloudOnly(bookId: String)

    @Query("UPDATE books SET filePath = :filePath, isCloudOnly = 0 WHERE id = :bookId")
    suspend fun markBookDownloaded(bookId: String, filePath: String)

    @Query(
        "UPDATE books SET isCloudOnly = 0, remoteLibraryKey = NULL, remoteFileName = NULL, " +
            "remoteFileSize = 0, remoteFileSha256 = NULL WHERE id = :bookId"
    )
    suspend fun clearRemoteAssociation(bookId: String)

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
