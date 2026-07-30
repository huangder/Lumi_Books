package com.huangder.lumibooks.util.parser

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
