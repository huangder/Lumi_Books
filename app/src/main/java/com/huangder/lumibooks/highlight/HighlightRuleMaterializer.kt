package com.huangder.lumibooks.highlight

import android.content.Context
import androidx.room.withTransaction
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.HighlightRuleRepository
import com.huangder.lumibooks.ui.reader.createHighlightLocatorPair
import com.huangder.lumibooks.util.parser.BookParserFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class HighlightRuleMaterializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val ruleRepository: HighlightRuleRepository,
    private val database: AppDatabase,
    private val syncIdentityStore: SyncIdentityStore
) {
    suspend fun scanBook(bookId: String, onProgress: suspend (current: Int, total: Int) -> Unit): Int {
        val book = bookRepository.getBookById(bookId) ?: return 0
        if (book.format != BookFormat.TXT && book.format != BookFormat.EPUB) return 0
        val settings = database.highlightRuleDao().getSettings(bookId) ?: return 0
        if (!settings.materializeNotes) return 0
        val rules = ruleRepository.getRulesForBook(bookId).filter(HighlightRule::enabled)
        val exclusionKeys = ruleRepository.getExclusionKeys(bookId)
        val parser = BookParserFactory.createParser(book.format, context)
        return try {
            val content = parser.parse(book.filePath)
            val total = content.chapters.size
            val desired = mutableListOf<NoteEntity>()
            content.chapters.indices.forEach { chapterIndex ->
                coroutineContext.ensureActive()
                val text = parser.getChapterContent(chapterIndex).toString()
                desired += desiredNotesForChapter(
                    bookId = bookId,
                    format = book.format,
                    chapterIndex = chapterIndex,
                    text = text,
                    rules = rules,
                    exclusionKeys = exclusionKeys
                )
                onProgress(chapterIndex + 1, total)
            }
            reconcile(bookId, desired)
            desired.size
        } finally {
            parser.close()
        }
    }

    /** Persists the visible chapter before the full-book worker continues in the background. */
    suspend fun materializeChapter(
        bookId: String,
        format: BookFormat,
        chapterIndex: Int,
        text: String
    ): Int {
        if (format != BookFormat.TXT && format != BookFormat.EPUB) return 0
        val settings = database.highlightRuleDao().getSettings(bookId) ?: return 0
        if (!settings.materializeNotes) return 0
        val desired = desiredNotesForChapter(
            bookId = bookId,
            format = format,
            chapterIndex = chapterIndex,
            text = text,
            rules = ruleRepository.getRulesForBook(bookId).filter(HighlightRule::enabled),
            exclusionKeys = ruleRepository.getExclusionKeys(bookId)
        )
        reconcile(bookId, desired, chapterIndex)
        return desired.size
    }

    private fun desiredNotesForChapter(
        bookId: String,
        format: BookFormat,
        chapterIndex: Int,
        text: String,
        rules: List<HighlightRule>,
        exclusionKeys: Set<String>
    ): List<NoteEntity> {
        val now = System.currentTimeMillis()
        return HighlightRuleMatcher.match(text, chapterIndex, rules).mapNotNull { match ->
            if ("${match.ruleId}\u0000${match.matchKey}" in exclusionKeys) return@mapNotNull null
            val locators = if (format == BookFormat.EPUB) {
                createHighlightLocatorPair(text, match.start, match.end, match.text)
            } else null to null
            NoteEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                startPosition = match.start,
                endPosition = match.end,
                startLocatorJson = locators.first,
                endLocatorJson = locators.second,
                selectedText = match.text,
                note = "",
                color = match.style.textColor?.let { String.format("#%08X", it) } ?: "#FFF2C94C",
                createdAt = now,
                type = if (match.style.underlineMode == HighlightRule.UNDERLINE_NONE) "highlight" else "underline",
                syncId = deterministicSyncId(bookId, match.ruleId, match.matchKey),
                updatedAt = now,
                isNote = false,
                origin = Note.ORIGIN_HIGHLIGHT_RULE,
                sourceRuleId = match.ruleId,
                sourceMatchKey = match.matchKey,
                styleSnapshotJson = RuleStyleJson.encode(match.style)
            )
        }
    }

    private suspend fun reconcile(
        bookId: String,
        desired: List<NoteEntity>,
        chapterIndex: Int? = null
    ) {
        val deviceId = syncIdentityStore.deviceId()
        database.withTransaction {
            val dao = database.noteDao()
            val existing = dao.getGeneratedNotesByBookId(bookId).let { notes ->
                if (chapterIndex == null) notes else notes.filter { it.chapterIndex == chapterIndex }
            }
            val existingByKey = existing.associateBy { "${it.sourceRuleId}\u0000${it.sourceMatchKey}" }
            val desiredKeys = desired.mapTo(mutableSetOf()) { "${it.sourceRuleId}\u0000${it.sourceMatchKey}" }
            val stale = existing.filterNot {
                "${it.sourceRuleId}\u0000${it.sourceMatchKey}" in desiredKeys
            }
            if (stale.isNotEmpty()) {
                val deletedAt = System.currentTimeMillis()
                database.syncStateDao().upsertTombstones(
                    stale.mapNotNull { note ->
                        note.syncId.takeIf(String::isNotBlank)?.let { syncId ->
                            SyncTombstoneEntity("note", syncId, deletedAt, deviceId)
                        }
                    }
                )
                dao.deleteNotesByIds(stale.map(NoteEntity::id))
            }
            val merged = desired.map { item ->
                val previous = existingByKey["${item.sourceRuleId}\u0000${item.sourceMatchKey}"]
                if (previous == null) item else {
                    val unchanged = previous.chapterIndex == item.chapterIndex &&
                        previous.startPosition == item.startPosition && previous.endPosition == item.endPosition &&
                        previous.startLocatorJson == item.startLocatorJson && previous.endLocatorJson == item.endLocatorJson &&
                        previous.selectedText == item.selectedText && previous.color == item.color &&
                        previous.type == item.type && previous.styleSnapshotJson == item.styleSnapshotJson
                    item.copy(
                        id = previous.id,
                        createdAt = previous.createdAt,
                        syncId = previous.syncId,
                        updatedAt = if (unchanged) previous.updatedAt else item.updatedAt,
                        note = previous.note,
                        isNote = previous.isNote
                    )
                }
            }
            if (merged.isNotEmpty()) dao.insertNotes(merged)
        }
    }

    private fun deterministicSyncId(bookId: String, ruleId: String, matchKey: String): String =
        UUID.nameUUIDFromBytes("lumi-highlight:$bookId:$ruleId:$matchKey".toByteArray(StandardCharsets.UTF_8)).toString()
}
