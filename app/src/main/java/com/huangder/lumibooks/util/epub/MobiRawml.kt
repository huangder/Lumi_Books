package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.nio.charset.Charset

/**
 * Shared rawml byte-level utilities for classic MOBI7 processing.
 *
 * Classic MOBI7 stores the whole book as a single "rawml" text (HTML-like
 * markup with Mobipocket extensions: `<sent>`, `<mbp:pagebreak/>`,
 * `<img recindex="N">`, `<a filepos="N">`). Chapters are split heuristically:
 * `<mbp:pagebreak/>` boundaries first, then h1-h6 heading boundaries, then a
 * single chapter. `filepos` values are byte offsets into the rawml, which is
 * why chapter ranges are kept as rawml byte ranges.
 */
internal object MobiRawml {

    /** Case-insensitive ASCII substring search within [from, to). Returns -1 when absent. */
    fun indexOfAscii(haystack: ByteArray, needle: String, from: Int = 0, to: Int = haystack.size): Int {
        if (needle.isEmpty()) return from.coerceIn(0, haystack.size)
        val pattern = needle.lowercase()
        val p0 = pattern[0].code.toByte()
        var i = from.coerceAtLeast(0)
        val limit = to.coerceAtMost(haystack.size)
        while (i <= limit - pattern.length) {
            if (eqAscii(haystack[i], p0)) {
                var matches = true
                for (k in 1 until pattern.length) {
                    if (!eqAscii(haystack[i + k], pattern[k].code.toByte())) {
                        matches = false
                        break
                    }
                }
                if (matches) return i
            }
            i++
        }
        return -1
    }

    private fun eqAscii(a: Byte, b: Byte): Boolean {
        if (a == b) return true
        val ua = a.toInt() and 0xFF
        val ub = b.toInt() and 0xFF
        return (ua in 'A'.code..'Z'.code && ua + 32 == ub) || (ub in 'A'.code..'Z'.code && ub + 32 == ua)
    }

    /**
     * Finds a `<tag` opening where the character right after the tag name is
     * whitespace, '/' or '>'. Returns the tag start index or -1.
     */
    fun findTagOpen(haystack: ByteArray, tag: String, from: Int = 0, to: Int = haystack.size): Int {
        val limit = to.coerceAtMost(haystack.size)
        var search = from.coerceAtLeast(0)
        while (true) {
            val index = indexOfAscii(haystack, "<$tag", search, limit)
            if (index < 0) return -1
            val after = index + 1 + tag.length
            if (after >= limit) return -1
            val c = haystack[after].toInt() and 0xFF
            if (c == '>'.code || c == '/'.code || c == ' '.code || c == '\t'.code ||
                c == '\r'.code || c == '\n'.code
            ) {
                return index
            }
            search = index + 1
        }
    }

    /** Returns the index just after the closing '>' of the tag starting at [tagStart]. */
    fun tagEnd(haystack: ByteArray, tagStart: Int, to: Int = haystack.size): Int {
        val limit = to.coerceAtMost(haystack.size)
        var i = tagStart + 1
        var inQuote = false
        var quoteChar = 0.toByte()
        while (i < limit) {
            val c = haystack[i]
            if (inQuote) {
                if (c == quoteChar) inQuote = false
            } else if (c == '"'.code.toByte() || c == '\''.code.toByte()) {
                inQuote = true
                quoteChar = c
            } else if (c == '>'.code.toByte()) {
                return i + 1
            }
            i++
        }
        return limit
    }

    /**
     * Splits rawml into chapter byte ranges.
     *
     * Priority: `<mbp:pagebreak/>` boundaries; otherwise h1-h6 heading
     * boundaries (the first heading stays with chapter 0); otherwise a single
     * chapter covering the whole rawml.
     */
    fun splitChapters(rawml: ByteArray): List<Pair<Int, Int>> {
        val size = rawml.size
        val pagebreaks = ArrayList<Int>()
        var from = 0
        while (true) {
            val index = findTagOpen(rawml, "mbp:pagebreak", from, size)
            if (index < 0) break
            pagebreaks += index
            from = tagEnd(rawml, index, size)
        }
        if (pagebreaks.isNotEmpty()) {
            val ranges = ArrayList<Pair<Int, Int>>(pagebreaks.size + 1)
            var start = 0
            for (pb in pagebreaks) {
                if (pb > start) ranges += start to pb
                start = pb
            }
            if (start < size) ranges += start to size
            return ranges.ifEmpty { listOf(0 to size) }
        }

        val headings = ArrayList<Int>()
        for (level in 1..6) {
            var search = 0
            while (true) {
                val index = findTagOpen(rawml, "h$level", search, size)
                if (index < 0) break
                headings += index
                search = tagEnd(rawml, index, size)
            }
        }
        headings.sort()
        if (headings.size > 1) {
            val ranges = ArrayList<Pair<Int, Int>>(headings.size)
            var start = 0
            for (heading in headings.drop(1)) {
                if (heading > start) ranges += start to heading
                start = heading
            }
            if (start < size) ranges += start to size
            return ranges.ifEmpty { listOf(0 to size) }
        }
        return listOf(0 to size)
    }

