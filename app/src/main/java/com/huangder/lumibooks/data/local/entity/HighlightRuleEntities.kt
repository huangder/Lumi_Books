package com.huangder.lumibooks.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "highlight_rules", primaryKeys = ["id"])
data class HighlightRuleEntity(
    val id: String,
    val name: String,
    val pattern: String,
    val enabled: Boolean,
    val position: Int,
    val targetScope: Int,
    val textColor: Int?,
    val underlineMode: Int,
    val underlineOffset: Float,
    val underlineWidth: Float,
    val fontWeight: Int,
    val isItalic: Boolean,
    val sampleText: String,
    val rawJson: String,
    val updatedAt: Long
)

@Entity(
    tableName = "book_highlight_rule_states",
    primaryKeys = ["bookId", "ruleId"],
    indices = [Index("ruleId")]
)
data class BookHighlightRuleStateEntity(
    val bookId: String,
    val ruleId: String,
    val enabled: Boolean,
    val position: Int
)

@Entity(tableName = "book_highlight_settings", primaryKeys = ["bookId"])
data class BookHighlightSettingsEntity(
    val bookId: String,
    val materializeNotes: Boolean,
    val revision: Long
)

@Entity(
    tableName = "highlight_rule_exclusions",
    primaryKeys = ["bookId", "ruleId", "matchKey"],
    indices = [Index("ruleId")]
)
data class HighlightRuleExclusionEntity(
    val bookId: String,
    val ruleId: String,
    val matchKey: String,
    val createdAt: Long
)
