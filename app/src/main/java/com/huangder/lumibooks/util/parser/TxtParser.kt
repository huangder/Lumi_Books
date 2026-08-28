package com.huangder.lumibooks.util.parser

import android.content.Context
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.SeekableBookSource
import com.huangder.lumibooks.util.cache.BookFingerprint
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import com.huangder.lumibooks.util.performance.ReaderOpenPerformance
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.LinkedHashMap

class TxtParser(private val context: Context? = null) : BookParser {
    override var paragraphSpacingDp: Float = 0f
    override var firstLineIndentChars: Float = 0f
    override var contentWidth: Int = 0
    override var useEpubCss: Boolean = false  // TxtParser 不支持 EPUB CSS，保留接口兼容

    private data class TxtChapterEntry(
        val index: Int,
        val title: String,
        val startByte: Long,
        val endByte: Long
    )

    private data class Heading(val title: String, val startByte: Long)
    private data class NumberedHeading(val heading: Heading, val number: Int)
    private data class ByteRange(val startByte: Long, val endByte: Long)
    private data class EncodingInfo(val charset: Charset, val contentStart: Long)

    private var sourceFile: File? = null
    private var sourceLocation: String = ""
    private var sourceLease: SeekableBookSource? = null
    var selectedEncoding: TxtEncoding = TxtEncoding.AUTO
    private var encodingInfo = EncodingInfo(Charsets.UTF_8, 0L)
    val activeCharsetName: String
        get() = encodingInfo.charset.name()
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

    override fun parse(filePath: String): BookContent = synchronized(parseLock(filePath)) {
        parseLocked(filePath)
    }

