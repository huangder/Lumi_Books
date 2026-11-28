package com.huangder.lumibooks.data.sync

import androidx.room.withTransaction
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.AnnotationTagEntity
import com.huangder.lumibooks.domain.model.AnnotationTags
import com.huangder.lumibooks.data.local.entity.SyncStateEntity
import javax.inject.Inject

/** Device-local visibility and repair. Neither action produces a sync tombstone. */
class WebdavLocalBookState @Inject constructor(private val database: AppDatabase) {
    private val states get() = database.syncStateDao()

    private fun key(bookId: String) = "webdav_suppressed:$bookId"

    suspend fun isSuppressed(bookId: String): Boolean = states.getValue(key(bookId)) != null

    suspend fun setSuppressed(bookId: String, suppressed: Boolean) {
        if (isSuppressed(bookId) == suppressed) return
        if (suppressed) states.putState(SyncStateEntity(key(bookId), "1"))
        else states.removeState(key(bookId))
    }

    internal suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction(block)

    internal suspend fun annotationDeletions(): Map<Pair<String, String>, Long> = states.getAllTombstones()
        .filter { it.namespace == "bookmark" || it.namespace == "note" }
        .associate { (it.namespace to it.itemId) to it.deletedAt }

    internal suspend fun bookmark(syncId: String) = states.findBookmark(syncId)
    internal suspend fun note(syncId: String) = states.findNote(syncId)

    internal suspend fun saveBookmark(item: BookmarkEntity) {
        states.upsertBookmarks(listOf(item))
        rememberTags(item.tagsJson, item.updatedAt)
    }

    internal suspend fun saveNote(item: NoteEntity) {
        states.upsertNotes(listOf(item.withoutConflictingRuleLink()))
        rememberTags(item.tagsJson, item.updatedAt)
    }

    private suspend fun rememberTags(json: String, updatedAt: Long) {
        database.annotationTagDao().ensureTags(AnnotationTags.decode(json).map { AnnotationTagEntity(it, updatedAt) })
    }

    private suspend fun NoteEntity.withoutConflictingRuleLink(): NoteEntity {
        val rule = sourceRuleId ?: return this
        val match = sourceMatchKey ?: return this
        val existing = states.findRuleNote(bookId, rule, match)
        // Both annotations keep their IDs and text; only one may own the derived rule match.
        return if (existing != null && existing.id != id) copy(sourceRuleId = null, sourceMatchKey = null) else this
    }

    internal suspend fun foldDuplicates(owners: Map<String, String>) {
        if (owners.isEmpty()) return
        transaction {
            val books = database.bookDao()
            val rows = books.getAllBooksSnapshot().associateBy { it.id }
            val records = states.getAllReadingRecords().groupBy { it.bookId }
            val bookmarks = states.getAllBookmarks().groupBy { it.bookId }
            val notes = states.getAllNotes().groupBy { it.bookId }
            val folders = states.getAllBookFolderLinks().groupBy { it.bookId }
            val tags = states.getAllBookTagLinks().groupBy { it.bookId }
            val journal = database.readingJournalDao().getAll().groupBy { it.bookId }
            val tombstones = states.getAllTombstones().associateBy { it.namespace to it.itemId }

            for ((ownerId, aliases) in owners.entries.groupBy({ it.value }, { it.key })) {
                val owner = rows[ownerId]?.takeIf { it.filePath.isNotBlank() } ?: continue
                val sources = aliases.mapNotNull(rows::get).filter { it.isCloudOnly && it.filePath.isBlank() }
                if (sources.isEmpty()) continue
                val ids = listOf(ownerId) + sources.map { it.id }
                val winner = (listOf(owner) + sources).maxBy { it.positionTime() }
                if (winner != owner) books.updateBook(owner.copy(
                    lastReadTime = winner.positionTime(), readingProgress = winner.readingProgress,
                    locatorJson = winner.locatorJson, isManuallyMarkedRead = winner.isManuallyMarkedRead
                ))
                states.upsertBookmarks(sources.flatMap { bookmarks[it.id].orEmpty() }.map { it.copy(bookId = ownerId) })
                for (note in sources.flatMap { notes[it.id].orEmpty() }.sortedByDescending { it.updatedAt }) {
                    states.upsertNotes(listOf(note.copy(bookId = ownerId).withoutConflictingRuleLink()))
                }
                database.readingJournalDao().upsertAll(sources.flatMap { journal[it.id].orEmpty() }.map { it.copy(bookId = ownerId) })
                val mergedRecords = ids.flatMap { records[it].orEmpty() }.groupBy { it.date to it.sourceDeviceId }
                for (versions in mergedRecords.values) {
                    if (versions.none { it.bookId != ownerId }) continue
                    val base = versions.firstOrNull { it.bookId == ownerId } ?: versions.first()
                    versions.filter { it != base && it.bookId != ownerId }.forEach { states.deleteReadingRecord(it.id) }
                    states.upsertReadingRecords(listOf(base.copy(
                        bookId = ownerId, duration = versions.maxOf { it.duration },
                        listeningDuration = versions.maxOf { it.listeningDuration },
                        startTime = versions.minOf { it.startTime }, endTime = versions.maxOf { it.endTime },
                        updatedAt = versions.maxOf { it.updatedAt }
                    )))
                }
                ids.flatMap { folders[it].orEmpty() }.maxByOrNull { it.updatedAt }?.let { link ->
                    val deletedAt = tombstones["book_folder" to ownerId]?.deletedAt ?: Long.MIN_VALUE
                    if (link.updatedAt > deletedAt) states.upsertBookFolderLinks(listOf(link.copy(bookId = ownerId)))
                }
                states.upsertBookTagLinks(ids.flatMap { tags[it].orEmpty() }.groupBy { it.tagId }.values.mapNotNull { versions ->
                    val link = versions.maxBy { it.updatedAt }
                    val deletedAt = tombstones["book_tag" to "$ownerId:${link.tagId}"]?.deletedAt ?: Long.MIN_VALUE
                    link.copy(bookId = ownerId).takeIf { it.updatedAt > deletedAt }
                })
                for (source in sources) {
                    states.deleteBookFolderLinksByBookIds(listOf(source.id))
                    books.deleteTagLinksByBookId(source.id)
                    // The progress is now on the owner. Don't reapply a stale rewind on retry.
                    books.updateBook(source.copy(lastReadTime = 0, readingProgress = 0f,
                        locatorJson = null, isManuallyMarkedRead = false))
                    setSuppressed(source.id, true)
                }
            }
        }
    }

    private fun BookEntity.positionTime() = readingPositionTimestamp(
        lastReadTime, createdAt, readingProgress, locatorJson, isManuallyMarkedRead
    )
}
