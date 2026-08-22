package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.util.ChineseConverter
import org.json.JSONObject
import kotlin.math.abs

internal data class HighlightTextReference(
    val exact: String,
    val prefix: String = "",
    val suffix: String = "",
    val textPosition: Int? = null,
    val textLength: Int? = null,
    val progression: Double? = null
)

internal data class ResolvedHighlightRange(
    val start: Int,
    val end: Int
)

private data class NormalizedText(
    val value: String,
    val sourceOffsets: IntArray
)

private data class HighlightCandidate(
    val normalizedStart: Int,
    val contextScore: Int,
    val distanceFromExpected: Double
)

internal object HighlightAnchorResolver {
    fun resolve(
        chapterText: CharSequence,
        storedStart: Int,
        storedEnd: Int,
        selectedText: String,
        reference: HighlightTextReference? = null
    ): ResolvedHighlightRange? {
        val source = chapterText.toString()
        val exact = reference?.exact?.takeIf { it.isNotBlank() } ?: selectedText
        if (source.isEmpty() || exact.isBlank()) return null

        val safeStart = storedStart.coerceIn(0, source.length)
        val safeEnd = storedEnd.coerceIn(safeStart, source.length)
        if (
            safeStart < safeEnd && equivalent(source.substring(safeStart, safeEnd), exact) &&
            storedRangeAgreesWithReference(safeStart, source.length, exact, reference)
        ) {
            return ResolvedHighlightRange(safeStart, safeEnd)
        }

        val normalizedSource = normalizeWithOffsets(source)
        val normalizedExact = normalize(exact)
        if (normalizedExact.isEmpty() || normalizedSource.value.isEmpty()) return null

        val normalizedPrefix = normalize(reference?.prefix.orEmpty()).takeLast(CONTEXT_LENGTH)
        val normalizedSuffix = normalize(reference?.suffix.orEmpty()).take(CONTEXT_LENGTH)
        val expected = expectedNormalizedPosition(reference, normalizedSource.value.length)
            ?: if (source.isNotEmpty()) {
                safeStart.toDouble() / source.length * normalizedSource.value.length
            } else {
                0.0
            }

        val candidates = mutableListOf<HighlightCandidate>()
        var cursor = 0
        while (cursor <= normalizedSource.value.length - normalizedExact.length) {
            val found = normalizedSource.value.indexOf(normalizedExact, cursor)
            if (found < 0) break
            val prefixScore = commonSuffixLength(
                normalizedSource.value.substring(0, found),
                normalizedPrefix
            )
            val suffixStart = found + normalizedExact.length
            val suffixScore = commonPrefixLength(
                normalizedSource.value.substring(suffixStart),
                normalizedSuffix
            )
            candidates += HighlightCandidate(
                normalizedStart = found,
                contextScore = prefixScore + suffixScore,
                distanceFromExpected = abs(found - expected)
            )
            cursor = found + 1
        }
        val best = candidates.minWithOrNull(
            compareByDescending<HighlightCandidate> { it.contextScore }
                .thenBy { it.distanceFromExpected }
                .thenBy { it.normalizedStart }
        ) ?: return null

        val sourceStart = normalizedSource.sourceOffsets[best.normalizedStart]
        val normalizedEnd = best.normalizedStart + normalizedExact.length - 1
        val sourceEnd = normalizedSource.sourceOffsets[normalizedEnd] + 1
        return ResolvedHighlightRange(sourceStart, sourceEnd.coerceAtMost(source.length))
    }

    private fun expectedNormalizedPosition(
        reference: HighlightTextReference?,
        normalizedLength: Int
    ): Double? {
        if (reference == null) return null
        val textPosition = reference.textPosition
        val textLength = reference.textLength
        if (textPosition != null && textLength != null && textLength > 0) {
            return textPosition.coerceAtLeast(0).toDouble() / textLength * normalizedLength
        }
        return reference.progression
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0.0, 1.0)
            ?.times(normalizedLength)
    }

    private fun storedRangeAgreesWithReference(
        storedStart: Int,
        sourceLength: Int,
        exact: String,
        reference: HighlightTextReference?
    ): Boolean {
        val textPosition = reference?.textPosition ?: return true
        val textLength = reference.textLength?.takeIf { it > 0 } ?: return true
        if (sourceLength <= 0) return true
        val storedProgression = storedStart.toDouble() / sourceLength
        val locatorProgression = textPosition.toDouble() / textLength
        val quoteTolerance = normalize(exact).length.toDouble() / sourceLength * 2.0
        return abs(storedProgression - locatorProgression) <= maxOf(0.02, quoteTolerance)
    }

    private fun equivalent(first: String, second: String): Boolean =
        normalize(first) == normalize(second)

    private fun normalize(value: String): String = normalizeWithOffsets(value).value

    private fun normalizeWithOffsets(value: String): NormalizedText {
        val simplified = ChineseConverter.convert(value, "simplified")
        val normalized = StringBuilder(simplified.length)
        val offsets = ArrayList<Int>(simplified.length)
        simplified.forEachIndexed { index, character ->
            if (!character.isAnchorWhitespace()) {
                normalized.append(character)
                offsets += index
            }
        }
        return NormalizedText(normalized.toString(), offsets.toIntArray())
    }

    private fun Char.isAnchorWhitespace(): Boolean =
        isWhitespace() || this == '\u00A0' || this == '\u200B' || this == '\u200C' ||
            this == '\u200D' || this == '\u2060' || this == '\uFEFF'

    private fun commonSuffixLength(source: String, expected: String): Int {
        var count = 0
        while (count < source.length && count < expected.length &&
            source[source.lastIndex - count] == expected[expected.lastIndex - count]
        ) count++
        return count
    }

    private fun commonPrefixLength(source: String, expected: String): Int {
        var count = 0
        while (count < source.length && count < expected.length && source[count] == expected[count]) {
            count++
        }
        return count
    }

    private const val CONTEXT_LENGTH = 32
}