    private fun parseLocked(filePath: String): BookContent {
        sourceLease?.close()
        val lease = ReaderOpenPerformance.trace("txt_open_seekable") {
            if (BookFileAccess.isContentUri(filePath)) {
                BookFileAccess.openSeekable(
                    requireNotNull(context) { "Context is required for document URIs" },
                    filePath,
                    writable = true
                )
            } else null
        }
        val file = File(lease?.path ?: filePath)
        require(file.isFile) { "TXT file not found: $filePath" }

        sourceLocation = filePath
        sourceLease = lease
        sourceFile = file
        val displayTitle = context?.let { FileUtils.getFileNameFromLocation(it, filePath) }
            ?.substringBeforeLast('.')
            ?.ifBlank { null }
            ?: file.nameWithoutExtension
        synchronized(contentCache) { contentCache.clear() }
        synchronized(htmlCache) { htmlCache.clear() }

        // 优先从磁盘缓存加载章节索引，避免每次重新全文扫描
        val cached = ReaderOpenPerformance.trace("txt_index_cache_read") {
            loadChapterCache(file)
        }
        if (cached != null) {
            encodingInfo = cached.first
            entries = cached.second
        } else {
            encodingInfo = ReaderOpenPerformance.trace("txt_encoding_detect") {
                resolveEncoding(file)
            }
            val headings = ReaderOpenPerformance.trace("txt_heading_scan") {
                findChapterHeadings(file, encodingInfo)
            }
            entries = ReaderOpenPerformance.trace("txt_index_build") {
                if (headings.size >= 2) {
                    buildHeadingEntries(file, headings, encodingInfo)
                } else {
                    buildFallbackEntries(file, encodingInfo)
                }
            }
            if (entries.isEmpty()) {
                entries = listOf(
                    TxtChapterEntry(
                        index = 0,
                        title = displayTitle,
                        startByte = encodingInfo.contentStart,
                        endByte = file.length()
                    )
                )
            }
            // 解析完成后写入缓存，供下次打开使用
            ReaderOpenPerformance.trace("txt_index_cache_write") {
                saveChapterCache(file, encodingInfo, entries)
            }
        }

        return BookContent(
            title = displayTitle,
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

    /**
     * 从磁盘缓存加载章节索引。
     * 缓存文件以文件路径哈希命名，并记录 fileSize + lastModified 用于失效校验。
     * 文件被修改或首次打开时返回 null，触发全文解析。
     */
    private fun loadChapterCache(file: File): Pair<EncodingInfo, List<TxtChapterEntry>>? {
        val ctx = context ?: return null
        val fingerprint = BookFingerprint.resolve(ctx, sourceLocation.ifBlank { file.absolutePath })
        val payload = ReaderCacheStore.get(ctx).readMetadata(txtIndexNamespace(), fingerprint) ?: return null
        return try {
            if (payload.getString("formatVersion") != CACHE_VERSION) return null
            val charset = Charset.forName(payload.getString("charset"))
            val contentStart = payload.getLong("contentStart")
            val encoding = EncodingInfo(charset, contentStart)
            val array = payload.getJSONArray("chapters")
            val chapterEntries = buildList {
                for (position in 0 until array.length()) {
                    val item = array.getJSONObject(position)
                    add(
                        TxtChapterEntry(
                            index = item.getInt("index"),
                            title = item.getString("title"),
                            startByte = item.getLong("startByte"),
                            endByte = item.getLong("endByte")
                        )
                    )
                }
            }
            if (chapterEntries.isEmpty()) return null
            Pair(encoding, chapterEntries)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将章节索引写入磁盘缓存。写入失败时静默忽略，不影响阅读。
     */
    private fun saveChapterCache(file: File, encoding: EncodingInfo, chapters: List<TxtChapterEntry>) {
        val ctx = context ?: return
        val fingerprint = BookFingerprint.resolve(ctx, sourceLocation.ifBlank { file.absolutePath })
        val chapterArray = JSONArray()
        chapters.forEach { entry ->
            chapterArray.put(
                JSONObject()
                    .put("index", entry.index)
                    .put("title", entry.title)
                    .put("startByte", entry.startByte)
                    .put("endByte", entry.endByte)
            )
        }
        val payload = JSONObject()
            .put("formatVersion", CACHE_VERSION)
            .put("charset", encoding.charset.name())
            .put("contentStart", encoding.contentStart)
            .put("chapters", chapterArray)
        runCatching {
            ReaderCacheStore.get(ctx).writeMetadata(txtIndexNamespace(), fingerprint, payload)
        }
    }

    private fun txtIndexNamespace(): String = "txt_index_${selectedEncoding.storageValue}"

    private fun resolveEncoding(file: File): EncodingInfo {
        val requestedCharset = selectedEncoding.charsetOrNull() ?: return detectEncoding(file)
        return EncodingInfo(requestedCharset, matchingBomLength(file, requestedCharset))
    }

    private fun matchingBomLength(file: File, charset: Charset): Long {
        val prefix = ByteArray(3)
        val size = FileInputStream(file).use { it.read(prefix) }
        return when {
            charset == Charsets.UTF_8 && size >= 3 &&
                prefix[0] == 0xEF.toByte() && prefix[1] == 0xBB.toByte() && prefix[2] == 0xBF.toByte() -> 3L
            charset == Charsets.UTF_16BE && size >= 2 &&
                prefix[0] == 0xFE.toByte() && prefix[1] == 0xFF.toByte() -> 2L
            charset == Charsets.UTF_16LE && size >= 2 &&
                prefix[0] == 0xFF.toByte() && prefix[1] == 0xFE.toByte() -> 2L
            else -> 0L
        }
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

        // 只采样前 512 KB 判断编码，避免对大文件读取全部内容。
        // 任何中文文件在前 512 KB 内必然出现非 ASCII 字节，足以区分 UTF-8 和 GBK。
        val sampleSize = (512 * 1024L).coerceAtMost(file.length()).toInt()
        val sample = ByteArray(sampleSize)
        val actualRead = FileInputStream(file).use { it.read(sample) }.coerceAtLeast(0)
        detectUtf16WithoutBom(sample, actualRead)?.let { return EncodingInfo(it, 0L) }

        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        // The fixed-size sample may end in the middle of a valid multi-byte UTF-8 character.
        // Decode it as a non-final chunk so an incomplete trailing sequence is accepted, while
        // malformed sequences inside the sample still make us fall back to GBK.
        val decodeResult = utf8Decoder.decode(
            java.nio.ByteBuffer.wrap(sample, 0, actualRead),
            java.nio.CharBuffer.allocate(actualRead.coerceAtLeast(1)),
            false
        )
        return if (decodeResult.isError) {
            EncodingInfo(Charset.forName("GB18030"), 0L)
        } else {
            EncodingInfo(Charsets.UTF_8, 0L)
        }
    }

    private fun detectUtf16WithoutBom(sample: ByteArray, length: Int): Charset? {
        if (length < 4) return null
        var evenZeros = 0
        var oddZeros = 0
        var leNewlines = 0
        var beNewlines = 0
        var index = 0
        while (index + 1 < length) {
            val first = sample[index].toInt() and 0xFF
            val second = sample[index + 1].toInt() and 0xFF
            if (first == 0) evenZeros++
            if (second == 0) oddZeros++
            if ((first == 0x0A || first == 0x0D) && second == 0) leNewlines++
            if (first == 0 && (second == 0x0A || second == 0x0D)) beNewlines++
            index += 2
        }
        val pairs = (length / 2).coerceAtLeast(1)
        val leSignal = oddZeros.toFloat() / pairs >= 0.20f || (leNewlines >= 2 && beNewlines == 0)
        val beSignal = evenZeros.toFloat() / pairs >= 0.20f || (beNewlines >= 2 && leNewlines == 0)
        return when {
            leSignal && oddZeros > evenZeros * 2 -> Charsets.UTF_16LE
            beSignal && evenZeros > oddZeros * 2 -> Charsets.UTF_16BE
            leNewlines >= 2 && beNewlines == 0 -> Charsets.UTF_16LE
            beNewlines >= 2 && leNewlines == 0 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun findChapterHeadings(file: File, encoding: EncodingInfo): List<Heading> {
        // 一次扫描同时匹配所有模式，避免对大文件重复全文扫描（原来最多 5 次）
        val matchesByPattern = Array(TxtChapterStructure.PATTERN_COUNT) { mutableListOf<Heading>() }
        val decoratedHeadings = mutableListOf<Heading>()
        val looseNumberedHeadings = mutableListOf<NumberedHeading>()
        var pendingDecoratedHeading: Heading? = null
        var pendingLooseNumberedHeading: NumberedHeading? = null
        var previousLineWasBlank = true
        forEachLinePrefix(file, encoding, encoding.contentStart, file.length()) { start, _, prefix ->
            // 🔥 同时 trim 首尾：中文TXT常见全角空格缩进如「　　第一章」
            // 原来只 trimEnd 导致 ^ 锚定失败，触发 fallback 用正文首行当标题
            val line = prefix.trim()
            pendingDecoratedHeading?.let { pending ->
                if (line.isNotEmpty()) {
                    if (!COPYRIGHT_LINE_PATTERN.containsMatchIn(line)) {
                        decoratedHeadings += pending
                    }
                    pendingDecoratedHeading = null
                }
            }
            pendingLooseNumberedHeading?.let { pending ->
                if (line.isEmpty()) {
                    looseNumberedHeadings += pending
                }
                pendingLooseNumberedHeading = null
            }

            if (TxtChapterStructure.isDecoratedHeading(line)) {
                pendingDecoratedHeading = Heading(line.take(50), start)
            }

            val matchedPattern = TxtChapterStructure.matchingPatternIndex(line)
            val matchedStrictPattern = matchedPattern != null
            if (matchedPattern != null) matchesByPattern[matchedPattern] += Heading(line.take(50), start)
            if (!matchedStrictPattern && previousLineWasBlank) {
                LOOSE_NUMBERED_HEADING_PATTERN.matchEntire(line)?.let { match ->
                    val number = match.groupValues[1].toIntOrNull()
                    if (number != null) {
                        pendingLooseNumberedHeading = NumberedHeading(
                            heading = Heading(line.take(50), start),
                            number = number
                        )
                    }
                }
            }
            previousLineWasBlank = line.isEmpty()
        }
        pendingDecoratedHeading?.let(decoratedHeadings::add)

        // 数字加顿号/空格更常见于正文清单，不能作为通用章节规则。
        val primaryHeadings = matchesByPattern.firstOrNull { it.size >= 2 } ?: return emptyList()
        val headingsWithFilledGaps = fillSingleNumberGaps(primaryHeadings, looseNumberedHeadings)
        val firstNumber = extractArabicChapterNumber(headingsWithFilledGaps.first().title)
        val decoratedPrelude = decoratedHeadings.filter {
            it.startByte < headingsWithFilledGaps.first().startByte
        }
        return if (firstNumber != null && firstNumber > 1 && decoratedPrelude.size == firstNumber - 1) {
            decoratedPrelude + headingsWithFilledGaps
        } else {
            headingsWithFilledGaps
        }
    }

    private fun fillSingleNumberGaps(
        headings: List<Heading>,
        looseNumberedHeadings: List<NumberedHeading>
    ): List<Heading> {
        if (headings.size < 2 || looseNumberedHeadings.isEmpty()) return headings
        return buildList {
            headings.zipWithNext().forEach { (current, next) ->
                add(current)
                val currentNumber = extractArabicChapterNumber(current.title) ?: return@forEach
                val nextNumber = extractArabicChapterNumber(next.title) ?: return@forEach
                if (nextNumber != currentNumber + 2) return@forEach

                val expectedNumber = currentNumber + 1
                val candidates = looseNumberedHeadings.filter { candidate ->
                    candidate.number == expectedNumber &&
                        candidate.heading.startByte > current.startByte &&
                        candidate.heading.startByte < next.startByte
                }
                if (candidates.size == 1) add(candidates.single().heading)
            }
            add(headings.last())
        }
    }

    private fun extractArabicChapterNumber(title: String): Int? {
        return ARABIC_CHAPTER_NUMBER_PATTERN.find(title)?.groupValues?.get(1)?.toIntOrNull()
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
                TxtChapterEntry(index, "第${index + 1}章", range.startByte, range.endByte)
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
                val limit = if (splitAtTarget) targetChars else TxtChapterStructure.MAX_CHAPTER_CHARS
                if (chunkChars > 0 && chunkChars + lineChars > limit) {
                    emit(lineStart)
                }
                chunkChars += lineChars
                if (splitAtTarget && chunkChars >= targetChars) {
                    emit(lineEnd)
                } else if (chunkChars >= TxtChapterStructure.MAX_CHAPTER_CHARS) {
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

    /**
     * 返回指定章节在源文件中的字节范围 (startByte, endByte)，用于编辑时定位
     */
    override fun getChapterByteRange(chapterIndex: Int): Pair<Long, Long>? {
        val entry = entries.getOrNull(chapterIndex) ?: return null
        return entry.startByte to entry.endByte
    }

    override fun replaceChapterContent(chapterIndex: Int, newText: String): Boolean {
        return rewriteWithOperations(listOf(TxtSetChapterText(chapterIndex, newText))).success
    }

    /** Applies editor operations in one streaming rewrite. */
    fun rewriteWithOperations(
        operations: List<TxtEditOperation>,
        reparseAfterWrite: Boolean = true
    ): TxtRewriteResult = synchronized(
        parseLock(sourceLocation.ifBlank { sourceFile?.absolutePath.orEmpty() })
    ) {
        rewriteWithOperationsLocked(operations, reparseAfterWrite)
    }

    private fun rewriteWithOperationsLocked(
        operations: List<TxtEditOperation>,
        reparseAfterWrite: Boolean
    ): TxtRewriteResult {
        if (operations.isEmpty()) return TxtRewriteResult(success = true)
        val file = sourceFile ?: return TxtRewriteResult(false, errorMessage = "TXT source is unavailable")
        val charset = encodingInfo.charset
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        val temporary = File(file.parentFile, file.name + ".editor.tmp")
        var changedChapterCount = 0
        val affectsEveryChapter = operations.any {
            it is TxtReplaceText && it.chapterIndex == null
        }
        val affectedChapterIndexes = if (affectsEveryChapter) {
            emptySet()
        } else {
            operations.mapNotNullTo(mutableSetOf()) { operation ->
                when (operation) {
                    is TxtSetChapterText -> operation.chapterIndex
                    is TxtReplaceRange -> operation.chapterIndex
                    is TxtReplaceText -> operation.chapterIndex
                }
            }
        }

        return try {
            if (temporary.exists() && !temporary.delete()) {
                return TxtRewriteResult(false, errorMessage = "Unable to prepare temporary file")
            }

            RandomAccessFile(file, "r").use { input ->
                FileOutputStream(temporary).use { output ->
                    var copiedUntil = 0L
                    entries.forEach { entry ->
                        copyRange(input, output, copiedUntil, entry.startByte, buffer)
                        if (!affectsEveryChapter && entry.index !in affectedChapterIndexes) {
                            copyRange(input, output, entry.startByte, entry.endByte, buffer)
                            copiedUntil = entry.endByte
                            return@forEach
                        }

                        val rawLength = entry.endByte - entry.startByte
                        require(rawLength <= Int.MAX_VALUE) { "TXT chapter is too large" }
                        val rawBytes = readBytes(input, entry.startByte, rawLength.toInt())
                        val rawText = String(rawBytes, charset)
                        val visibleStart = rawText.indexOfFirst { it != '\uFEFF' && it != '\r' && it != '\n' }
                            .let { if (it < 0) rawText.length else it }
                        val visibleEnd = rawText.indexOfLast { it != '\uFEFF' && it != '\r' && it != '\n' } + 1
                        val originalText = rawText.substring(visibleStart, visibleEnd.coerceAtLeast(visibleStart))
                        val editedText = applyTxtEditOperations(entry.index, originalText, operations)

                        if (editedText == originalText) {
                            output.write(rawBytes)
                        } else {
                            val prefixBytes = strictEncode(rawText.substring(0, visibleStart), charset)
                            val suffixBytes = strictEncode(rawText.substring(visibleEnd.coerceAtLeast(visibleStart)), charset)
                            output.write(prefixBytes)
                            output.write(strictEncode(editedText, charset))
                            output.write(suffixBytes)
                            changedChapterCount++
                        }
                        copiedUntil = entry.endByte
                    }
                    copyRange(input, output, copiedUntil, input.length(), buffer)
                    output.flush()
                    output.fd.sync()
                }
            }

            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            if (BookFileAccess.isContentUri(sourceLocation)) {
                sourceLease?.writeBack()
            }
            // Size and timestamp can both remain unchanged after a quick same-length edit on
            // some Android filesystems. Never let the next parser reuse that stale index.
            context?.let { ctx ->
                ReaderCacheStore.get(ctx).invalidate(sourceLocation.ifBlank { file.absolutePath })
            }
            synchronized(contentCache) { contentCache.clear() }
            synchronized(htmlCache) { htmlCache.clear() }
            val reparseError = if (reparseAfterWrite) {
                runCatching {
                    parse(sourceLocation.ifBlank { file.absolutePath })
                }.exceptionOrNull()
            } else {
                null
            }
            TxtRewriteResult(
                success = true,
                changedChapterCount = changedChapterCount,
                errorMessage = reparseError?.let {
                    "TXT was saved, but its index could not be refreshed: " +
                        (it.message ?: it.javaClass.simpleName)
                }
            )
        } catch (error: Exception) {
            temporary.delete()
            TxtRewriteResult(
                success = false,
                changedChapterCount = changedChapterCount,
                errorMessage = error.message ?: error.javaClass.simpleName
            )
        }
    }

    private fun copyRange(
        input: RandomAccessFile,
        output: FileOutputStream,
        startByte: Long,
        endByte: Long,
        buffer: ByteArray
    ) {
        if (endByte <= startByte) return
        input.seek(startByte)
        var remaining = endByte - startByte
        while (remaining > 0L) {
            val read = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (read <= 0) error("Unexpected end of TXT source")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun strictEncode(text: String, charset: Charset): ByteArray {
        if (text.isEmpty()) return ByteArray(0)
        val encoded = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(text))
        return ByteArray(encoded.remaining()).also(encoded::get)
    }

    private fun replaceDocumentRangeInPlace(
        file: File,
        entry: TxtChapterEntry,
        replacement: ByteArray,
        buffer: ByteArray
    ) {
        RandomAccessFile(file, "rw").use { raf ->
            val originalLength = raf.length()
            val oldLength = entry.endByte - entry.startByte
            val delta = replacement.size.toLong() - oldLength

            if (delta > 0L) {
                raf.setLength(originalLength + delta)
                var readEnd = originalLength
                while (readEnd > entry.endByte) {
                    val count = minOf(buffer.size.toLong(), readEnd - entry.endByte).toInt()
                    val readStart = readEnd - count
                    raf.seek(readStart)
                    raf.readFully(buffer, 0, count)
                    raf.seek(readStart + delta)
                    raf.write(buffer, 0, count)
                    readEnd = readStart
                }
            } else if (delta < 0L) {
                var readPosition = entry.endByte
                var writePosition = entry.startByte + replacement.size
                while (readPosition < originalLength) {
                    val count = minOf(buffer.size.toLong(), originalLength - readPosition).toInt()
                    raf.seek(readPosition)
                    raf.readFully(buffer, 0, count)
                    raf.seek(writePosition)
                    raf.write(buffer, 0, count)
                    readPosition += count
                    writePosition += count
                }
                raf.setLength(originalLength + delta)
            }

            raf.seek(entry.startByte)
            raf.write(replacement)
            raf.fd.sync()
        }
    }

    override fun close() {
        sourceLease?.close()
        sourceLease = null
        sourceFile = null
    }

    private companion object {
        const val CACHE_VERSION = "TXT_INDEX_V5"  // V5: support mixed-format chapter sequences
        const val STREAM_BUFFER_SIZE = 64 * 1024
        const val HEADING_PREFIX_BYTES = 512
        const val TITLE_PREFIX_BYTES = 512
        const val FALLBACK_TARGET_CHARS = 3_000
        const val MAX_RAW_CHUNK_BYTES = 32_000L
        const val CONTENT_CACHE_SIZE = 5
        const val HTML_CACHE_SIZE = 3

        val PARSE_LOCKS = Array(16) { Any() }

        fun parseLock(location: String): Any {
            val index = (location.hashCode() and Int.MAX_VALUE) % PARSE_LOCKS.size
            return PARSE_LOCKS[index]
        }

        val COPYRIGHT_LINE_PATTERN = Regex("^(?:ⓒ|\u00a9|版权)", RegexOption.IGNORE_CASE)
        val LOOSE_NUMBERED_HEADING_PATTERN = Regex("^\\D{1,12}?(\\d{1,5})\\s*[：:]\\s*\\S.{0,40}$")
        val ARABIC_CHAPTER_NUMBER_PATTERN = Regex("^第(\\d{1,5})[章节回卷话]")
    }
}
