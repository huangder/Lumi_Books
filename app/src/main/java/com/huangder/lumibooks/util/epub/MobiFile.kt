package com.huangder.lumibooks.util.epub

import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.LinkedHashMap

/** PDB record table entry. */
internal data class MobiRecord(
    val offset: Long,
    val attributes: Int,
    val uniqueId: Int
)

/** Parsed PalmDOC + MOBI + EXTH header metadata (record data excluded). */
internal data class MobiHeader(
    val compression: Int,
    val textEncoding: Int,
    val fullName: String,
    val author: String,
    /** EXTH 503 updated title; falls back to fullName when blank. */
    val titleOverride: String,
    /** First text record index (inclusive), absolute within the PDB records. */
    val firstTextRecord: Int,
    /** Last text record index (inclusive), absolute within the PDB records. */
    val lastTextRecord: Int,
    /** Absolute record index of the first image record. */
    val firstImageIndex: Int,
    /** EXTH 201 cover offset (relative to firstImageIndex). */
    val coverOffset: Int?,
    /** EXTH 202 thumbnail offset (relative to firstImageIndex). */
    val thumbnailOffset: Int?,
    /** Extra flags at MOBI header 0xF2: bit0 = multibyte, bit1+ = trailers. */
    val extraFlags: Int
)

/**
 * PalmDOC LZ77 decompression (classic MOBI7 text records).
 *
 * The control byte layout matches KindleUnpack's `PalmdocReader` and libmobi's
 * `mobi_decompress_lz77`:
 *   0x00       -> literal byte 0x00
 *   0x01..0x08 -> copy next (byte) bytes literally
 *   0x09..0x7F -> literal byte
 *   0x80..0xBF -> back-reference: pair = (b1 << 8) | b2,
 *                 distance = (pair >> 3) & 0x7FF, length = (pair & 7) + 3
 *   0xC0..0xFF -> space + (byte xor 0x80)
 */
internal object PalmDocLz77 {
    private const val MAX_DECOMPRESSED_BYTES = 256 * 1024 * 1024

    fun decompress(input: ByteArray): ByteArray {
        var out = ByteArray((input.size * 3).coerceAtLeast(64))
        var outSize = 0
        var pos = 0
        while (pos < input.size) {
            val c = input[pos].toInt() and 0xFF
            pos++
            when {
                c in 1..8 -> {
                    val available = (input.size - pos).coerceAtLeast(0)
                    val n = minOf(c, available)
                    out = ensure(out, outSize, n)
                    System.arraycopy(input, pos, out, outSize, n)
                    outSize += n
                    pos += n
                }
                c < 0x80 -> {
                    out = ensure(out, outSize, 1)
                    out[outSize++] = c.toByte()
                }
                c >= 0xC0 -> {
                    out = ensure(out, outSize, 2)
                    out[outSize++] = ' '.code.toByte()
                    out[outSize++] = (c xor 0x80).toByte()
                }
                else -> {
                    if (pos >= input.size) break
                    val next = input[pos].toInt() and 0xFF
                    pos++
                    val pair = (c shl 8) or next
                    val distance = (pair shr 3) and 0x7FF
                    val length = (pair and 7) + 3
                    out = ensure(out, outSize, length)
                    if (distance <= 0 || distance > outSize) break
                    var source = outSize - distance
                    repeat(length) {
                        out[outSize++] = out[source++]
                    }
                }
            }
            if (outSize > MAX_DECOMPRESSED_BYTES) {
                throw IllegalArgumentException("MOBI decompressed data exceeds the safety limit")
            }
        }
        return out.copyOf(outSize)
    }

    private fun ensure(data: ByteArray, size: Int, extra: Int): ByteArray {
        if (size + extra <= data.size) return data
        var newSize = data.size * 2
        while (newSize < size + extra) newSize *= 2
        return data.copyOf(newSize)
    }
}

