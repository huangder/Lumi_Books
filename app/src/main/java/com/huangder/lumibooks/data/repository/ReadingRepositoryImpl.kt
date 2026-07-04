package com.huangder.lumibooks.data.repository

import com.huangder.lumibooks.data.local.dao.BookDuration
import com.huangder.lumibooks.data.local.dao.BookmarkDao
import com.huangder.lumibooks.data.local.dao.NoteDao
import com.huangder.lumibooks.data.local.dao.ReadingRecordDao
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.ReadingRecord
import com.huangder.lumibooks.domain.repository.ReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReadingRepositoryImpl @Inject constructor(
    private val readingRecordDao: ReadingRecordDao,
    private val bookmarkDao: BookmarkDao,
    private val noteDao: NoteDao
) : ReadingRepository {

    override fun getRecordsByDate(date: String): Flow<List<ReadingRecord>> {
        return readingRecordDao.getRecordsByDate(date).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRecordsBetweenDates(startDate: String, endDate: String): Flow<List<ReadingRecord>> {
        return readingRecordDao.getRecordsBetweenDates(startDate, endDate).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTotalDurationByDate(date: String): Flow<Long?> {
        return readingRecordDao.getTotalDurationByDate(date)
    }

    override fun getTotalDurationBetweenDates(startDate: String, endDate: String): Flow<Long?> {
        return readingRecordDao.getTotalDurationBetweenDates(startDate, endDate)
    }

    override suspend fun insertRecord(record: ReadingRecord) {
        readingRecordDao.insertRecord(record.toEntity())
    }

    override suspend fun getRecordByBookAndDate(bookId: String, date: String): ReadingRecord? {
        return readingRecordDao.getRecordByBookAndDate(bookId, date)?.toDomain()
    }

    override suspend fun updateRecordDuration(recordId: Long, additionalDuration: Long, endTime: Long) {
        readingRecordDao.updateRecordDuration(recordId, additionalDuration, endTime)
    }

    override fun getBookmarksByBookId(bookId: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksByBookId(bookId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertBookmark(bookmark: Bookmark) {
        bookmarkDao.insertBookmark(bookmark.toEntity())
    }

    override suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.deleteBookmark(bookmark.toEntity())
    }

    override fun getNotesByBookId(bookId: String): Flow<List<Note>> {
        return noteDao.getNotesByBookId(bookId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertNote(note: Note) {
        noteDao.insertNote(note.toEntity())
    }

    override suspend fun updateNote(note: Note) {
        noteDao.updateNote(note.toEntity())
    }

    override suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note.toEntity())
    }

    override fun getMostReadBooks(limit: Int): Flow<List<BookDuration>> {
        return readingRecordDao.getMostReadBooks(limit)
    }

    private fun ReadingRecordEntity.toDomain(): ReadingRecord {
        return ReadingRecord(
            id = id,
            bookId = bookId,
            date = date,
            duration = duration,
            startTime = startTime,
            endTime = endTime
        )
    }

    private fun ReadingRecord.toEntity(): ReadingRecordEntity {
        return ReadingRecordEntity(
            id = id,
            bookId = bookId,
            date = date,
            duration = duration,
            startTime = startTime,
            endTime = endTime
        )
    }

    private fun BookmarkEntity.toDomain(): Bookmark {
        return Bookmark(
            id = id,
            bookId = bookId,
            chapterIndex = chapterIndex,
            position = position,
            title = title,
            createdAt = createdAt
        )
    }

    private fun Bookmark.toEntity(): BookmarkEntity {
        return BookmarkEntity(
            id = id,
            bookId = bookId,
            chapterIndex = chapterIndex,
            position = position,
            title = title,
            createdAt = createdAt
        )
    }

    private fun NoteEntity.toDomain(): Note {
        return Note(
            id = id,
            bookId = bookId,
            chapterIndex = chapterIndex,
            startPosition = startPosition,
            endPosition = endPosition,
            selectedText = selectedText,
            note = note,
            color = color,
            createdAt = createdAt
        )
    }

    private fun Note.toEntity(): NoteEntity {
        return NoteEntity(
            id = id,
            bookId = bookId,
            chapterIndex = chapterIndex,
            startPosition = startPosition,
            endPosition = endPosition,
            selectedText = selectedText,
            note = note,
            color = color,
            createdAt = createdAt
        )
    }
}
