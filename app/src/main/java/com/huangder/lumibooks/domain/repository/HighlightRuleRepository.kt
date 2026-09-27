package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.domain.model.BookHighlightSettings
import com.huangder.lumibooks.domain.model.HighlightRule
import kotlinx.coroutines.flow.Flow

interface HighlightRuleRepository {
    fun observeRulesForBook(bookId: String): Flow<List<HighlightRule>>
    fun observeSettings(bookId: String): Flow<BookHighlightSettings>
    suspend fun getRulesForBook(bookId: String): List<HighlightRule>
    suspend fun saveRule(rule: HighlightRule)
    suspend fun saveRules(rules: List<HighlightRule>)
    suspend fun deleteRule(ruleId: String)
    suspend fun setRuleEnabled(bookId: String, ruleId: String, enabled: Boolean)
    suspend fun reorderRules(bookId: String, orderedRuleIds: List<String>)
    suspend fun setMaterializeNotes(bookId: String, enabled: Boolean)
    suspend fun addExclusion(bookId: String, ruleId: String, matchKey: String)
    suspend fun clearExclusions(bookId: String)
    suspend fun getExclusionKeys(bookId: String): Set<String>
}