/** MOBI file access: PDB container + PalmDOC/MOBI/EXTH header + random record access. */
internal class MobiFile private constructor(
    private val raf: RandomAccessFile,
    val records: List<MobiRecord>,
    val header: MobiHeader,
    private val fileLength: Long
) : AutoCloseable {

    private var textCache: ByteArray? = null
    /** 图片缓存当前总字节数 */
    private var totalImageCacheBytes: Long = 0
    /** LRU 图片缓存，最多保留 MAX_IMAGE_CACHE_ENTRIES 张且总大小不超过 MAX_IMAGE_CACHE_TOTAL_BYTES，避免大体积 Mobi 图片过多导致 OOM */
    private val imageCache = object : LinkedHashMap<Int, ByteArray>(MAX_IMAGE_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>?): Boolean {
            if (size > MAX_IMAGE_CACHE_ENTRIES) {
                eldest?.let { totalImageCacheBytes -= it.value.size.toLong() }
                return true
            }
            return false
        }
    }

    /** 从缓存中移除条目并更新总大小计数 */
    private fun evictImageCacheEntry(key: Int) {
        val bytes = imageCache.remove(key) ?: return
        totalImageCacheBytes -= bytes.size.toLong()
    }

    /** 逐出最旧的缓存条目，直到总大小在限制内 */
    private fun evictImageCacheUntilFit() {
        while (totalImageCacheBytes > MAX_IMAGE_CACHE_TOTAL_BYTES && imageCache.isNotEmpty()) {
            val eldestKey = imageCache.keys.firstOrNull() ?: break
            evictImageCacheEntry(eldestKey)
        }
    }

    @Synchronized
    fun readRecord(index: Int): ByteArray {
        if (index !in records.indices) return ByteArray(0)
        val start = records[index].offset
        val end = records.getOrNull(index + 1)?.offset ?: fileLength
        val size = (end - start).toInt().coerceIn(0, MAX_RECORD_BYTES)
        val bytes = ByteArray(size)
        raf.seek(start)
        var read = 0
        while (read < size) {
            val count = raf.read(bytes, read, size - read)
            if (count < 0) break
            read += count
        }
        return bytes
    }

    /**
     * Concatenates (and decompresses when needed) the text records between
     * [firstTextRecord, lastTextRecord]. The output is the rawml markup; the
     * record-0 PalmDOC/MOBI/EXTH headers are never part of the text records.
     */
    @Synchronized
    fun decompressedText(): ByteArray {
        textCache?.let { return it }
        if (header.compression !in setOf(0, 1, 2)) {
            throw UnsupportedOperationException(UNSUPPORTED_COMPRESSION_MESSAGE)
        }
        val first = header.firstTextRecord
        val last = header.lastTextRecord
        require(first in records.indices && last in records.indices && first <= last) {
            "MOBI text record range is invalid"
        }
        val output = java.io.ByteArrayOutputStream()
        for (index in first..last) {
            var record = readRecord(index)
            record = trimTrailingData(record, header.extraFlags)
            val part = if (header.compression == 2) {
                PalmDocLz77.decompress(record)
            } else {
                record
            }
            output.write(part)
        }
        val text = output.toByteArray()
        textCache = text
        return text
    }

    /**
     * Returns bytes directly renderable by BitmapFactory/Coil:
     * JPEG/PNG pass through; complete GIF passes through; GIF records stored
     * with a 2-byte length prefix are rebuilt with a GIF89a header.
     */
    @Synchronized
    fun imageRecordBytes(index: Int): ByteArray? {
        imageCache[index]?.let { return it }
        val bytes = readRecord(index)
        if (bytes.isEmpty()) return null
        val renderable = when {
            bytes.startsWithGifSignature() -> bytes
            bytes.isJpegOrPng() -> bytes
            bytes.size >= 2 -> {
                val length = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                if (length in 1..(bytes.size - 2)) {
                    GIF_SIGNATURE.toByteArray(Charsets.US_ASCII) + bytes.copyOfRange(2, 2 + length)
                } else {
                    bytes
                }
            }
            else -> bytes
        }
        // 如果单张图片超过缓存总大小限制，不缓存直接返回
        if (renderable.size.toLong() > MAX_IMAGE_CACHE_TOTAL_BYTES) {
            return renderable
        }
        totalImageCacheBytes += renderable.size.toLong()
        imageCache[index] = renderable
        // 逐出旧条目直到总大小在限制内
        evictImageCacheUntilFit()
        return renderable
    }

    /** Record index of the EXTH cover image (EXTH 201 / fallback 202), if any. */
    @Synchronized
    fun coverRecordIndex(): Int? {
        val relative = header.coverOffset ?: header.thumbnailOffset ?: return null
        val index = header.firstImageIndex + relative
        if (index !in records.indices) return null
        return index
    }

    @Synchronized
    override fun close() {
        imageCache.clear()
        totalImageCacheBytes = 0
        textCache = null
        runCatching { raf.close() }
    }

    companion object {
        const val COMPRESSION_PALMDOC = 2
        const val COMPRESSION_NONE = 1
        const val COMPRESSION_HUFF_CDIC = 17480
        const val TEXT_ENCODING_UTF8 = 65001
        const val TEXT_ENCODING_CP1252 = 1252

        private const val MAX_RECORD_BYTES = 256 * 1024 * 1024
        private const val MAX_IMAGE_CACHE_ENTRIES = 32
        private const val MAX_IMAGE_CACHE_TOTAL_BYTES = 128L * 1024 * 1024
        private const val GIF_SIGNATURE = "GIF89a"

        const val UNSUPPORTED_COMPRESSION_MESSAGE = "该 MOBI 使用新版压缩（KF8/HUFF-CDIC），暂不支持"
        const val DRM_MESSAGE = "加密书籍暂不支持"

        fun open(filePath: String): MobiFile {
            val raf = RandomAccessFile(filePath, "r")
            try {
                val headerBytes = ByteArray(PDB_HEADER_SIZE)
                raf.seek(0)
                readFully(raf, headerBytes)
                if (!headerBytes.isPdbMobi()) {
                    throw IllegalArgumentException("不是有效的 MOBI 文件")
                }
                val numRecords = be16(headerBytes, 76)
                require(numRecords > 0) { "MOBI 文件没有记录" }
                val tableBytes = ByteArray(numRecords * RECORD_ENTRY_SIZE)
                readFully(raf, tableBytes)
                val records = ArrayList<MobiRecord>(numRecords)
                for (i in 0 until numRecords) {
                    val base = i * RECORD_ENTRY_SIZE
                    records += MobiRecord(
                        offset = be32(tableBytes, base),
                        attributes = tableBytes[base + 4].toInt() and 0xFF,
                        uniqueId = ((tableBytes[base + 5].toInt() and 0xFF) shl 16) or
                            ((tableBytes[base + 6].toInt() and 0xFF) shl 8) or
                            (tableBytes[base + 7].toInt() and 0xFF)
                    )
                }
                if (records.isEmpty()) throw IllegalArgumentException("MOBI 文件没有记录")
                val firstRecord = readRecordAt(raf, records, 0, raf.length())
                if (firstRecord.size < 16) throw IllegalArgumentException("MOBI 文件头不完整")
                val header = parseFirstRecord(firstRecord, records.size)
                return MobiFile(raf, records, header, raf.length())
            } catch (error: Throwable) {
                runCatching { raf.close() }
                throw error
            }
        }

        /**
         * Parses the PalmDOC header + MOBI header + EXTH, all located inside
         * record 0. Field offsets are absolute within record 0, matching
         * KindleUnpack's mobi_header.py (PalmDOC header is 16 bytes).
         */
        fun parseFirstRecord(firstRecord: ByteArray, recordCount: Int = 0): MobiHeader {
            require(firstRecord.size >= 16) { "MOBI 文件头不完整" }
            val compression = be16(firstRecord, 0)
            val encryption = be16(firstRecord, 12)
            if (encryption != 0) throw IllegalArgumentException(DRM_MESSAGE)
            require(firstRecord.asciiAt(16, 4) == "MOBI") { "不是有效的 MOBI 文件" }
            val mobiHeaderLength = be32(firstRecord, 20).toInt().coerceIn(24, firstRecord.size - 16)
            val textEncoding = be32(firstRecord, 28).toInt()
            val fullNameOffset = be32(firstRecord, 84).toInt()
            val fullNameLength = be32(firstRecord, 88).toInt()
            val firstImageIndex = be32(firstRecord, 108).toInt()
            val exthFlags = be32(firstRecord, 128).toInt()
            val headerFirstContent = be32(firstRecord, 164).toInt()
            val headerLastContent = be32(firstRecord, 168).toInt()
            val mobiVersion = if (firstRecord.size >= 16 + 0x68 + 4) be32(firstRecord, 0x68).toInt() else 0
            val extraFlags = if (mobiHeaderLength >= 0xE4 && mobiVersion >= 5 &&
                firstRecord.size >= 16 + 0xF2 + 2
            ) {
                be16(firstRecord, 0xF2)
            } else {
                0
            }

            // PalmDOC header field at record-0 offset 8 = number of text records
            // that follow the header record. libmobi/KindleUnpack always read
            // text from record 1 onward, so record 0 is never part of rawml.
            val palmDocRecordCount = be16(firstRecord, 8)
            val firstTextRecord: Int
            val lastTextRecord: Int
            if (palmDocRecordCount > 0) {
                firstTextRecord = 1
                lastTextRecord = (1 + palmDocRecordCount - 1).coerceAtMost((recordCount - 1).coerceAtLeast(0))
            } else if (headerFirstContent > 0 && headerLastContent > 0) {
                firstTextRecord = headerFirstContent
                lastTextRecord = headerLastContent
            } else {
                firstTextRecord = 1
                lastTextRecord = (recordCount - 1).coerceAtLeast(0)
            }

            val exth = mutableMapOf<Int, MutableList<ByteArray>>()
            if (exthFlags and 0x40 != 0) {
                val exthStart = 16 + mobiHeaderLength
                if (exthStart + 12 <= firstRecord.size && firstRecord.asciiAt(exthStart, 4) == "EXTH") {
                    val exthLength = be32(firstRecord, exthStart + 4).toInt()
                    val recordCount = be32(firstRecord, exthStart + 8).toInt()
                    var pos = exthStart + 12
                    val exthEnd = minOf(exthStart + exthLength, firstRecord.size)
                    repeat(recordCount.coerceAtMost(256)) {
                        if (pos + 8 > exthEnd) return@repeat
                        val type = be32(firstRecord, pos).toInt()
                        val length = be32(firstRecord, pos + 4).toInt()
                        if (length < 8 || pos + length > exthEnd) return@repeat
                        val data = firstRecord.copyOfRange(pos + 8, pos + length)
                        exth.getOrPut(type) { mutableListOf() }.add(data)
                        pos += length
                    }
                }
            }

            // fullNameOffset is absolute within record 0 (0x54), NOT relative to
            // the MOBI header start.
            val fullNameBytes = runCatching {
                firstRecord.copyOfRange(fullNameOffset, fullNameOffset + fullNameLength)
            }.getOrDefault(ByteArray(0)).takeIf { it.isNotEmpty() }

            val author = exth[EXTH_AUTHOR]
                ?.flatMap { bytes -> MobiText.decode(bytes, textEncoding).split('\u0000') }
                ?.joinToString("; ") { it.trim() }
                ?.takeIf { it.isNotBlank() }
            val titleOverride = exth[EXTH_TITLE_OVERRIDE]
                ?.firstOrNull()
                ?.let { bytes -> MobiText.decode(bytes, textEncoding) }
                ?.takeIf { it.isNotBlank() }
            val coverOffset = exth[EXTH_COVER_OFFSET]?.firstOrNull()?.let { be32(it, 0).toInt() }
            val thumbnailOffset = exth[EXTH_THUMBNAIL_OFFSET]?.firstOrNull()?.let { be32(it, 0).toInt() }

            val fullName = fullNameBytes?.let { bytes ->
                MobiText.decode(bytes, textEncoding)
            }.orEmpty()

            return MobiHeader(
                compression = compression,
                textEncoding = textEncoding,
                fullName = fullName,
                author = author ?: "",
                titleOverride = titleOverride ?: "",
                firstTextRecord = firstTextRecord,
                lastTextRecord = lastTextRecord,
                firstImageIndex = firstImageIndex,
                coverOffset = coverOffset,
                thumbnailOffset = thumbnailOffset,
                extraFlags = extraFlags
            )
        }

        /**
         * Strips per-record trailing data described by the MOBI extra flags
         * (mirrors KindleUnpack's trimTrailingDataEntries): trailer entries
         * first, then multibyte data.
         */
        internal fun trimTrailingData(record: ByteArray, extraFlags: Int): ByteArray {
            if (extraFlags == 0 || record.isEmpty()) return record
            var data = record
            var flags = extraFlags
            val multibyte = flags and 1 != 0
            var trailers = 0
            while (flags > 1) {
                if (flags and 2 != 0) trailers++
                flags = flags shr 1
            }
            repeat(trailers.coerceAtMost(8)) {
                if (data.isEmpty()) return data
                val size = trailingEntrySize(data)
                if (size in 1..data.size) {
                    data = data.copyOfRange(0, data.size - size)
                }
            }
            if (multibyte && data.isNotEmpty()) {
                val size = (data[data.size - 1].toInt() and 0xFF and 3) + 1
                if (size in 1..data.size) {
                    data = data.copyOfRange(0, data.size - size)
                }
            }
            return data
        }

        private fun trailingEntrySize(data: ByteArray): Int {
            var num = 0
            val start = maxOf(0, data.size - 4)
            for (i in start until data.size) {
                val v = data[i].toInt() and 0xFF
                if (v and 0x80 != 0) num = 0
                num = (num shl 7) or (v and 0x7F)
            }
            return num
        }

        private fun readRecordAt(
            raf: RandomAccessFile,
            records: List<MobiRecord>,
            index: Int,
            fileLength: Long
        ): ByteArray {
            val start = records[index].offset
            val end = records.getOrNull(index + 1)?.offset ?: fileLength
            val size = (end - start).toInt().coerceIn(0, MAX_RECORD_BYTES)
            val bytes = ByteArray(size)
            raf.seek(start)
            readFully(raf, bytes)
            return bytes
        }

        private fun readFully(raf: RandomAccessFile, bytes: ByteArray) {
            var read = 0
            while (read < bytes.size) {
                val count = raf.read(bytes, read, bytes.size - read)
                if (count < 0) break
                read += count
            }
        }

        private fun ByteArray.isPdbMobi(): Boolean =
            size >= PDB_HEADER_SIZE && asciiAt(60, 8) == "BOOKMOBI"

        private fun ByteArray.isJpegOrPng(): Boolean =
            size >= 4 && ((this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()) ||
                (this[0] == 0x89.toByte() && this[1] == 0x50.toByte()))

        private fun ByteArray.startsWithGifSignature(): Boolean =
            size >= 6 && this[0] == 'G'.code.toByte() && this[1] == 'I'.code.toByte() &&
                this[2] == 'F'.code.toByte() && this[3] == '8'.code.toByte() &&
                (this[4] == '7'.code.toByte() || this[4] == '9'.code.toByte()) &&
                this[5] == 'a'.code.toByte()

        internal fun ByteArray.asciiAt(offset: Int, length: Int): String {
            if (offset + length > size) return ""
            val sb = StringBuilder(length)
            for (i in offset until offset + length) sb.append((this[i].toInt() and 0xFF).toChar())
            return sb.toString()
        }

        internal fun be16(bytes: ByteArray, offset: Int): Int {
            if (offset + 2 > bytes.size) return 0
            return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        }

        internal fun be32(bytes: ByteArray, offset: Int): Long {
            if (offset + 4 > bytes.size) return 0L
            return ((bytes[offset].toLong() and 0xFF) shl 24) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
                (bytes[offset + 3].toLong() and 0xFF)
        }

        private const val PDB_HEADER_SIZE = 78
        private const val RECORD_ENTRY_SIZE = 8
        private const val EXTH_AUTHOR = 100
        private const val EXTH_COVER_OFFSET = 201
        private const val EXTH_THUMBNAIL_OFFSET = 202
        private const val EXTH_TITLE_OVERRIDE = 503
    }
}

