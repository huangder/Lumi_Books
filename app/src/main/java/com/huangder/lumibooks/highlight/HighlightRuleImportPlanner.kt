package com.huangder.lumibooks.highlight

import com.huangder.lumibooks.domain.model.HighlightRule
import org.json.JSONObject
import java.util.UUID

data class HighlightRuleImportAnalysis(
    val supported: Int,
    val duplicates: Int,
    val conflicts: Int,
    val unsupported: Int
)

data class HighlightRuleImportPlan(
    val rulesToSave: List<HighlightRule>,
    val skipped: Int,
    val conflicts: Int,
    val unsupported: Int
)

object HighlightRuleImportPlanner {
    fun analyze(current: List<HighlightRule>, incoming: List<HighlightRule>): HighlightRuleImportAnalysis {
        val existing = current.associateBy(HighlightRule::id)
        var duplicates = 0
        var conflicts = 0
        var unsupported = 0
        incoming.forEach { candidate ->
            if (HighlightRuleMatcher.validationError(candidate) != null) unsupported++
            existing[candidate.id]?.let { old ->
                if (sameContent(old, candidate)) duplicates++ else conflicts++
            }
        }
        return HighlightRuleImportAnalysis(
            supported = incoming.size - unsupported,
            duplicates = duplicates,
            conflicts = conflicts,
            unsupported = unsupported
        )
    }

    fun plan(
        current: List<HighlightRule>,
        incoming: List<HighlightRule>,
        replaceConflicts: Boolean,
        importedName: (String) -> String,
        newId: () -> String = { UUID.randomUUID().toString() }
    ): HighlightRuleImportPlan {
        val byId = current.associateBy(HighlightRule::id).toMutableMap()
        val output = mutableListOf<HighlightRule>()
        var skipped = 0
        var conflicts = 0
        var nextPosition = (current.maxOfOrNull(HighlightRule::position) ?: -1) + 1
        incoming.forEach { candidate ->
            val old = byId[candidate.id]
            when {
                old == null -> {
                    val inserted = candidate.copy(position = nextPosition++)
                    output += inserted
                    byId[inserted.id] = inserted
                }
                HighlightRuleMatcher.validationError(old) != null &&
                    HighlightRuleMatcher.validationError(candidate) == null -> {
                    val replacement = candidate.copy(position = old.position)
                    output += replacement
                    byId[replacement.id] = replacement
                }
                sameContent(old, candidate) -> skipped++
                replaceConflicts -> {
                    val replacement = candidate.copy(position = old.position)
                    output += replacement
                    byId[replacement.id] = replacement
                    conflicts++
                }
                else -> {
                    val inserted = candidate.copy(
                        id = newId(),
                        name = importedName(candidate.name),
                        position = nextPosition++
                    )
                    output += inserted
                    byId[inserted.id] = inserted
                    conflicts++
                }
            }
        }
        return HighlightRuleImportPlan(
            rulesToSave = output,
            skipped = skipped,
            conflicts = conflicts,
            unsupported = incoming.count { HighlightRuleMatcher.validationError(it) != null }
        )
    }

    fun sameContent(left: HighlightRule, right: HighlightRule): Boolean =
        left.name == right.name && left.pattern == right.pattern && left.enabled == right.enabled &&
            left.targetScope == right.targetScope && left.textColor == right.textColor &&
            left.underlineMode == right.underlineMode && left.underlineOffset == right.underlineOffset &&
            left.underlineWidth == right.underlineWidth && left.fontWeight == right.fontWeight &&
            left.isItalic == right.isItalic && left.fontType == right.fontType &&
            left.sampleText == right.sampleText &&
            opaqueFields(left.rawJson) == opaqueFields(right.rawJson)

    private fun opaqueFields(raw: String): String = runCatching {
        JSONObject(raw).apply {
            listOf(
                "id", "name", "pattern", "enabled", "position", "targetScope", "textColor",
                "underlineMode", "underlineOffset", "underlineWidth", "fontWeight", "isItalic",
                "fontType", "sampleText", "_lumi"
            ).forEach(::remove)
        }.toString()
    }.getOrDefault("{}")
}