internal fun findOverlappingResolvedNotes(
    chapterText: CharSequence,
    notes: List<Note>,
    chapterIndex: Int,
    storedStart: Int,
    storedEnd: Int,
    selectedText: String,
    startLocatorJson: String?,
    endLocatorJson: String?
): List<Note> {
    val reference = parseHighlightTextReference(
        startLocatorJson = startLocatorJson,
        endLocatorJson = endLocatorJson,
        selectedText = selectedText
    )
    val selectionRange = HighlightAnchorResolver.resolve(
        chapterText = chapterText,
        storedStart = storedStart,
        storedEnd = storedEnd,
        selectedText = selectedText,
        reference = reference
    ) ?: return emptyList()
    return notes.filter { note ->
        note.chapterIndex == chapterIndex &&
            note.startPosition < selectionRange.end &&
            note.endPosition > selectionRange.start
    }
}

internal fun findOverlappingResolvedNote(
    chapterText: CharSequence,
    notes: List<Note>,
    chapterIndex: Int,
    storedStart: Int,
    storedEnd: Int,
    selectedText: String,
    startLocatorJson: String?,
    endLocatorJson: String?
): Note? = findOverlappingResolvedNotes(
    chapterText,
    notes,
    chapterIndex,
    storedStart,
    storedEnd,
    selectedText,
    startLocatorJson,
    endLocatorJson
).firstOrNull()

internal fun resolveReaderNote(note: Note, readerChapterText: CharSequence): Note? {
    val reference = parseHighlightTextReference(
        startLocatorJson = note.startLocatorJson,
        endLocatorJson = note.endLocatorJson,
        selectedText = note.selectedText
    )
    val range = HighlightAnchorResolver.resolve(
        chapterText = readerChapterText,
        storedStart = note.startPosition,
        storedEnd = note.endPosition,
        selectedText = note.selectedText,
        reference = reference
    ) ?: return null
    return note.copy(startPosition = range.start, endPosition = range.end)
}

internal fun parseHighlightTextReference(
    startLocatorJson: String?,
    endLocatorJson: String?,
    selectedText: String
): HighlightTextReference? {
    val start = startLocatorJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    val end = endLocatorJson?.let { runCatching { JSONObject(it) }.getOrNull() }
    val version = start.optInt("version", 1)
    val exact = start.optString("exact").takeIf { it.isNotBlank() } ?: selectedText
    val prefix = start.optString("prefix")
    val suffix = if (version >= 2) start.optString("suffix") else ""
    val textPosition = when {
        start.has("textPosition") -> start.optInt("textPosition").coerceAtLeast(0)
        else -> null
    }
    val textLength = when {
        start.has("textLength") -> start.optInt("textLength").takeIf { it > 0 }
        end?.has("textLength") == true -> end.optInt("textLength").takeIf { it > 0 }
        else -> null
    }
    val progression = start.optDouble("progression", Double.NaN).takeIf { it.isFinite() }
    return HighlightTextReference(
        exact = exact,
        prefix = prefix,
        suffix = suffix,
        textPosition = textPosition,
        textLength = textLength,
        progression = progression
    )
}

internal fun createHighlightLocatorPair(
    chapterText: CharSequence,
    startPosition: Int,
    endPosition: Int,
    selectedText: String,
    href: String? = null,
    progression: Double? = null
): Pair<String, String> {
    val text = chapterText.toString()
    val start = startPosition.coerceIn(0, text.length)
    val end = endPosition.coerceIn(start, text.length)
    val exact = selectedText.ifBlank { text.substring(start, end) }
    val prefix = text.substring(maxOf(0, start - 32), start)
    val suffix = text.substring(end, minOf(text.length, end + 32))
    val resolvedProgression = progression
        ?: start.toDouble() / text.length.coerceAtLeast(1).toDouble()

    fun locator(position: Int): String = JSONObject()
        .put("version", 2)
        .apply { if (!href.isNullOrBlank()) put("href", href) }
        .put("textPosition", position)
        .put("textLength", text.length)
        .put("exact", exact)
        .put("prefix", prefix)
        .put("suffix", suffix)
        .put("progression", resolvedProgression.coerceIn(0.0, 1.0))
        .toString()

    return locator(start) to locator(end)
}
