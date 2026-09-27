package com.huangder.lumibooks.data.repository

import androidx.room.withTransaction
import com.huangder.lumibooks.data.local.dao.HighlightRuleDao
import com.huangder.lumibooks.data.local.dao.NoteDao
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookHighlightRuleStateEntity
import com.huangder.lumibooks.data.local.entity.BookHighlightSettingsEntity
import com.huangder.lumibooks.data.local.entity.HighlightRuleEntity
import com.huangder.lumibooks.data.local.entity.HighlightRuleExclusionEntity
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.BookHighlightSettings
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.repository.HighlightRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightRuleRepositoryImpl @Inject constructor(
    private val dao: HighlightRuleDao,
    private val noteDao: NoteDao,
    private val database: AppDatabase,
    private val syncIdentityStore: SyncIdentityStore
) : HighlightRuleRepository {
    override fun observeRulesForBook(bookId: String): Flow<List<HighlightRule>> =
        combine(dao.observeRules(), dao.observeBookStates(bookId)) { rules, states ->
            merge(rules, states)
        }

    override fun observeSettings(bookId: String): Flow<BookHighlightSettings> =
        dao.observeSettings(bookId).map { it?.toDomain() ?: BookHighlightSettings(bookId) }

    override suspend fun getRulesForBook(bookId: String): List<HighlightRule> =
        merge(dao.getRules(), dao.getBookStates(bookId))

    override suspend fun saveRule(rule: HighlightRule) {
        val existing = dao.getRule(rule.id)
        val entity = rule.toEntity().let { incoming ->
            if (existing == null) incoming else incoming.copy(
                // The editor receives this book's merged state; do not leak it into global defaults.
                enabled = existing.enabled,
                position = existing.position
            )
        }
        dao.upsertRule(entity)
        bumpSettingsForBooks()
    }

    override suspend fun saveRules(rules: List<HighlightRule>) {
        if (rules.isEmpty()) return
        dao.upsertRules(rules.map { it.toEntity() })
        bumpSettingsForBooks()
    }

    override suspend fun deleteRule(ruleId: String) {
        val deviceId = syncIdentityStore.deviceId()
        database.withTransaction {
            tombstoneGeneratedNotes(noteDao.getGeneratedNotesForRule(ruleId), deviceId)
            noteDao.deleteGeneratedNotesForRule(ruleId)
            dao.deleteStatesForRule(ruleId)
            dao.deleteExclusionsForRule(ruleId)
            dao.deleteRule(ruleId)
        }
        bumpSettingsForBooks()
    }

    override suspend fun setRuleEnabled(bookId: String, ruleId: String, enabled: Boolean) {
        val current = getRulesForBook(bookId)
        val position = current.indexOfFirst { it.id == ruleId }.coerceAtLeast(0)
        dao.upsertBookState(BookHighlightRuleStateEntity(bookId, ruleId, enabled, position))
        bumpSettings(bookId)
    }

    override suspend fun reorderRules(bookId: String, orderedRuleIds: List<String>) {
        val current = getRulesForBook(bookId).associateBy(HighlightRule::id)
        dao.upsertBookStates(orderedRuleIds.mapIndexedNotNull { index, id ->
            current[id]?.let { BookHighlightRuleStateEntity(bookId, id, it.enabled, index) }
        })
        bumpSettings(bookId)
    }

    override suspend fun setMaterializeNotes(bookId: String, enabled: Boolean) {
        val current = dao.getSettings(bookId)
        dao.upsertSettings(
            BookHighlightSettingsEntity(
                bookId = bookId,
                materializeNotes = enabled,
                revision = (current?.revision ?: 0L) + 1L
            )
        )
        if (!enabled) {
            val deviceId = syncIdentityStore.deviceId()
            database.withTransaction {
                tombstoneGeneratedNotes(noteDao.getGeneratedNotesByBookId(bookId), deviceId)
                noteDao.deleteGeneratedNotesByBookId(bookId)
            }
        }
    }

    override suspend fun addExclusion(bookId: String, ruleId: String, matchKey: String) {
        dao.upsertExclusion(HighlightRuleExclusionEntity(bookId, ruleId, matchKey, System.currentTimeMillis()))
    }

    override suspend fun clearExclusions(bookId: String) = dao.clearExclusions(bookId)

    override suspend fun getExclusionKeys(bookId: String): Set<String> =
        dao.getExclusions(bookId).mapTo(mutableSetOf()) { "${it.ruleId}\u0000${it.matchKey}" }

    private suspend fun bumpSettings(bookId: String) {
        val current = dao.getSettings(bookId)
        dao.upsertSettings(
            BookHighlightSettingsEntity(
                bookId,
                current?.materializeNotes ?: false,
                (current?.revision ?: 0L) + 1L
            )
        )
    }

    private suspend fun bumpSettingsForBooks() {
        dao.getAllSettings().forEach { settings ->
            dao.upsertSettings(settings.copy(revision = settings.revision + 1L))
        }
    }

    private suspend fun tombstoneGeneratedNotes(
        notes: List<com.huangder.lumibooks.data.local.entity.NoteEntity>,
        deviceId: String
    ) {
        if (notes.isEmpty()) return
        val deletedAt = System.currentTimeMillis()
        database.syncStateDao().upsertTombstones(
            notes.mapNotNull { note ->
                note.syncId.takeIf(String::isNotBlank)?.let { syncId ->
                    SyncTombstoneEntity("note", syncId, deletedAt, deviceId)
                }
            }
        )
    }

    private fun merge(
        rules: List<HighlightRuleEntity>,
        states: List<BookHighlightRuleStateEntity>
    ): List<HighlightRule> {
        val byId = states.associateBy(BookHighlightRuleStateEntity::ruleId)
        return rules.map { entity ->
            val state = byId[entity.id]
            entity.toDomain().copy(
                enabled = state?.enabled ?: entity.enabled,
                position = state?.position ?: entity.position
            )
        }.sortedWith(compareBy<HighlightRule> { it.position }.thenBy { it.name })
    }

    private fun HighlightRuleEntity.toDomain() = HighlightRule(
        id, name, pattern, enabled, position, targetScope, textColor, underlineMode,
        underlineOffset, underlineWidth, fontWeight, isItalic, sampleText, rawJson, updatedAt
    )

    private fun HighlightRule.toEntity() = HighlightRuleEntity(
        id, name, pattern, enabled, position, targetScope, textColor, underlineMode,
        underlineOffset, underlineWidth, fontWeight, isItalic, sampleText, rawJson, updatedAt
    )

    private fun BookHighlightSettingsEntity.toDomain() =
        BookHighlightSettings(bookId, materializeNotes, revision)
}
