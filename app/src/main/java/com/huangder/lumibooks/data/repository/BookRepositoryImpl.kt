package com.ebook.reader.data.repository

import com.ebook.reader.data.local.dao.BookDao
import com.ebook.reader.data.local.entity.BookEntity
import com.ebook.reader.domain.model.Book
import com.ebook.reader.domain.model.BookFormat
import com.ebook.reader.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> {
        return bookDao.getAllBooks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchBooks(query: String): Flow<List<Book>> {
        return bookDao.searchBooks(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getBookById(bookId: String): Book? {
        return bookDao.getBookById(bookId)?.toDomain()
    }

    override suspend fun insertBook(book: Book) {
        bookDao.insertBook(book.toEntity())
    }

    override suspend fun updateBook(book: Book) {
        bookDao.updateBook(book.toEntity())
    }

    override suspend fun deleteBook(book: Book) {
        bookDao.deleteBook(book.toEntity())
    }

    override suspend fun updateLastReadTime(bookId: String, timestamp: Long) {
        bookDao.updateLastReadTime(bookId, timestamp)
    }

    override suspend fun updateReadingProgress(bookId: String, progress: Float) {
        bookDao.updateReadingProgress(bookId, progress)
    }

    private fun BookEntity.toDomain(): Book {
        return Book(
            id = id,
            title = title,
            author = author,
            filePath = filePath,
            coverPath = coverPath,
            format = BookFormat.valueOf(format),
            lastReadTime = lastReadTime,
            readingProgress = readingProgress,
            createdAt = createdAt
        )
    }

    private fun Book.toEntity(): BookEntity {
        return BookEntity(
            id = id,
            title = title,
            author = author,
            filePath = filePath,
            coverPath = coverPath,
            format = format.name,
            lastReadTime = lastReadTime,
            readingProgress = readingProgress,
            createdAt = createdAt
        )
    }
}
