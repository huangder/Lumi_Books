package com.huangder.lumibooks.highlight

import com.google.re2j.Pattern

/** Recognizes only whole-quotation regexes; content-specific regexes keep RE2 semantics. */
internal object DialogueQuoteMatcher {
    private val quotePairs = listOf('“' to '”', '‘' to '’', '「' to '」', '『' to '』', '"' to '"')
    private val repetition = Regex("(?:[+*]|\\{\\d+(?:,\\d*)?\\})\\??")

    private data class Branch(val open: Char, val close: Char, val excludedLineBreaks: String, val pattern: Pattern)

    /** Null means this is an ordinary regex, rather than a supported quotation rule. */
    fun ranges(text: CharSequence, expression: String): Sequence<IntRange>? {
        val branches = alternatives(expression)?.map { parseBranch(it) ?: return null } ?: return null
        return sequence {
            // Alternatives must behave like regex find(): do not emit nested/overlapping matches.
            val iterators = branches.map { scan(text, it).iterator() }
            val candidates = iterators.map { if (it.hasNext()) it.next() else null }.toMutableList()
            var end = 0
            while (true) {
                val next = candidates.indices.filter { candidates[it] != null }
                    .minByOrNull { candidates[it]!!.first } ?: break
                val range = candidates[next]!!
                if (range.first >= end) {
                    yield(range)
                    end = range.last + 1
                }
                candidates[next] = if (iterators[next].hasNext()) iterators[next].next() else null
            }
        }
    }

    private fun parseBranch(expression: String): Branch? {
        for ((open, close) in quotePairs) {
            val openToken = listOf(open.toString(), "[$open]").firstOrNull(expression::startsWith) ?: continue
            val closeToken = listOf(close.toString(), "[$close]").firstOrNull(expression::endsWith) ?: continue
            if (expression.length <= openToken.length + closeToken.length) continue
            val body = alternatives(expression.substring(openToken.length, expression.length - closeToken.length))
                ?.singleOrNull() ?: return null
            var offset = 0
            var atom: String? = null
            var excludedLineBreaks = ""
            while (offset < body.length) {
                val atomEnd = when {
                    body[offset] == '.' -> offset + 1
                    body.startsWith("[^", offset) -> body.indexOf(']', offset + 2).takeIf { it >= 0 }?.plus(1)
                    body.startsWith("[\\s\\S]", offset) -> offset + 6
                    else -> null
                } ?: return null
                val nextAtom = body.substring(offset, atomEnd)
                // Repeated identical atoms include the chunks produced by HighlightRuleCodec.
                if (atom != null && atom != nextAtom) return null
                atom = nextAtom
                if (nextAtom.startsWith("[^")) {
                    val excluded = nextAtom.substring(2, nextAtom.lastIndex)
                        .replace("\\n", "\n").replace("\\r", "\r")
                    if (excluded.isEmpty() || excluded.any { it != open && it != close && it != '\n' && it != '\r' }) {
                        return null
                    }
                    excludedLineBreaks = excluded.filter { it == '\n' || it == '\r' }
                } else {
                    excludedLineBreaks = if (nextAtom == ".") "\n" else ""
                }
                val quantifier = repetition.find(body, atomEnd)?.takeIf { it.range.first == atomEnd } ?: return null
                offset = quantifier.range.last + 1
            }
            return Branch(open, close, excludedLineBreaks, Pattern.compile(expression))
        }
        return null
    }

    private fun scan(text: CharSequence, branch: Branch): Sequence<IntRange> = sequence {
        var start = -1
        var depth = 0
        for (index in text.indices) {
            val char = text[index]
            if (char in branch.excludedLineBreaks) {
                start = -1
                depth = 0
            } else if (char == branch.close && depth > 0) {
                depth--
                if (depth == 0) {
                    // Mask only inner delimiters for validation, retaining original length and
                    // line breaks. The result still obeys the regex's empty/length limits.
                    val candidate = buildString(index - start + 1) {
                        append(branch.open)
                        for (inner in start + 1 until index) {
                            val value = text[inner]
                            append(if (value == branch.open || value == branch.close) 'a' else value)
                        }
                        append(branch.close)
                    }
                    if (branch.pattern.matcher(candidate).matches()) yield(start..index)
                    start = -1
                }
            } else if (char == branch.open) {
                if (depth == 0) start = index
                depth++
            }
        }
    }

    /** Flatten grouping/alternation only outside character classes and escaped literals. */
    private fun alternatives(expression: String, nesting: Int = 0): List<String>? {
        if (nesting > 16) return null
        var depth = 0
        var inClass = false
        var escaped = false
        var groupEnd = -1
        var start = 0
        val parts = mutableListOf<String>()
        expression.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '[' -> inClass = true
                char == ']' -> inClass = false
                inClass -> Unit
                char == '(' -> depth++
                char == ')' -> { depth--; if (depth == 0 && groupEnd < 0) groupEnd = index }
                char == '|' && depth == 0 -> { parts += expression.substring(start, index); start = index + 1 }
            }
        }
        if (parts.isNotEmpty()) {
            parts += expression.substring(start)
            return parts.flatMap { alternatives(it, nesting + 1) ?: return null }
        }
        if (expression.startsWith('(') && groupEnd == expression.lastIndex) {
            val prefix = if (expression.startsWith("(?:")) 3 else 1
            return alternatives(expression.substring(prefix, expression.lastIndex), nesting + 1)
        }
        return listOf(expression)
    }
}
