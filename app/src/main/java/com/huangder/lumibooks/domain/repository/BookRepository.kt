package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun searchBooks(query: String): Flow<List<Book>>
    suspend fun getBookById(bookId: String): Book?
    suspend fun getBookBySourceDocumentKey(key: String): Book?
    suspend fun getBookBySourceUri(uri: String): Book?
    suspend fun getBooksBySourceSha256(sha256: String): List<Book>
    suspend fun insertBook(book: Book)
    suspend fun updateBook(book: Book)
    suspend fun updateSourceSha256(bookId: String, sha256: String)
    suspend fun updateBookMetadata(book: Book)
    /** Moves books into or out of the hidden library in one write. */
    suspend fun setBooksHidden(bookIds: Collection<String>, hidden: Boolean)
    suspend fun deleteBook(book: Book)
    suspend fun markBookCloudOnly(bookId: String)
    suspend fun markBookDownloaded(bookId: String, filePath: String)
    suspend fun clearRemoteAssociation(bookId: String)
    suspend fun updateLastReadTime(bookId: String, timestamp: Long)
    suspend fun updateReadingProgress(bookId: String, progress: Float, locatorJson: String? = null)
}
