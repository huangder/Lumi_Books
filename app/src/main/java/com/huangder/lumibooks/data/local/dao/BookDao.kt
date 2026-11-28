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

private const val WEBDAV_AVAILABLE = "(filePath != '' OR NOT EXISTS " +
    "(SELECT 1 FROM sync_state WHERE `key` = 'webdav_suppressed:' || books.id))"

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE isHidden = 0 AND isArchived = 0 AND " + WEBDAV_AVAILABLE + " ORDER BY lastReadTime DESC")
    fun observeVisibleBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id AND isHidden = 0 AND isArchived = 0 AND " + WEBDAV_AVAILABLE)
    suspend fun getVisibleBookById(id: String): BookEntity?

    /** Rejects stale external-reader writes and books hidden since the snapshot was loaded. */
    @Query("UPDATE books SET readingProgress = :progress, locatorJson = :locator, lastReadTime = :timestamp " +
        "WHERE id = :id AND isHidden = 0 AND isArchived = 0 AND readingProgress = :expectedProgress " +
        "AND ((locatorJson IS NULL AND :expectedLocator IS NULL) OR locatorJson = :expectedLocator)")
    suspend fun compareAndSetVisibleReadingProgress(
        id: String, expectedProgress: Float, expectedLocator: String?,
        progress: Float, locator: String, timestamp: Long
    ): Int

    @Query("SELECT * FROM books WHERE " + WEBDAV_AVAILABLE + " ORDER BY lastReadTime DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    suspend fun getAllBooksSnapshot(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: String): BookEntity?

    @Query("UPDATE books SET coverPath = :newPath WHERE id = :bookId AND ((coverPath IS NULL AND :oldPath IS NULL) OR coverPath = :oldPath)")
    suspend fun updateCoverIfUnchanged(bookId: String, oldPath: String?, newPath: String): Int

    @Query("SELECT * FROM books WHERE sourceDocumentKey = :key LIMIT 1")
    suspend fun getBookBySourceDocumentKey(key: String): BookEntity?

    @Query("SELECT * FROM books WHERE sourceUri = :uri LIMIT 1")
    suspend fun getBookBySourceUri(uri: String): BookEntity?

    @Query("SELECT * FROM books WHERE sourceSha256 = :sha256")
    suspend fun getBooksBySourceSha256(sha256: String): List<BookEntity>

    @Query(
        "SELECT id AS bookId, title, author, coverPath, readingProgress " +
            "FROM books WHERE isHidden = 0 AND isArchived = 0 AND " + WEBDAV_AVAILABLE + " ORDER BY lastReadTime DESC LIMIT 1"
    )
    suspend fun getContinueReadingWidgetData(): ContinueReadingWidgetData?

    @Query(
        "SELECT id AS bookId, title, author, coverPath, readingProgress " +
            "FROM books WHERE isHidden = 0 AND isArchived = 0 AND " + WEBDAV_AVAILABLE + " ORDER BY lastReadTime DESC LIMIT 1"
    )
    fun observeContinueReadingWidgetData(): Flow<ContinueReadingWidgetData?>

    @Query("SELECT * FROM books WHERE " + WEBDAV_AVAILABLE +
        " AND (title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%')")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Upsert
    suspend fun insertBook(book: BookEntity)

    @Query("""
        INSERT INTO book_highlight_rule_states (bookId, ruleId, enabled, position)
        SELECT :bookId, state.ruleId, state.enabled, rule.position
        FROM book_highlight_rule_states AS state
        JOIN highlight_rules AS rule ON rule.id = state.ruleId
        WHERE state.bookId = (
            SELECT book.id FROM books AS book
            WHERE book.id != :bookId AND book.isHidden = 0 AND book.isArchived = 0
                AND book.format IN (:eligibleFormats)
                AND EXISTS (
                    SELECT 1 FROM book_highlight_rule_states AS saved
                    JOIN highlight_rules AS existingRule ON existingRule.id = saved.ruleId
                    WHERE saved.bookId = book.id
                )
            ORDER BY book.lastReadTime DESC, book.id
            LIMIT 1
        )
    """)
    suspend fun inheritHighlightRuleSwitches(bookId: String, eligibleFormats: List<String>)

    @Transaction
    suspend fun insertBookWithInheritedHighlightSwitches(book: BookEntity, eligibleFormats: List<String>) {
        insertBook(book)
        inheritHighlightRuleSwitches(book.id, eligibleFormats)
    }

    @Upsert
    suspend fun upsertBooks(books: List<BookEntity>)

    @Query("DELETE FROM books")
    suspend fun clearBooks()

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("""
        UPDATE books SET title = :title, author = :author,
            coverPath = COALESCE(:newCoverPath, coverPath),
            metadataUpdatedAt = MAX(:updatedAt, metadataUpdatedAt + 1)
        WHERE id = :id AND title = :expectedTitle AND author = :expectedAuthor
            AND metadataUpdatedAt = :expectedUpdatedAt AND filePath = :expectedFilePath
            AND sourceUri IS :expectedSourceUri AND isCloudOnly = 0 AND isMissing = 0
            AND sourceDisplayName IS :expectedSourceName AND sourceSha256 IS :expectedSourceHash
            AND sourceLastModified = :expectedSourceModified
            AND (:newCoverPath IS NULL OR coverPath IS :expectedCoverPath)
    """)
    suspend fun compareAndSetBookMetadata(
        id: String,
        expectedTitle: String,
        expectedAuthor: String,
        expectedUpdatedAt: Long,
        expectedFilePath: String,
        expectedSourceUri: String?,
        expectedSourceName: String?,
        expectedSourceHash: String?,
        expectedSourceModified: Long,
        expectedCoverPath: String?,
        title: String,
        author: String,
        newCoverPath: String?,
        updatedAt: Long
    ): Int

    @Query("UPDATE books SET sourceSha256 = :sha256 WHERE id = :bookId")
    suspend fun updateSourceSha256(bookId: String, sha256: String)

    /** Bumps metadataUpdatedAt so WebDAV and backup merges carry the change to other devices. */
    @Query("UPDATE books SET isHidden = :hidden, metadataUpdatedAt = :updatedAt WHERE id IN (:bookIds)")
    suspend fun setBooksHidden(bookIds: List<String>, hidden: Boolean, updatedAt: Long)

    @Query("UPDATE books SET wantToReadAt = :wantToReadAt, metadataUpdatedAt = :updatedAt WHERE id = :bookId")
    suspend fun setWantToRead(bookId: String, wantToReadAt: Long, updatedAt: Long)

    @Query("UPDATE books SET isArchived = :archived, metadataUpdatedAt = :updatedAt WHERE id IN (:bookIds)")
    suspend fun setBooksArchived(bookIds: List<String>, archived: Boolean, updatedAt: Long)

    /** The read flag travels with reading progress, so lastReadTime is its sync timestamp. */
    @Query("UPDATE books SET isManuallyMarkedRead = :read, lastReadTime = :timestamp WHERE id = :bookId")
    suspend fun setManuallyMarkedRead(bookId: String, read: Boolean, timestamp: Long)

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

    @Query("UPDATE books SET readingProgress = :progress, locatorJson = :locatorJson WHERE id = :bookId")
    suspend fun updateReadingPosition(bookId: String, progress: Float, locatorJson: String?)
}
