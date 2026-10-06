package com.huangder.lumibooks.util.markdown

import org.json.JSONObject
import kotlin.math.abs

/** Quote matching never falls back to painting a stale numerical offset. */
object MarkdownLocatorMigration {
    data class Match(val start: Int, val end: Int)
    private fun normalized(value: String): Pair<String, IntArray> {
        val text = StringBuilder()
        val offsets = IntArray(value.length)
        var count = 0
        value.forEachIndexed { i, c ->
            if (!c.isWhitespace() && c != '\u00A0' && c !in '\u200B'..'\u200D' && c != '\uFEFF' && c != '\u2060') {
                text.append(c); offsets[count++] = i
            }
        }
        return text.toString() to offsets.copyOf(count)
    }
    fun match(text: String, exact: String, locator: String?, oldOffset: Int): Match? {
        return Matcher(text).match(exact, locator, oldOffset)
    }
    /** One normalization per update, shared by all notes and bookmarks. */
    class Matcher(private val text: String) {
    private val indexed by lazy { normalized(text) }
    fun match(exact: String, locator: String?, oldOffset: Int): Match? {
        val quote = normalized(exact).first
        if (quote.isEmpty()) return null
        val (normalizedText, offsets) = indexed
        val saved = runCatching { JSONObject(locator ?: "{}") }.getOrDefault(JSONObject())
        val prefix = normalized(saved.optString("prefix")).first.takeLast(32)
        val suffix = normalized(saved.optString("suffix")).first.take(32)
        val expected = if (saved.optInt("textLength") > 0) {
            saved.optInt("textPosition", oldOffset).toDouble() / saved.optInt("textLength") * text.length
        } else oldOffset.toDouble()
        var from = 0
        var best: Match? = null
        var bestScore = -1
        var bestDistance = Double.MAX_VALUE
        while (from <= normalizedText.length - quote.length) {
            val at = normalizedText.indexOf(quote, from)
            if (at < 0) break
            val before = normalizedText.substring((at - prefix.length).coerceAtLeast(0), at)
            val afterStart = at + quote.length
            val after = normalizedText.substring(afterStart, (afterStart + suffix.length).coerceAtMost(normalizedText.length))
            val prefixScore = before.reversed().zip(prefix.reversed()).takeWhile { it.first == it.second }.size
            val suffixScore = after.zip(suffix).takeWhile { it.first == it.second }.size
            val score = prefixScore + suffixScore
            val start = offsets[at]
            val distance = abs(start - expected)
            if (score > bestScore || score == bestScore && distance < bestDistance) {
                best = Match(start, offsets[at + quote.length - 1] + 1)
                bestScore = score; bestDistance = distance
            }
            from = at + 1
        }
        return best
    }
    }
    fun locator(text: String, match: Match, end: Boolean = false): String = JSONObject()
        .put("version", 3).put("format", "markdown-native").put("href", "document.md")
        .put("textPosition", if (end) match.end else match.start).put("textLength", text.length)
        .put("textOffset", 0).put("domPath", org.json.JSONArray())
        .put("exact", text.substring(match.start, match.end))
        .put("prefix", text.substring((match.start - 32).coerceAtLeast(0), match.start))
        .put("suffix", text.substring(match.end, (match.end + 32).coerceAtMost(text.length)))
        .put("progression", (if (end) match.end else match.start).toDouble() / text.length.coerceAtLeast(1)).toString()
}
