package com.huangder.lumibooks.util.parser

import com.huangder.lumibooks.domain.model.BookFormat

internal data class BookNameMetadata(val title: String? = null, val author: String? = null) {
    fun withFallback(other: BookNameMetadata) =
        BookNameMetadata(title.usableBookTitle() ?: other.title.usableBookTitle(),
            author.usableBookAuthor() ?: other.author.usableBookAuthor())
}

internal fun String?.usableBookTitle(): String? = this?.trim()?.takeIf(String::isNotEmpty)

internal fun String?.usableBookAuthor(): String? =
    usableBookTitle()?.takeUnless { it.lowercase() in unknownAuthors }

private val unknownAuthors = setOf(
    "未知作者", "作者不详", "作者不詳", "著者不明", "저자 불명", "unknown author", "unknown"
)

internal object BookFilenameMetadata {
    private val authorLabel = Regex("""作者[\s\p{Zs}]*[:：]""")
    private val bookBrackets = Regex("""^《([^《》]+)》(.*)$""")
    private val separators = Regex("""[\s\p{Zs}]+(?:[-–—]|by)[\s\p{Zs}]+""", RegexOption.IGNORE_CASE)
    private val editionSuffix = Regex(
        """(?:全集|全本|完结|完結|完整版|修订版|修訂版|珍藏版|第[\s\p{Zs}]*[\p{N}一二三四五六七八九十百零〇两兩]+[\s\p{Zs}]*[卷册冊版部]|[\p{N}]+)"""
    )

    fun stem(name: String): String {
        val extension = name.substringAfterLast('.', "")
        return (if (extension.lowercase() in BookFormat.importExtensions) name.substringBeforeLast('.') else name).trim()
    }

    fun read(name: String): BookNameMetadata {
        val stem = stem(name)
        val fallback = BookNameMetadata(title = stem.usableBookTitle())
        val labels = authorLabel.findAll(stem).take(2).toList()
        if (labels.isNotEmpty()) {
            val label = labels.singleOrNull() ?: return fallback
            return split(stem.substring(0, label.range.first), stem.substring(label.range.last + 1), explicit = true)
                ?: fallback
        }

        bookBrackets.matchEntire(stem)?.let { match ->
            val title = match.groupValues[1]
            val suffix = match.groupValues[2]
            if (suffix.isBlank()) return BookNameMetadata(title = title.usableBookTitle())
            // Bracketed titles may use either an adjacent author or the normal spaced separator.
            val author = separators.find(suffix)?.takeIf { it.range.first == 0 }
                ?.let { suffix.substring(it.range.last + 1) } ?: suffix
            return split(title, author) ?: fallback
        }

        val separator = separators.findAll(stem).take(2).toList().singleOrNull() ?: return fallback
        return split(stem.substring(0, separator.range.first), stem.substring(separator.range.last + 1))
            ?: fallback
    }

    private fun split(rawTitle: String, rawAuthor: String, explicit: Boolean = false): BookNameMetadata? {
        val trimmedTitle = rawTitle.trim()
        val bracketed = bookBrackets.matchEntire(trimmedTitle)?.takeIf { it.groupValues[2].isBlank() }
        val title = (bracketed?.groupValues?.get(1) ?: trimmedTitle).usableBookTitle() ?: return null
        val author = rawAuthor.usableBookAuthor() ?: return null
        if (!explicit && (editionSuffix.matches(author) || separators.containsMatchIn(author) ||
                author.any { it in "()（）[]［］【】《》" } || author.first() in "-–—_")
        ) return null
        return BookNameMetadata(title, author)
    }
}
