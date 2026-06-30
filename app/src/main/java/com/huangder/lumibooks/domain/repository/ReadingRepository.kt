package com.ebook.reader.domain.repository

import com.ebook.reader.data.local.dao.BookDuration
import com.ebook.reader.domain.model.Bookmark
import com.ebook.reader.domain.model.Note
import com.ebook.reader.domain.model.ReadingRecord
import kotlinx.coroutines.flow.Flow

interface ReadingRepository {
    fun getRecordsByDate(date: String): Flow<List<ReadingRecord>>
    fun getRecordsBetweenDates(startDate: String, endDate: String): Flow<List<ReadingRecord>>
    fun getTotalDurationByDate(date: String): Flow<Long?>
    fun getTotalDurationBetweenDates(startDate: String, endDate: String): Flow<Long?>
    suspend fun insertRecord(record: ReadingRecord)
    suspend fun updateRecordDuration(recordId: Long, additionalDuration: Long, endTime: Long)
    fun getBookmarksByBookId(bookId: String): Flow<List<Bookmark>>
    suspend fun insertBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(bookmark: Bookmark)
    fun getNotesByBookId(bookId: String): Flow<List<Note>>
    suspend fun insertNote(note: Note)
    suspend fun deleteNote(note: Note)
    fun getMostReadBooks(limit: Int = 5): Flow<List<BookDuration>>
}
