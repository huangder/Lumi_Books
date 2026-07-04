package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.data.local.dao.BookDuration
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.ReadingRecord
import kotlinx.coroutines.flow.Flow

interface ReadingRepository {
    fun getRecordsByDate(date: String): Flow<List<ReadingRecord>>
    fun getRecordsBetweenDates(startDate: String, endDate: String): Flow<List<ReadingRecord>>
    fun getTotalDurationByDate(date: String): Flow<Long?>
    fun getTotalDurationBetweenDates(startDate: String, endDate: String): Flow<Long?>
    suspend fun insertRecord(record: ReadingRecord)
    suspend fun getRecordByBookAndDate(bookId: String, date: String): ReadingRecord?
    suspend fun updateRecordDuration(recordId: Long, additionalDuration: Long, endTime: Long)
    fun getBookmarksByBookId(bookId: String): Flow<List<Bookmark>>
    suspend fun insertBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(bookmark: Bookmark)
    fun getNotesByBookId(bookId: String): Flow<List<Note>>
    suspend fun insertNote(note: Note)
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(note: Note)
    fun getMostReadBooks(limit: Int = 5): Flow<List<BookDuration>>
}
