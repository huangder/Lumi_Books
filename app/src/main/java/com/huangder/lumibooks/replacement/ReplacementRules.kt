package com.huangder.lumibooks.replacement

import com.google.re2j.Pattern
import org.json.JSONArray
import org.json.JSONObject

internal data class ReplacementRule(
    val id: String,
    val name: String = "",
    val find: String = "",
    val replacement: String = "",
    val regex: Boolean = false,
    val enabled: Boolean = true
)

internal data class BookReplacementRules(
    val enabled: Boolean = false,
    val rules: List<ReplacementRule> = emptyList()
) {
    fun upsert(rule: ReplacementRule): BookReplacementRules = copy(
        rules = if (rules.any { it.id == rule.id }) {
            rules.map { if (it.id == rule.id) rule else it }
        } else rules + rule
    )

    fun move(id: String, delta: Int): BookReplacementRules {
        val index = rules.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in rules.indices) return this
        return copy(rules = rules.toMutableList().apply { add(target, removeAt(index)) })
    }

    fun encode(): String = JSONObject().put("enabled", enabled).put(
        "rules",
        JSONArray().apply {
            rules.forEach { rule ->
                put(JSONObject().put("id", rule.id).put("name", rule.name)
                    .put("find", rule.find).put("replacement", rule.replacement)
                    .put("regex", rule.regex).put("enabled", rule.enabled))
            }
        }
    ).toString()

    companion object {
        fun decode(raw: String?): BookReplacementRules {
            if (raw == null) return BookReplacementRules()
            return runCatching {
                val root = JSONObject(raw)
                val entries = root.optJSONArray("rules") ?: JSONArray()
                val rules = (0 until entries.length()).mapNotNull { index ->
                    val value = entries.optJSONObject(index) ?: return@mapNotNull null
                    val id = value.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    ReplacementRule(id, value.optString("name"), value.optString("find"),
                        value.optString("replacement"), value.optBoolean("regex"), value.optBoolean("enabled", true))
                }.distinctBy(ReplacementRule::id)
                BookReplacementRules(root.optBoolean("enabled"), rules)
            }.getOrDefault(BookReplacementRules())
        }
    }
}

internal data class ReplacementEdit(val start: Int, val end: Int, val value: String)
/**
 * Context around the first match. Every match inside the window is applied to [after], so the
 * preview never shows a later occurrence as if it were left unchanged. [removed] and [added] index
 * the matched text in [before] and the inserted text in [after]; a deletion adds an empty range.
 */
internal data class ReplacementPreview(
    val count: Int = 0,
    val before: String = "",
    val after: String = "",
    val removed: List<IntRange> = emptyList(),
    val added: List<IntRange> = emptyList()
)

private const val PreviewContext = 24
private const val PreviewMaxMatch = 160
private const val PreviewMaxValue = 120

/** [edits] are ascending and non-overlapping offsets into [text]. */
internal fun previewWindow(text: String, edits: List<ReplacementEdit>): ReplacementPreview {
    val first = edits.first()
    val left = (first.start - PreviewContext).coerceAtLeast(0)
    if (first.end - first.start > PreviewMaxMatch) {
        val cut = first.start + PreviewMaxMatch
        val before = text.substring(left, cut) + "…"
        val value = first.value.take(PreviewMaxValue)
        val offset = first.start - left
        return ReplacementPreview(edits.size, before, text.substring(left, first.start) + value,
            listOf(offset until before.length), listOf(offset until offset + value.length))
    }
    val right = (first.end + PreviewContext).coerceAtMost(text.length)
    val removed = mutableListOf<IntRange>()
    val added = mutableListOf<IntRange>()
    val after = StringBuilder()
    var position = left
    for (edit in edits) {
        if (edit.end > right) break
        after.append(text, position, edit.start)
        removed += (edit.start - left) until (edit.end - left)
        val value = edit.value.take(PreviewMaxValue)
        added += after.length until after.length + value.length
        after.append(value)
        position = edit.end
    }
    after.append(text, position, right)
    return ReplacementPreview(edits.size, text.substring(left, right), after.toString(), removed, added)
}
internal enum class ReplacementError { EMPTY_FIND, INVALID_REGEX, INVALID_GROUP }

/** A stage uses offsets in the input to that rule. Stages are traversed in reverse for saving. */
internal class ReplacementOffsets(private val stages: List<List<ReplacementEdit>>) {
    val changed: Boolean get() = stages.any { it.isNotEmpty() }

    fun display(offset: Int, end: Boolean = false): Int =
        stages.fold(offset) { value, edits -> project(value, edits, end, reverse = false) }

    fun source(offset: Int, end: Boolean = false): Int =
        stages.asReversed().fold(offset) { value, edits -> project(value, edits, end, reverse = true) }

    companion object {
        private fun project(offset: Int, edits: List<ReplacementEdit>, end: Boolean, reverse: Boolean): Int {
            var delta = 0
            for (edit in edits) {
                val change = edit.value.length - (edit.end - edit.start)
                val left = if (reverse) edit.start + delta else edit.start
                val right = if (reverse) left + edit.value.length else edit.end
                val target = if (reverse) edit.start else edit.start + delta
                val targetLength = if (reverse) edit.end - edit.start else edit.value.length
                if (offset < left) break
                if (offset == left && left != right) return target
                if (offset < right) return if (end) target + targetLength else target
                // At a collapsed deletion, a selection starting here belongs to the following text.
                if (left == right && offset == left && end) return target
                if (offset == right && left != right && end) return target + targetLength
                delta += change
            }
            return if (reverse) offset - delta else offset + delta
        }
    }
}

