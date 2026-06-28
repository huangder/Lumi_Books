package com.ebook.reader.domain.repository

import com.ebook.reader.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun searchBooks(query: String): Flow<List<Book>>
    suspend fun getBookById(bookId: String): Book?
    suspend fun insertBook(book: Book)
    suspend fun updateBook(book: Book)
    suspend fun deleteBook(book: Book)
    suspend fun updateLastReadTime(bookId: String, timestamp: Long)
    suspend fun updateReadingProgress(bookId: String, progress: Float)
}
