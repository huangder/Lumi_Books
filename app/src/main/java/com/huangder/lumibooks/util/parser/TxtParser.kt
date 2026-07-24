package com.huangder.lumibooks.util.parser

import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.LinkedHashMap

class TxtParser : BookParser {
    override var paragraphSpacingDp: Float = 0f
    override var firstLineIndentChars: Float = 0f
    override var contentWidth: Int = 0

    private data class TxtChapterEntry(
        val index: Int,
        val title: String,
        val startByte: Long,
        val endByte: Long
    )

    private data class Heading(val title: String, val startByte: Long)
    private data class ByteRange(val startByte: Long, val endByte: Long)
    private data class EncodingInfo(val charset: Charset, val contentStart: Long)

    private var sourceFile: File? = null
    private var encodingInfo = EncodingInfo(Charsets.UTF_8, 0L)
    private var entries: List<TxtChapterEntry> = emptyList()

    private val contentCache = object : LinkedHashMap<Int, String>(6, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
            return size > CONTENT_CACHE_SIZE
        }
    }

    private val htmlCache = object : LinkedHashMap<Int, String>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
            return size > HTML_CACHE_SIZE
        }
    }

    override fun parse(filePath: String): BookContent {
        val file = File(filePath)
        require(file.isFile) { "TXT file not found: $filePath" }

        sourceFile = file
        encodingInfo = detectEncoding(file)
        synchronized(contentCache) { contentCache.clear() }
        synchronized(htmlCache) { htmlCache.clear() }

        val headings = findChapterHeadings(file, encodingInfo)
        entries = if (headings.size >= 2) {
            buildHeadingEntries(file, headings, encodingInfo)
        } else {
            buildFallbackEntries(file, encodingInfo)
        }

        if (entries.isEmpty()) {
            entries = listOf(
                TxtChapterEntry(
                    index = 0,
                    title = file.nameWithoutExtension,
                    startByte = encodingInfo.contentStart,
                    endByte = file.length()
                )
            )
        }

        return BookContent(
            title = file.nameWithoutExtension,
            author = "未知作者",
            chapters = entries.map { entry ->
                Chapter(
                    index = entry.index,
                    title = entry.title,
                    content = "",
                    htmlContent = ""
                )
            }
        )
    }

    private fun detectEncoding(file: File): EncodingInfo {
        val bom = ByteArray(3)
        val bomSize = FileInputStream(file).use { it.read(bom) }
        if (bomSize >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte()) {
            return EncodingInfo(Charsets.UTF_8, 3L)
        }
        if (bomSize >= 2) {
            if (bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte()) {
                return EncodingInfo(Charsets.UTF_16BE, 2L)
            }
            if (bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte()) {
                return EncodingInfo(Charsets.UTF_16LE, 2L)
            }
        }

        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            InputStreamReader(FileInputStream(file), utf8Decoder).use { reader ->
                val buffer = CharArray(STREAM_BUFFER_SIZE)
                while (reader.read(buffer) != -1) {
                    // Decode the whole stream without retaining its contents.
                }
            }
            EncodingInfo(Charsets.UTF_8, 0L)
        } catch (_: CharacterCodingException) {
            EncodingInfo(Charset.forName("GBK"), 0L)
        }
    }

    private fun findChapterHeadings(file: File, encoding: EncodingInfo): List<Heading> {
        for (pattern in CHAPTER_PATTERNS) {
            val matches = mutableListOf<Heading>()
            forEachLinePrefix(file, encoding, encoding.contentStart, file.length()) { start, _, prefix ->
                val line = prefix.trimEnd('\r', '\n')
                if (pattern.containsMatchIn(line)) {
                    matches += Heading(line.trim().take(50), start)
                }
            }
            if (matches.size >= 2) return matches
        }
        return emptyList()
    }

    private fun buildHeadingEntries(
        file: File,
        headings: List<Heading>,
        encoding: EncodingInfo
    ): List<TxtChapterEntry> {
        val result = mutableListOf<TxtChapterEntry>()
        headings.forEachIndexed { headingIndex, heading ->
            val range = ByteRange(
                startByte = heading.startByte,
                endByte = headings.getOrNull(headingIndex + 1)?.startByte ?: file.length()
            )
            val parts = splitRange(file, encoding, range, FALLBACK_TARGET_CHARS, splitAtTarget = false)
            parts.forEachIndexed { partIndex, part ->
                val title = if (partIndex == 0) {
                    heading.title
                } else {
                    "${heading.title}（续${partIndex + 1}）"
                }
                result += TxtChapterEntry(result.size, title, part.startByte, part.endByte)
            }
        }
        return result
    }

    private fun buildFallbackEntries(file: File, encoding: EncodingInfo): List<TxtChapterEntry> {
        val fullRange = ByteRange(encoding.contentStart, file.length())
        val ranges = splitRange(file, encoding, fullRange, FALLBACK_TARGET_CHARS, splitAtTarget = true)
        return RandomAccessFile(file, "r").use { reader ->
            ranges.mapIndexed { index, range ->
                val firstLine = decodePrefix(
                    reader,
                    encoding.charset,
                    range.startByte,
                    range.endByte,
                    TITLE_PREFIX_BYTES
                ).lineSequence().firstOrNull()?.trim().orEmpty().take(30)
                val title = firstLine.ifBlank { "第${index + 1}部分" }
                TxtChapterEntry(index, "第${index + 1}章 $title", range.startByte, range.endByte)
            }
        }
    }

    private fun splitRange(
        file: File,
        encoding: EncodingInfo,
        range: ByteRange,
        targetChars: Int,
        splitAtTarget: Boolean
    ): List<ByteRange> {
        if (range.endByte <= range.startByte) return listOf(range)
        if (!splitAtTarget && range.endByte - range.startByte <= MAX_RAW_CHUNK_BYTES) {
            return listOf(range)
        }

        val result = mutableListOf<ByteRange>()
        var chunkStart = range.startByte
        var chunkChars = 0

        fun emit(endByte: Long) {
            if (endByte > chunkStart) result += ByteRange(chunkStart, endByte)
            chunkStart = endByte
            chunkChars = 0
        }

        RandomAccessFile(file, "r").use { contentReader ->
            forEachLineRange(file, encoding, range.startByte, range.endByte) { lineStart, lineEnd ->
                val lineBytes = lineEnd - lineStart
                if (lineBytes > MAX_RAW_CHUNK_BYTES) {
                    if (lineStart > chunkStart) emit(lineStart)
                    var segmentStart = lineStart
                    while (segmentStart < lineEnd) {
                        val segmentEnd = safeRawChunkEnd(
                            file,
                            encoding.charset,
                            segmentStart,
                            lineEnd,
                            MAX_RAW_CHUNK_BYTES
                        )
                        if (segmentEnd <= segmentStart) break
                        result += ByteRange(segmentStart, segmentEnd)
                        segmentStart = segmentEnd
                    }
                    chunkStart = lineEnd
                    chunkChars = 0
                    return@forEachLineRange
                }

                val lineChars = decodeRange(contentReader, encoding.charset, lineStart, lineEnd).length
                val limit = if (splitAtTarget) targetChars else MAX_CHAPTER_CHARS
                if (chunkChars > 0 && chunkChars + lineChars > limit) {
                    emit(lineStart)
                }
                chunkChars += lineChars
                if (splitAtTarget && chunkChars >= targetChars) {
                    emit(lineEnd)
                } else if (chunkChars >= MAX_CHAPTER_CHARS) {
                    emit(lineEnd)
                }
            }
        }

        if (chunkStart < range.endByte) result += ByteRange(chunkStart, range.endByte)
        return result.ifEmpty { listOf(range) }
    }

    private fun safeRawChunkEnd(
        file: File,
        charset: Charset,
        start: Long,
        end: Long,
        maxBytes: Long
    ): Long {
        val proposed = minOf(start + maxBytes, end)
        if (proposed >= end) return end
        return when (charset) {
            Charsets.UTF_16LE, Charsets.UTF_16BE -> proposed - ((proposed - start) % 2L)
            Charsets.UTF_8 -> {
                val length = (proposed - start).toInt()
                val bytes = readBytes(file, start, length)
                var leadIndex = bytes.lastIndex
                var continuationBytes = 0
                while (leadIndex >= 0 && (bytes[leadIndex].toInt() and 0xC0) == 0x80) {
                    continuationBytes++
                    leadIndex--
                }
                var safeLength = bytes.size
                if (leadIndex >= 0) {
                    val lead = bytes[leadIndex].toInt() and 0xFF
                    val expected = when {
                        lead and 0x80 == 0 -> 1
                        lead and 0xE0 == 0xC0 -> 2
                        lead and 0xF0 == 0xE0 -> 3
                        lead and 0xF8 == 0xF0 -> 4
                        else -> 1
                    }
                    if (continuationBytes + 1 < expected) safeLength = leadIndex
                }
                start + safeLength.coerceAtLeast(1)
            }
            else -> {
                val bytes = readBytes(file, start, (proposed - start).toInt())
                var cursor = 0
                var safeLength = 0
                while (cursor < bytes.size) {
                    val value = bytes[cursor].toInt() and 0xFF
                    val width = if (value <= 0x7F) 1 else 2
                    if (cursor + width > bytes.size) break
                    cursor += width
                    safeLength = cursor
                }
                start + safeLength.coerceAtLeast(1)
            }
        }
    }

    private inline fun forEachLineRange(
        file: File,
        encoding: EncodingInfo,
        startByte: Long,
        endByte: Long,
        action: (Long, Long) -> Unit
    ) {
        if (endByte <= startByte) return
        RandomAccessFile(file, "r").use { randomAccess ->
            randomAccess.seek(startByte)
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var absolutePosition = startByte
            var lineStart = startByte
            var remaining = endByte - startByte
            val utf16 = encoding.charset == Charsets.UTF_16LE || encoding.charset == Charsets.UTF_16BE

            while (remaining > 0) {
                val requested = minOf(buffer.size.toLong(), remaining).toInt()
                val read = randomAccess.read(buffer, 0, requested)
                if (read <= 0) break

                if (utf16) {
                    var index = 0
                    while (index + 1 < read) {
                        val isNewline = if (encoding.charset == Charsets.UTF_16LE) {
                            buffer[index] == 0x0A.toByte() && buffer[index + 1] == 0x00.toByte()
                        } else {
                            buffer[index] == 0x00.toByte() && buffer[index + 1] == 0x0A.toByte()
                        }
                        if (isNewline) {
                            val lineEnd = absolutePosition + index + 2
                            action(lineStart, lineEnd)
                            lineStart = lineEnd
                        }
                        index += 2
                    }
                } else {
                    for (index in 0 until read) {
                        if (buffer[index] == 0x0A.toByte()) {
                            val lineEnd = absolutePosition + index + 1
                            action(lineStart, lineEnd)
                            lineStart = lineEnd
                        }
                    }
                }

                absolutePosition += read
                remaining -= read
            }
            if (lineStart < endByte) action(lineStart, endByte)
        }
    }

    private fun forEachLinePrefix(
        file: File,
        encoding: EncodingInfo,
        startByte: Long,
        endByte: Long,
        action: (Long, Long, String) -> Unit
    ) {
        if (endByte <= startByte) return
        RandomAccessFile(file, "r").use { randomAccess ->
            randomAccess.seek(startByte)
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            val prefix = ByteArray(HEADING_PREFIX_BYTES)
            var prefixLength = 0
            var absolutePosition = startByte
            var lineStart = startByte
            var remaining = endByte - startByte
            val utf16 = encoding.charset == Charsets.UTF_16LE || encoding.charset == Charsets.UTF_16BE

            fun emit(lineEnd: Long) {
                action(lineStart, lineEnd, String(prefix, 0, prefixLength, encoding.charset))
                lineStart = lineEnd
                prefixLength = 0
            }

            while (remaining > 0) {
                val requested = minOf(buffer.size.toLong(), remaining).toInt()
                val read = randomAccess.read(buffer, 0, requested)
                if (read <= 0) break

                if (utf16) {
                    var index = 0
                    while (index + 1 < read) {
                        val isNewline = if (encoding.charset == Charsets.UTF_16LE) {
                            buffer[index] == 0x0A.toByte() && buffer[index + 1] == 0x00.toByte()
                        } else {
                            buffer[index] == 0x00.toByte() && buffer[index + 1] == 0x0A.toByte()
                        }
                        if (isNewline) {
                            emit(absolutePosition + index + 2)
                        } else if (prefixLength + 2 <= prefix.size) {
                            prefix[prefixLength++] = buffer[index]
                            prefix[prefixLength++] = buffer[index + 1]
                        }
                        index += 2
                    }
                } else {
                    for (index in 0 until read) {
                        if (buffer[index] == 0x0A.toByte()) {
                            emit(absolutePosition + index + 1)
                        } else if (prefixLength < prefix.size) {
                            prefix[prefixLength++] = buffer[index]
                        }
                    }
                }

                absolutePosition += read
                remaining -= read
            }
            if (lineStart < endByte) emit(endByte)
        }
    }

    private fun decodePrefix(
        file: File,
        charset: Charset,
        startByte: Long,
        endByte: Long,
        maxBytes: Int
    ): String {
        var length = minOf(endByte - startByte, maxBytes.toLong()).toInt()
        if ((charset == Charsets.UTF_16LE || charset == Charsets.UTF_16BE) && length % 2 != 0) {
            length--
        }
        return if (length <= 0) "" else String(readBytes(file, startByte, length), charset)
    }

    private fun decodePrefix(
        reader: RandomAccessFile,
        charset: Charset,
        startByte: Long,
        endByte: Long,
        maxBytes: Int
    ): String {
        var length = minOf(endByte - startByte, maxBytes.toLong()).toInt()
        if ((charset == Charsets.UTF_16LE || charset == Charsets.UTF_16BE) && length % 2 != 0) {
            length--
        }
        return if (length <= 0) "" else String(readBytes(reader, startByte, length), charset)
    }

    private fun decodeRange(file: File, charset: Charset, startByte: Long, endByte: Long): String {
        val length = endByte - startByte
        require(length <= Int.MAX_VALUE) { "TXT chapter is too large" }
        return String(readBytes(file, startByte, length.toInt()), charset)
    }

    private fun decodeRange(
        reader: RandomAccessFile,
        charset: Charset,
        startByte: Long,
        endByte: Long
    ): String {
        val length = endByte - startByte
        require(length <= Int.MAX_VALUE) { "TXT chapter is too large" }
        return String(readBytes(reader, startByte, length.toInt()), charset)
    }

    private fun readBytes(file: File, startByte: Long, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        return RandomAccessFile(file, "r").use { randomAccess ->
            randomAccess.seek(startByte)
            ByteArray(length).also { randomAccess.readFully(it) }
        }
    }

    private fun readBytes(reader: RandomAccessFile, startByte: Long, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        reader.seek(startByte)
        return ByteArray(length).also { reader.readFully(it) }
    }

    override fun getChapterContent(chapterIndex: Int): CharSequence {
        synchronized(contentCache) {
            contentCache[chapterIndex]?.let { return it }
        }
        val entry = entries.getOrNull(chapterIndex) ?: return ""
        val file = sourceFile ?: return ""
        val text = decodeRange(file, encodingInfo.charset, entry.startByte, entry.endByte)
            .trim('\uFEFF', '\r', '\n')
        synchronized(contentCache) { contentCache[chapterIndex] = text }
        return text
    }

    override fun getChapterHtml(chapterIndex: Int, optimizeLayout: Boolean): String {
        synchronized(htmlCache) {
            htmlCache[chapterIndex]?.let { return it }
        }
        val text = getChapterContent(chapterIndex).toString()
        if (text.isEmpty()) return ""
        val html = wrapHtml(text)
        synchronized(htmlCache) { htmlCache[chapterIndex] = html }
        return html
    }

    private fun wrapHtml(text: String): String {
        val htmlBody = text.lineSequence()
            .filter { it.isNotBlank() }
            .joinToString("") { line ->
                val escaped = line.trim()
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                "<p>$escaped</p>"
            }
        return """
            |<html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            |<style>
            |  * { box-sizing: border-box; }
            |  body { font-family: sans-serif; font-size: 18px; line-height: 1.7; color: #333; margin: 0; }
            |  p { text-indent: 2em; margin: 8px 0; }
            |</style></head>
            |<body>$htmlBody</body></html>
        """.trimMargin()
    }

    override fun clearHtmlCache() {
        synchronized(htmlCache) { htmlCache.clear() }
    }

    override fun getChapterCount(): Int = entries.size

    private companion object {
        const val STREAM_BUFFER_SIZE = 64 * 1024
        const val HEADING_PREFIX_BYTES = 512
        const val TITLE_PREFIX_BYTES = 512
        const val FALLBACK_TARGET_CHARS = 3_000
        const val MAX_CHAPTER_CHARS = 32_000
        const val MAX_RAW_CHUNK_BYTES = 32_000L
        const val CONTENT_CACHE_SIZE = 5
        const val HTML_CACHE_SIZE = 3

        val CHAPTER_PATTERNS = listOf(
            Regex("^第[一二三四五六七八九十百千零\\d]+[章节回卷]"),
            Regex("^[卷篇][一二三四五六七八九十百千零\\d]+[章回]?"),
            Regex("^Chapter\\s+\\d+", RegexOption.IGNORE_CASE),
            Regex("^\\d{1,3}[.、\\s]"),
            Regex("^第\\d+章")
        )
    }
}
