package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.domain.model.Book
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    /** Includes archived and locally suppressed rows for reconciliation. */
    suspend fun getBooksForWebdav(): List<Book> = getAllBooks().first()
    fun searchBooks(query: String): Flow<List<Book>>
    suspend fun getBookById(bookId: String): Book?
    suspend fun getBookBySourceDocumentKey(key: String): Book?
    suspend fun getBookBySourceUri(uri: String): Book?
    suspend fun getBooksBySourceSha256(sha256: String): List<Book>
    suspend fun insertBook(book: Book)
    suspend fun insertExternalBook(book: Book)
    suspend fun updateBook(book: Book)
    suspend fun updateSourceSha256(bookId: String, sha256: String)
    suspend fun updateBookMetadata(book: Book)
    /** Updates only metadata and returns false if the source or metadata differs from the snapshot. */
    suspend fun compareAndSetBookMetadata(
        expected: Book,
        title: String,
        author: String,
        newCoverPath: String? = null
    ): Boolean
    /** Moves books into or out of the hidden library in one write. */
    suspend fun setBooksHidden(bookIds: Collection<String>, hidden: Boolean)
    /** Archives without changing reading history or shelf organisation. */
    suspend fun setBooksArchived(bookIds: Collection<String>, archived: Boolean)
    /** Changes only the manual read flag; progress and locator stay untouched. */
    suspend fun setManuallyMarkedRead(bookId: String, read: Boolean)
    suspend fun deleteBook(book: Book)
    suspend fun markBookCloudOnly(bookId: String)
    suspend fun markBookDownloaded(bookId: String, filePath: String)
    suspend fun clearRemoteAssociation(bookId: String)
    suspend fun updateLastReadTime(bookId: String, timestamp: Long)
    suspend fun updateReadingProgress(bookId: String, progress: Float, locatorJson: String? = null)
    /** Replaces the saved reading position, including clearing an obsolete locator. */
    suspend fun updateReadingPosition(bookId: String, progress: Float, locatorJson: String?)
}
