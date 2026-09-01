package com.huangder.lumibooks.util.parser

internal object TxtChapterStructure {
    const val MAX_CHAPTER_CHARS = 32_000
    const val PATTERN_COUNT = 9
    val chapterPatterns = listOf(
        Regex("^第[一二三四五六七八九十百千零\\d]+[章节回卷话]"),
        Regex("^[卷篇][一二三四五六七八九十百千零\\d]+[章回]?"),
        Regex("^Chapter\\s+\\d+", RegexOption.IGNORE_CASE),
        Regex("^[一二三四五六七八九十百千零〇两]+$"),
        Regex("^第\\d+章"),
        Regex("^(?:序章|楔子|前言|终章|尾声|后记|番外)(?:$|[：:：、，,\\s].*)"),
        Regex("^(?:Section|Episode|Part|Volume|Vol\\.|Book)\\s+(?:\\d+|[IVXLCDM]+)(?:$|[：:：、，,\\s].*)", RegexOption.IGNORE_CASE),
        Regex("^(?:第|제\\s*)[0-9０-９一二三四五六七八九十百千万零〇两]+\\s*(?:話|節|장|화)(?:$|[：:：、，,\\s].*)", RegexOption.IGNORE_CASE),
        Regex("^(?:第|제\\s*)[0-9０-９一二三四五六七八九十百千万零〇两]+\\s*(?:卷|篇|部|巻|권)(?:$|[：:：、，,\\s].*)", RegexOption.IGNORE_CASE)
    )
    val decoratedHeadingPattern = Regex("^<[^<>\\r\\n]{1,48}>$")

    fun matchingPatternIndex(line: String): Int? {
        if (line.isEmpty()) return null

        if (line[0] == '第') {
            var index = 1
            while (index < line.length && line[index].isStrictChapterNumberCharacter()) index++
            if (index > 1 && index < line.length && line[index] in STRICT_CHAPTER_SUFFIXES) return 0
        }

        // Some Chinese TXT sources prefix every chapter with a short series or volume label,
        // for example "剑中仙 第一章：..." or "===剑中仙 第二章：...". Only accept a marker
        // near the beginning when it is separated from that label, so body sentences such as
        // "他说：第一章..." are not treated as headings.
        prefixedChapterIndex(line)?.let { return 0 }

        if (line[0] == '卷' || line[0] == '篇') {
            var index = 1
            while (index < line.length && line[index].isStrictChapterNumberCharacter()) index++
            if (index > 1) return 1
        }

        if (line.regionMatches(0, "Chapter", 0, 7, ignoreCase = true)) {
            var index = 7
            val whitespaceStart = index
            while (index < line.length && line[index] in REGEX_WHITESPACE_CHARACTERS) index++
            val digitStart = index
            while (index < line.length && line[index] in '0'..'9') index++
            if (whitespaceStart < digitStart && digitStart < index) return 2
        }

        if (line.all { it in STANDALONE_CHAPTER_NUMBER_CHARACTERS }) return 3
        if (chapterPatterns[5].matches(line)) return 5
        if (chapterPatterns[6].matches(line)) return 6
        if (chapterPatterns[7].matches(line)) return 7
        if (chapterPatterns[8].matches(line)) return 8
        return null
    }

    fun isVolumeHeading(line: String): Boolean {
        val candidate = line.trim()
        if (candidate.isEmpty()) return false
        return Regex(
            "^(?:第\\s*[0-9０-９一二三四五六七八九十百千万零〇两]+\\s*[卷篇部巻]|[卷篇部巻]\\s*[0-9０-９一二三四五六七八九十百千万零〇两]+|(?:Volume|Vol\\.|Book|Part)\\s*(?:[0-9０-９]+|[IVXLCDM]+)|제\\s*[0-9０-９]+\\s*권)(?:$|[：:：、，,\\s].*)",
            RegexOption.IGNORE_CASE
        ).matches(candidate)
    }

