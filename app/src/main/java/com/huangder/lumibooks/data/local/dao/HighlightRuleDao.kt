package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.huangder.lumibooks.data.local.entity.BookHighlightRuleStateEntity
import com.huangder.lumibooks.data.local.entity.BookHighlightSettingsEntity
import com.huangder.lumibooks.data.local.entity.HighlightRuleEntity
import com.huangder.lumibooks.data.local.entity.HighlightRuleExclusionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightRuleDao {
    @Query("SELECT * FROM highlight_rules ORDER BY position, name")
    fun observeRules(): Flow<List<HighlightRuleEntity>>

    @Query("SELECT * FROM highlight_rules ORDER BY position, name")
    suspend fun getRules(): List<HighlightRuleEntity>

    @Query("SELECT * FROM highlight_rules WHERE id = :id LIMIT 1")
    suspend fun getRule(id: String): HighlightRuleEntity?

    @Upsert
    suspend fun upsertRule(rule: HighlightRuleEntity)

    @Upsert
    suspend fun upsertRules(rules: List<HighlightRuleEntity>)

    @Query("DELETE FROM highlight_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: String)

    @Query("DELETE FROM highlight_rules")
    suspend fun clearRules()

    @Query("SELECT * FROM book_highlight_rule_states WHERE bookId = :bookId ORDER BY position")
    fun observeBookStates(bookId: String): Flow<List<BookHighlightRuleStateEntity>>

    @Query("SELECT * FROM book_highlight_rule_states WHERE bookId = :bookId ORDER BY position")
    suspend fun getBookStates(bookId: String): List<BookHighlightRuleStateEntity>

    @Query("SELECT * FROM book_highlight_rule_states")
    suspend fun getAllBookStates(): List<BookHighlightRuleStateEntity>

    @Upsert
    suspend fun upsertBookState(state: BookHighlightRuleStateEntity)

    @Upsert
    suspend fun upsertBookStates(states: List<BookHighlightRuleStateEntity>)

    @Query("DELETE FROM book_highlight_rule_states WHERE ruleId = :ruleId")
    suspend fun deleteStatesForRule(ruleId: String)

    @Query("DELETE FROM book_highlight_rule_states")
    suspend fun clearBookStates()

    @Query("SELECT * FROM book_highlight_settings WHERE bookId = :bookId LIMIT 1")
    fun observeSettings(bookId: String): Flow<BookHighlightSettingsEntity?>

    @Query("SELECT * FROM book_highlight_settings WHERE bookId = :bookId LIMIT 1")
    suspend fun getSettings(bookId: String): BookHighlightSettingsEntity?

    @Query("SELECT * FROM book_highlight_settings")
    suspend fun getAllSettings(): List<BookHighlightSettingsEntity>

    @Upsert
    suspend fun upsertSettings(settings: BookHighlightSettingsEntity)

    @Upsert
    suspend fun upsertAllSettings(settings: List<BookHighlightSettingsEntity>)

    @Query("DELETE FROM book_highlight_settings")
    suspend fun clearSettings()

    @Query("SELECT * FROM highlight_rule_exclusions WHERE bookId = :bookId")
    suspend fun getExclusions(bookId: String): List<HighlightRuleExclusionEntity>

    @Query("SELECT * FROM highlight_rule_exclusions")
    suspend fun getAllExclusions(): List<HighlightRuleExclusionEntity>

    @Upsert
    suspend fun upsertExclusion(exclusion: HighlightRuleExclusionEntity)

    @Upsert
    suspend fun upsertExclusions(exclusions: List<HighlightRuleExclusionEntity>)

    @Query("DELETE FROM highlight_rule_exclusions WHERE bookId = :bookId")
    suspend fun clearExclusions(bookId: String)

    @Query("DELETE FROM highlight_rule_exclusions WHERE ruleId = :ruleId")
    suspend fun deleteExclusionsForRule(ruleId: String)

    @Query("DELETE FROM highlight_rule_exclusions")
    suspend fun clearAllExclusions()
}
