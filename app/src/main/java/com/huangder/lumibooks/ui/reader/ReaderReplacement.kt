package com.huangder.lumibooks.ui.reader

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.replacement.ReplacementProgram
import com.huangder.lumibooks.replacement.ReplacementEdit
import com.huangder.lumibooks.replacement.ReplacementResult
import com.huangder.lumibooks.replacement.ReplacementSegment
import org.json.JSONObject

internal class ReplacementFormattingEdits {
    private val batches = mutableListOf<MutableList<Pair<ReplacementEdit, String>>>()

    fun record(start: Int, previous: String, value: String) {
        val batch = batches.lastOrNull()?.takeIf { start + previous.length <= it.last().first.start }
            ?: mutableListOf<Pair<ReplacementEdit, String>>().also(batches::add)
        batch.add(ReplacementEdit(start, start + previous.length, value) to previous)
    }

    fun stages(reverse: Boolean = false): List<List<ReplacementEdit>> =
        if (!reverse) batches.map { batch -> batch.asReversed().map { it.first } } else
            batches.asReversed().map { batch ->
                var delta = 0
                batch.asReversed().map { (edit, previous) ->
                    val start = edit.start + delta
                    delta += edit.value.length - previous.length
                    ReplacementEdit(start, start + edit.value.length, previous)
                }
            }
}

internal fun BookFormat?.supportsReplacementRules(): Boolean =
    this == BookFormat.TXT || this == BookFormat.EPUB || this == BookFormat.MOBI

internal fun ReplacementResult.styledDisplay(text: CharSequence): CharSequence {
    if (!offsets.changed) return text
    if (text !is Spanned) return display
    return SpannableStringBuilder(text).apply {
        stages.forEach { edits ->
            edits.asReversed().forEach { edit -> replace(edit.start, edit.end, edit.value) }
        }
    }
}

/** Canvas renders decoded paragraphs and uses ReplacementSpan for inline media. */
internal fun ReplacementProgram.applyToReaderText(
    text: CharSequence,
    txt: Boolean,
    previewRule: String? = null
): ReplacementResult {
    val source = text.toString()
    if (txt) return apply(source, previewRule = previewRule)
    val protected = (text as? Spanned)?.let { spanned ->
        spanned.getSpans(0, spanned.length, ReplacementSpan::class.java)
            .associate { spanned.getSpanStart(it) to spanned.getSpanEnd(it) }
    }.orEmpty()
    val segments = buildList {
        var start = 0
        var index = 0
        while (index < source.length) {
            val end = protected[index]?.takeIf { it > index }
                ?: if (source[index] == '\n' || source[index] == '\r' || source[index] == '\uFFFC') index + 1 else null
            if (end == null) {
                index++
            } else {
                if (index > start) add(ReplacementSegment(index - start))
                add(ReplacementSegment(end - index, eligible = false))
                index = end
                start = end
            }
        }
        if (index > start) add(ReplacementSegment(index - start))
    }
    return apply(source, segments, previewRule)
}

internal fun ReplacementResult.projectLocator(
    raw: String?,
    toSource: Boolean,
    end: Boolean = false,
    selection: IntRange? = null
): String? {
    if (raw == null || !offsets.changed) return raw
    val locator = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
    if (!locator.has("textPosition")) return raw
    val text = if (toSource) source else display
    fun map(value: Int, end: Boolean): Int =
        (if (toSource) offsets.source(value, end) else offsets.display(value, end)).coerceIn(0, text.length)
    val position = map(locator.optInt("textPosition"), end)
    val quoteStart = selection?.first?.let { map(it, false) } ?: position
    val quoteEnd = selection?.last?.let { map(it + 1, true) } ?: (position + 96).coerceAtMost(text.length)
    locator.remove("domPath")
    locator.remove("textOffset")
    return locator.put("replacementAnchor", true).put("textPosition", position).put("textLength", text.length)
        .put("exact", text.substring(quoteStart, quoteEnd.coerceAtLeast(quoteStart)))
        .put("prefix", text.substring((quoteStart - 32).coerceAtLeast(0), quoteStart))
        .put("suffix", text.substring(quoteEnd, (quoteEnd + 32).coerceAtMost(text.length)))
        .toString()
}