/** MOBI text decoding: declared encoding first, with GB18030/GBK fallbacks. */
internal object MobiText {
    fun decode(bytes: ByteArray, declaredEncoding: Int): String {
        return String(bytes, resolveCharset(bytes, declaredEncoding))
    }

    /** Returns the charset that would be chosen for [bytes] under [declaredEncoding]. */
    fun resolveCharset(bytes: ByteArray, declaredEncoding: Int): Charset {
        val candidates = buildList {
            when (declaredEncoding) {
                MobiFile.TEXT_ENCODING_UTF8 -> {
                    add(Charsets.UTF_8)
                    add(Charset.forName("GB18030"))
                    add(Charset.forName("GBK"))
                }
                MobiFile.TEXT_ENCODING_CP1252 -> {
                    add(Charset.forName("windows-1252"))
                    add(Charsets.UTF_8)
                    add(Charset.forName("GB18030"))
                    add(Charset.forName("GBK"))
                }
                else -> {
                    add(Charsets.UTF_8)
                    add(Charset.forName("GB18030"))
                    add(Charset.forName("GBK"))
                }
            }
        }.distinct()
        var best: String? = null
        var chosen = Charsets.UTF_8
        for (charset in candidates) {
            val decoded = runCatching { String(bytes, charset) }.getOrNull() ?: continue
            if (!decoded.contains('\uFFFD')) {
                chosen = charset
                best = decoded
                break
            }
            if (best == null) {
                best = decoded
                chosen = charset
            }
        }
        return chosen
    }
}
