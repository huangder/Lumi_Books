package com.huangder.lumibooks.data.repository

import com.huangder.lumibooks.data.local.dao.BookDuration
import com.huangder.lumibooks.data.local.dao.BookmarkDao
import com.huangder.lumibooks.data.local.dao.NoteDao
import com.huangder.lumibooks.data.local.dao.ReadingRecordDao
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.domain.model.AnnotationEditPlan
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.DailyTotal
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.ReadingRecord
import com.huangder.lumibooks.domain.repository.ReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import javax.inject.Inject
import java.util.UUID
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.data.local.dao.SyncStateDao
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity

class ReadingRepositoryImpl @Inject constructor(
    private val readingRecordDao: ReadingRecordDao,
    private val bookmarkDao: BookmarkDao,
    private val noteDao: NoteDao,
    private val syncStateDao: SyncStateDao,
    private val syncIdentityStore: SyncIdentityStore,
    private val database: AppDatabase
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
        val deviceId = record.sourceDeviceId.ifBlank { syncIdentityStore.deviceId() }
        readingRecordDao.insertRecord(record.copy(sourceDeviceId = deviceId).toEntity())
    }

    override suspend fun getRecordByBookAndDate(bookId: String, date: String): ReadingRecord? {
        return readingRecordDao.getRecordByBookAndDate(
            bookId,
            date,
            syncIdentityStore.deviceId()
        )?.toDomain()
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
        val now = System.currentTimeMillis()
        bookmarkDao.insertBookmark(
            bookmark.copy(
                syncId = bookmark.syncId.ifBlank { UUID.randomUUID().toString() },
                updatedAt = maxOf(bookmark.updatedAt, now)
            ).toEntity()
        )
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarkDao.updateBookmark(bookmark.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    override suspend fun deleteBookmark(bookmark: Bookmark) {
        val syncId = bookmark.syncId.ifBlank { "legacy-bookmark-${bookmark.id}" }
        syncStateDao.upsertTombstones(
            listOf(SyncTombstoneEntity("bookmark", syncId, System.currentTimeMillis(), syncIdentityStore.deviceId()))
        )
        bookmarkDao.deleteBookmark(bookmark.toEntity())
    }

    override suspend fun deleteAllBookmarksByBookId(bookId: String) {
        bookmarkDao.deleteAllBookmarksByBookId(bookId)
    }

    override fun getNotesByBookId(bookId: String): Flow<List<Note>> {
        return noteDao.getNotesByBookId(bookId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertNote(note: Note) {
        val now = System.currentTimeMillis()
        noteDao.insertNote(
            note.copy(
                syncId = note.syncId.ifBlank { UUID.randomUUID().toString() },
                updatedAt = maxOf(note.updatedAt, now)
            ).toEntity()
        )
    }

    override suspend fun updateNote(note: Note) {
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    override suspend fun deleteNote(note: Note) {
        val syncId = note.syncId.ifBlank { "legacy-note-${note.id}" }
        syncStateDao.upsertTombstones(
            listOf(SyncTombstoneEntity("note", syncId, System.currentTimeMillis(), syncIdentityStore.deviceId()))
        )
        noteDao.deleteNote(note.toEntity())
    }

    override suspend fun applyAnnotationEdit(plan: AnnotationEditPlan) {
        val now = System.currentTimeMillis()
        val deviceId = syncIdentityStore.deviceId()
        database.withTransaction {
            val deleted = plan.deletes.filter { it.id > 0L && it.syncId.isNotBlank() }
            if (deleted.isNotEmpty()) {
                syncStateDao.upsertTombstones(
                    deleted.map { SyncTombstoneEntity("note", it.syncId, now, deviceId) }
                )
            }
            noteDao.applyAnnotationEdit(
                deleteIds = plan.deletes.map { it.id }.filter { it > 0L },
                updates = plan.updates.map { it.copy(updatedAt = now).toEntity() },
                inserts = plan.inserts.map {
                    it.copy(
                        id = 0,
                        syncId = UUID.randomUUID().toString(),
                        updatedAt = now
                    ).toEntity()
                }
            )
        }
    }

    override suspend fun deleteAllNotesByBookId(bookId: String) {
        noteDao.deleteAllNotesByBookId(bookId)
    }

    override suspend fun deleteLegacyPdfAnnotationsByBookId(bookId: String) {
        noteDao.deleteLegacyPdfAnnotationsByBookId(bookId)
    }

    override fun getMostReadBooks(limit: Int): Flow<List<BookDuration>> {
        return readingRecordDao.getMostReadBooks(limit)
    }

    override fun getDailyTotalsBetween(startDate: String, endDate: String): Flow<List<DailyTotal>> {
        return readingRecordDao.getDailyTotalsBetween(startDate, endDate)
    }

    override fun getTotalDurationByBookId(bookId: String): Flow<Long?> =
        readingRecordDao.getTotalDurationByBookId(bookId)

    override fun getActiveDaysByBookId(bookId: String): Flow<Int> =
        readingRecordDao.getActiveDaysByBookId(bookId)

    private fun ReadingRecordEntity.toDomain(): ReadingRecord {
        return ReadingRecord(
            id = id,
            bookId = bookId,
            date = date,
            duration = duration,
            startTime = startTime,
            endTime = endTime,
            sourceDeviceId = sourceDeviceId,
            updatedAt = updatedAt
        )
    }

    private fun ReadingRecord.toEntity(): ReadingRecordEntity {
        return ReadingRecordEntity(
            id = id,
            bookId = bookId,
            date = date,
            duration = duration,
            startTime = startTime,
            endTime = endTime,
            sourceDeviceId = sourceDeviceId,
            updatedAt = updatedAt
        )
    }

    private fun BookmarkEntity.toDomain(): Bookmark {
        return Bookmark(
            id = id,
            bookId = bookId,
            chapterIndex = chapterIndex,
            position = position,
            locatorJson = locatorJson,
            title = title,
            createdAt = createdAt,
            syncId = syncId,
            updatedAt = updatedAt
        )
    }

    private fun Bookmark.toEntity(): BookmarkEntity {
        return BookmarkEntity(
            id = id,
            bookId = bookId,
            chapterIndex = chapterIndex,
            position = position,
            locatorJson = locatorJson,
            title = title,
            createdAt = createdAt,
            syncId = syncId,
            updatedAt = updatedAt
        )
    }

    private fun NoteEntity.toDomain(): Note {
        return Note(
            id = id,
            bookId = bookId,
            chapterIndex = chapterIndex,
            startPosition = startPosition,
            endPosition = endPosition,
            startLocatorJson = startLocatorJson,
            endLocatorJson = endLocatorJson,
            selectedText = selectedText,
            note = note,
            color = color,
            createdAt = createdAt,
            type = type,
            syncId = syncId,
            updatedAt = updatedAt
        )
    }

    private fun Note.toEntity(): NoteEntity {
        return NoteEntity(
            id = id,
            bookId = bookId,
            chapterIndex = chapterIndex,
            startPosition = startPosition,
            endPosition = endPosition,
            startLocatorJson = startLocatorJson,
            endLocatorJson = endLocatorJson,
            selectedText = selectedText,
            note = note,
            color = color,
            createdAt = createdAt,
            type = type,
            syncId = syncId,
            updatedAt = updatedAt
        )
    }
}