    fun isDecoratedHeading(line: String): Boolean =
        line.length in 3..50 &&
            line.first() == '<' &&
            line.last() == '>' &&
            line.substring(1, line.lastIndex).none { it == '<' || it == '>' || it == '\r' || it == '\n' }

    private fun prefixedChapterIndex(line: String): Int? {
        val searchEnd = minOf(line.length, MAX_PREFIXED_CHAPTER_MARKER_INDEX)
        for (index in 1 until searchEnd) {
            if (line[index] != '第') continue
            if (line[index - 1] !in PREFIXED_HEADING_SEPARATORS) continue

            var cursor = index + 1
            while (cursor < line.length && line[cursor].isStrictChapterNumberCharacter()) cursor++
            if (cursor > index + 1 && cursor < line.length && line[cursor] in STRICT_CHAPTER_SUFFIXES) {
                return index
            }
        }
        return null
    }

    fun headingLines(text: String): List<String> = text.lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.isNotEmpty() &&
                (isDecoratedHeading(line) || matchingPatternIndex(line) != null)
        }
        .toList()

    fun mayChange(oldText: String, newText: String): Boolean =
        headingLines(oldText) != headingLines(newText) ||
            (oldText.length < MAX_CHAPTER_CHARS) != (newText.length < MAX_CHAPTER_CHARS)

    fun mayChange(oldText: String, newText: String, rule: TxtTocRule?): Boolean {
        if (rule == null) return mayChange(oldText, newText)
        val compiled = TxtTocRuleCompiler.compile(rule).getOrNull() ?: return true
        fun headings(text: String) = text.lineSequence().map(String::trim)
            .mapNotNull { compiled.match(it)?.sourceLine }
            .toList()
        return headings(oldText) != headings(newText) ||
            (oldText.length < MAX_CHAPTER_CHARS) != (newText.length < MAX_CHAPTER_CHARS)
    }

    private fun Char.isStrictChapterNumberCharacter(): Boolean =
        this in '0'..'9' || this in STRICT_CHAPTER_NUMBER_CHARACTERS

    private const val STRICT_CHAPTER_NUMBER_CHARACTERS = "一二三四五六七八九十百千万零〇两壹贰叁肆伍陆柒捌玖拾佰仟萬０１２３４５６７８９"
    private const val STANDALONE_CHAPTER_NUMBER_CHARACTERS = "一二三四五六七八九十百千零〇两壹贰叁肆伍陆柒捌玖拾佰仟萬０１２３４５６７８９"
    private const val STRICT_CHAPTER_SUFFIXES = "章节回卷话節"
    private const val REGEX_WHITESPACE_CHARACTERS = " \t\n\u000B\u000C\r"
    private const val MAX_PREFIXED_CHAPTER_MARKER_INDEX = 32
    private const val PREFIXED_HEADING_SEPARATORS = " \t=|-_·"
}

data class TxtTextMatch(
    val start: Int,
    val endExclusive: Int
)

sealed interface TxtEditOperation

data class TxtSetChapterText(
    val chapterIndex: Int,
    val text: String
) : TxtEditOperation

data class TxtReplaceText(
    val chapterIndex: Int?,
    val query: String,
    val replacement: String,
    val ignoreCase: Boolean
) : TxtEditOperation

data class TxtReplaceRange(
    val chapterIndex: Int,
    val start: Int,
    val endExclusive: Int,
    val replacement: String
) : TxtEditOperation

data class TxtRewriteResult(
    val success: Boolean,
    val changedChapterCount: Int = 0,
    val errorMessage: String? = null
)

data class TxtOffsetMigrationStep(
    val matches: List<TxtTextMatch>,
    val replacementLength: Int
)

