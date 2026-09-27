package com.huangder.lumibooks.domain.model

data class HighlightRule(
    val id: String,
    val name: String,
    val pattern: String,
    val enabled: Boolean = true,
    val position: Int = 0,
    val targetScope: Int = TARGET_BODY,
    val textColor: Int? = null,
    val underlineMode: Int = UNDERLINE_NONE,
    val underlineOffset: Float = 2f,
    val underlineWidth: Float = 1f,
    val fontWeight: Int = 400,
    val isItalic: Boolean = false,
    val sampleText: String = "",
    /** Original object used to preserve fields that LUMI does not execute. */
    val rawJson: String = "{}",
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TARGET_BODY = 0
        const val UNDERLINE_NONE = 0
        const val UNDERLINE_STRAIGHT = 1
        const val UNDERLINE_WAVE = 3
    }
}

data class BookHighlightRuleState(
    val bookId: String,
    val ruleId: String,
    val enabled: Boolean,
    val position: Int
)

data class BookHighlightSettings(
    val bookId: String,
    val materializeNotes: Boolean = false,
    val revision: Long = 0L
)

data class RuleStyle(
    val textColor: Int?,
    val underlineMode: Int,
    val underlineOffset: Float,
    val underlineWidth: Float,
    val fontWeight: Int,
    val italic: Boolean
)

data class RuleMatch(
    val ruleId: String,
    val ruleName: String,
    val chapterIndex: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val matchKey: String,
    val style: RuleStyle,
    val position: Int
)
