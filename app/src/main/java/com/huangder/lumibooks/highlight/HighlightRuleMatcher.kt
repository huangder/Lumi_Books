package com.huangder.lumibooks.highlight

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.model.RuleMatch
import com.huangder.lumibooks.domain.model.RuleStyle
import java.security.MessageDigest

object HighlightRuleMatcher {
    const val MAX_MATCHES_PER_RULE_AND_CHAPTER = 5_000

    fun validationError(rule: HighlightRule): String? {
        if (rule.targetScope != HighlightRule.TARGET_BODY) return "暂不支持该作用范围"
        if (rule.underlineMode !in setOf(
                HighlightRule.UNDERLINE_NONE,
                HighlightRule.UNDERLINE_STRAIGHT,
                HighlightRule.UNDERLINE_DOUBLE,
                HighlightRule.UNDERLINE_WAVE,
                HighlightRule.UNDERLINE_DASHED
            )
        ) return "暂不支持该下划线类型"
        return try {
            Pattern.compile(rule.pattern)
            null
        } catch (error: PatternSyntaxException) {
            error.message ?: "正则语法不受支持"
        }
    }

    fun match(text: CharSequence, chapterIndex: Int, rules: List<HighlightRule>): List<RuleMatch> {
        if (text.isEmpty()) return emptyList()
        return buildList {
            rules.asSequence().filter(HighlightRule::enabled).sortedBy(HighlightRule::position).forEach { rule ->
                val matcher = runCatching {
                    if (validationError(rule) != null) return@forEach
                    Pattern.compile(rule.pattern).matcher(text)
                }.getOrNull() ?: return@forEach
                var count = 0
                while (count < MAX_MATCHES_PER_RULE_AND_CHAPTER && matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    if (end <= start) continue
                    val selected = text.subSequence(start, end).toString()
                    add(
                        RuleMatch(
                            ruleId = rule.id,
                            ruleName = rule.name,
                            chapterIndex = chapterIndex,
                            start = start,
                            end = end,
                            text = selected,
                            matchKey = matchKey(chapterIndex, start, end, selected),
                            style = RuleStyle(
                                rule.textColor,
                                rule.underlineMode,
                                rule.underlineOffset,
                                rule.underlineWidth,
                                rule.fontWeight,
                                rule.isItalic,
                                rule.fontType
                            ),
                            position = rule.position
                        )
                    )
                    count++
                }
            }
        }
    }

    fun matchKey(chapterIndex: Int, start: Int, end: Int, selected: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(selected.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "$chapterIndex:$start:$end:$digest"
    }
}