fun computeMinimalTxtReplacement(
    chapterIndex: Int,
    oldText: String,
    newText: String
): TxtReplaceRange? {
    if (oldText == newText) return null
    var prefix = 0
    val sharedLength = minOf(oldText.length, newText.length)
    while (prefix < sharedLength && oldText[prefix] == newText[prefix]) prefix++
    if (prefix > 0 && prefix < oldText.length && prefix < newText.length &&
        (oldText[prefix].isLowSurrogate() || newText[prefix].isLowSurrogate())
    ) {
        prefix--
    }

    var suffix = 0
    val maxSuffix = sharedLength - prefix
    while (suffix < maxSuffix &&
        oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]
    ) suffix++
    val oldSuffixStart = oldText.length - suffix
    val newSuffixStart = newText.length - suffix
    if (suffix > 0 && oldSuffixStart > prefix && newSuffixStart > prefix &&
        (oldText[oldSuffixStart].isLowSurrogate() || newText[newSuffixStart].isLowSurrogate())
    ) {
        suffix--
    }

    return TxtReplaceRange(
        chapterIndex = chapterIndex,
        start = prefix,
        endExclusive = oldText.length - suffix,
        replacement = newText.substring(prefix, newText.length - suffix)
    )
}

fun mapTxtOffsetThroughSteps(
    originalOffset: Int,
    steps: List<TxtOffsetMigrationStep>,
    endBias: Boolean
): Int {
    var offset = originalOffset.coerceAtLeast(0)
    steps.forEach { step ->
        var delta = 0
        var mappedInsideMatch = false
        step.matches.forEach { match ->
            when {
                offset < match.start -> Unit
                offset >= match.endExclusive -> {
                    delta += step.replacementLength - (match.endExclusive - match.start)
                }
                else -> {
                    offset = match.start + delta + if (endBias) step.replacementLength else 0
                    mappedInsideMatch = true
                }
            }
            if (mappedInsideMatch) return@forEach
        }
        if (!mappedInsideMatch) offset += delta
    }
    return offset.coerceAtLeast(0)
}

fun applyTxtEditOperations(
    chapterIndex: Int,
    originalText: String,
    operations: List<TxtEditOperation>
): String {
    var text = originalText
    operations.forEach { operation ->
        text = when (operation) {
            is TxtSetChapterText -> {
                if (operation.chapterIndex == chapterIndex) operation.text else text
            }
            is TxtReplaceText -> {
                if (operation.chapterIndex == null || operation.chapterIndex == chapterIndex) {
                    replaceTxtLiteral(
                        text = text,
                        query = operation.query,
                        replacement = operation.replacement,
                        ignoreCase = operation.ignoreCase
                    ).first
                } else {
                    text
                }
            }
            is TxtReplaceRange -> {
                if (operation.chapterIndex == chapterIndex &&
                    operation.start >= 0 &&
                    operation.endExclusive in operation.start..text.length
                ) {
                    text.replaceRange(operation.start, operation.endExclusive, operation.replacement)
                } else {
                    text
                }
            }
        }
    }
    return text
}

fun findTxtLiteralMatches(
    text: String,
    query: String,
    ignoreCase: Boolean
): List<TxtTextMatch> {
    if (query.isEmpty() || text.isEmpty() || query.length > text.length) return emptyList()
    val matches = ArrayList<TxtTextMatch>()
    var start = 0
    while (start <= text.length - query.length) {
        val index = text.indexOf(query, startIndex = start, ignoreCase = ignoreCase)
        if (index < 0) break
        matches += TxtTextMatch(index, index + query.length)
        start = index + query.length
    }
    return matches
}

fun replaceTxtLiteral(
    text: String,
    query: String,
    replacement: String,
    ignoreCase: Boolean
): Pair<String, Int> {
    val matches = findTxtLiteralMatches(text, query, ignoreCase)
    if (matches.isEmpty()) return text to 0

    val capacity = text.length + (replacement.length - query.length) * matches.size
    val result = StringBuilder(capacity.coerceAtLeast(0))
    var copiedUntil = 0
    matches.forEach { match ->
        result.append(text, copiedUntil, match.start)
        result.append(replacement)
        copiedUntil = match.endExclusive
    }
    result.append(text, copiedUntil, text.length)
    return result.toString() to matches.size
}