    /**
     * Builds a cleaned HTML fragment for one chapter.
     *
     * [resolveImage] maps a recindex to an image src (session URL, data URI or
     * a `recindex:N` placeholder for the Canvas engine); returning null drops
     * the image. [resolveLink] maps a filepos to an href; returning null keeps
     * the anchor text without an href.
     */
    fun chapterFragment(
        rawml: ByteArray,
        range: Pair<Int, Int>,
        chapterIndex: Int,
        charset: Charset,
        resolveImage: (Int) -> String?,
        resolveLink: (Long) -> String?
    ): String {
        var start = range.first.coerceIn(0, rawml.size)
        val end = range.second.coerceIn(start, rawml.size)
        if (chapterIndex == 0) {
            val bodyIndex = findTagOpen(rawml, "body", start, end)
            if (bodyIndex >= 0) start = tagEnd(rawml, bodyIndex, end)
        }
        if (start >= end) return ""
        var text = String(rawml, start, end - start, charset)
        text = text.replace(Regex("</body>\\s*</html>\\s*$", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("</sent\\s*>", RegexOption.IGNORE_CASE), "</span>")
        text = text.replace(Regex("<sent(\\s[^>]*)?>", RegexOption.IGNORE_CASE)) { match ->
            "<span" + match.groupValues[1] + ">"
        }
        text = text.replace(Regex("</mbp:section\\s*>", RegexOption.IGNORE_CASE), "</div>")
        text = text.replace(Regex("<mbp:section(\\s[^>]*)?>", RegexOption.IGNORE_CASE)) { match ->
            "<div" + match.groupValues[1] + ">"
        }
        text = text.replace(Regex("<mbp:pagebreak\\s*/?>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)) { tagMatch ->
            val tag = tagMatch.value
            val recindex = Regex("""recindex\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)?.toIntOrNull()
            if (recindex == null) {
                ""
            } else {
                val url = resolveImage(recindex)
                if (url == null) "" else Regex("""\brecindex\s*=\s*["']?\d+["']?""", RegexOption.IGNORE_CASE)
                    .replaceFirst(tag, "src=\"$url\"")
            }
        }
        text = text.replace(Regex("<a\\b[^>]*>", RegexOption.IGNORE_CASE)) { tagMatch ->
            val tag = tagMatch.value
            val filepos = Regex("""filepos\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)?.toLongOrNull()
            if (filepos == null) {
                tag
            } else {
                val href = resolveLink(filepos)
                if (href == null) "<a>" else Regex("""\bfilepos\s*=\s*["']?\d+["']?""", RegexOption.IGNORE_CASE)
                    .replaceFirst(tag, "href=\"$href\"")
            }
        }
        return text.trim()
    }

    /** Converts a rawml byte offset (relative to the whole rawml) into a char offset in the decoded chapter. */
    fun byteToCharOffset(rawml: ByteArray, range: Pair<Int, Int>, charset: Charset, byteOffset: Int): Int {
        if (range.first >= range.second) return 0
        val slice = rawml.copyOfRange(range.first, range.second)
        val text = String(slice, charset)
        val target = (byteOffset - range.first).coerceIn(0, slice.size)
        var pos = 0
        for ((index, character) in text.withIndex()) {
            val encodedLength = character.toString().toByteArray(charset).size
            if (pos + encodedLength > target) return index
            pos += encodedLength
        }
        return text.length
    }

    /** Extracts a chapter title from the first h1-h6 heading inside a cleaned fragment. */
    fun chapterTitle(fragment: String, fallback: String): String {
        val heading = Regex(
            """<h[1-6][^>]*>(.*?)</h[1-6]>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
            .find(fragment)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace(Regex("&(#\\d+|#x[0-9a-fA-F]+|amp|lt|gt|quot|nbsp);")) { match ->
                when (val entity = match.groupValues[1]) {
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "nbsp" -> " "
                    else -> if (entity.startsWith("#x")) {
                        runCatching { java.lang.Integer.parseInt(entity.substring(2), 16) }
                            .getOrNull()?.toChar()?.toString() ?: match.value
                    } else if (entity.startsWith("#")) {
                        entity.substring(1).toIntOrNull()?.toChar()?.toString() ?: match.value
                    } else {
                        match.value
                    }
                }
            }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return heading ?: fallback
    }

    /** Extracts plain search text from a cleaned fragment, mirroring the EPUB extraction pipeline. */
    fun searchText(fragment: String): String {
        val body = Jsoup.parseBodyFragment(fragment).body()
        val text = StringBuilder()
        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(node: org.jsoup.nodes.Node, depth: Int) {
                    if (node !is TextNode) return
                    text.append(node.wholeText)
                }

                override fun tail(node: org.jsoup.nodes.Node, depth: Int) = Unit
            },
            body
        )
        return text.toString()
    }

}