internal data class ReplacementResult(
    val source: String,
    val display: String,
    val stages: List<List<ReplacementEdit>>,
    val preview: ReplacementPreview = ReplacementPreview()
) {
    val offsets = ReplacementOffsets(stages)
}

internal class ReplacementProgram(rules: List<ReplacementRule>) {
    val cacheKey: String = rules.filter(ReplacementRule::enabled).takeIf { it.isNotEmpty() }
        ?.let { enabled ->
            val encoded = BookReplacementRules(true, enabled.map { it.copy(id = "", name = "") }).encode()
            java.security.MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.orEmpty()
    private val compiled = rules.filter(ReplacementRule::enabled).mapNotNull { rule ->
        compile(rule).second?.let { rule.id to it }
    }
    val active: Boolean get() = compiled.isNotEmpty()

    /** Segment lengths include separators as independent, ineligible runs where applicable. */
    fun apply(
        source: String,
        segments: List<ReplacementSegment> = listOf(ReplacementSegment(source.length)),
        previewRule: String? = null,
        onEdits: (List<ReplacementEdit>) -> Unit = {}
    ): ReplacementResult {
        var current = source
        var currentSegments = segments
        val stages = mutableListOf<List<ReplacementEdit>>()
        var preview = ReplacementPreview()
        for ((id, rule) in compiled) {
            var start = 0
            val edits = mutableListOf<ReplacementEdit>()
            val nextSegments = currentSegments.map { segment ->
                val end = start + segment.length
                val hits = if (segment.eligible && end > start) rule.edits(current.substring(start, end), start) else emptyList()
                edits.addAll(hits)
                start = end
                segment.copy(length = segment.length + hits.sumOf { it.value.length - (it.end - it.start) })
            }
            val changes = edits.filterNot { it.value.length == it.end - it.start && current.regionMatches(it.start, it.value, 0, it.value.length) }
            val next = if (changes.isEmpty()) current else buildString {
                var position = 0
                changes.forEach { edit ->
                    append(current, position, edit.start)
                    append(edit.value)
                    position = edit.end
                }
                append(current, position, current.length)
            }
            if (id == previewRule && edits.isNotEmpty()) preview = previewWindow(current, edits)
            if (changes.isNotEmpty()) {
                onEdits(changes)
                stages.add(changes)
            }
            current = next
            currentSegments = nextSegments
        }
        return ReplacementResult(source, current, stages, preview)
    }

    private class CompiledRule(val pattern: Pattern, val parts: List<ReplacementPart>) {
        fun edits(text: String, offset: Int): List<ReplacementEdit> = buildList {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                if (matcher.start() == matcher.end()) continue
                val value = buildString {
                    parts.forEach { part ->
                        if (part.group == null) append(part.literal) else append(matcher.group(part.group).orEmpty())
                    }
                }
                add(ReplacementEdit(offset + matcher.start(), offset + matcher.end(), value))
            }
        }
    }

    private data class ReplacementPart(val literal: String = "", val group: Int? = null)

    companion object {
        val Empty = ReplacementProgram(emptyList())

        fun validate(rule: ReplacementRule): ReplacementError? = compile(rule).first

        private fun compile(rule: ReplacementRule): Pair<ReplacementError?, CompiledRule?> {
            if (rule.find.isEmpty()) return ReplacementError.EMPTY_FIND to null
            val pattern = try {
                Pattern.compile(if (rule.regex) rule.find else Pattern.quote(rule.find))
            } catch (_: com.google.re2j.PatternSyntaxException) {
                return ReplacementError.INVALID_REGEX to null
            }
            val parts = if (rule.regex) replacementParts(rule.replacement, pattern.matcher("").groupCount())
                ?: return ReplacementError.INVALID_GROUP to null
            else listOf(ReplacementPart(literal = rule.replacement))
            return null to CompiledRule(pattern, parts)
        }

        private fun replacementParts(value: String, groups: Int): List<ReplacementPart>? = buildList {
            var index = 0
            while (index < value.length) {
                val character = value[index++]
                when {
                    character == '\\' -> {
                        if (index == value.length) return null
                        add(ReplacementPart(literal = value[index++].toString()))
                    }
                    character == '$' -> {
                        if (index == value.length || value[index] !in '0'..'9') return null
                        var group = value[index++].digitToInt()
                        if (group > groups) return null
                        while (index < value.length && value[index] in '0'..'9') {
                            val candidate = group.toLong() * 10 + value[index].digitToInt()
                            if (candidate > groups) break
                            group = candidate.toInt()
                            index++
                        }
                        add(ReplacementPart(group = group))
                    }
                    else -> add(ReplacementPart(literal = character.toString()))
                }
            }
        }
    }
}

internal data class ReplacementSegment(val length: Int, val eligible: Boolean = true)
