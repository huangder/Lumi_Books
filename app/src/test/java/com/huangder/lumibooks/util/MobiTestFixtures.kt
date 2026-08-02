package com.huangder.lumibooks.util

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Builders for synthetic MOBI fixtures used by JVM unit tests:
 * record-0 headers and a complete uncompressed .mobi container.
 */
object MobiTestFixtures {

    fun be16(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())

    fun be32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    /**
     * Builds a synthetic MOBI record 0: PalmDOC header (16 bytes) + MOBI header
     * (232 bytes) + EXTH (author 100 / title 503 / cover offset 201) + full name.
     */
    fun buildRecord0(
        compression: Int = 1,
        textEncoding: Int = 65001,
        fullName: String = "Raw Title",
        author: String = "Test Author",
        titleOverride: String = "MOBI Book",
        coverOffset: Int = 0,
        firstImageIndex: Int = 3,
        textRecordCount: Int = 2
    ): ByteArray {
        val mobiHeaderLength = 232
        val exthBody = ByteArrayOutputStream().apply {
            fun entry(type: Int, data: ByteArray) {
                val length = 8 + data.size
                write(be32(type))
                write(be32(length))
                write(data)
            }
            entry(100, author.toByteArray(Charsets.UTF_8))
            entry(503, titleOverride.toByteArray(Charsets.UTF_8))
            entry(201, be32(coverOffset))
        }.toByteArray()
        val exthLength = 12 + exthBody.size
        val fullNameBytes = fullName.toByteArray(Charsets.UTF_8)
        val fullNameOffset = 16 + mobiHeaderLength + exthLength

        return ByteArrayOutputStream().apply {
            write(be16(compression))            // 0x00 compression
            write(be16(0))                      // 0x02 unused
            write(be32(0))                      // 0x04 text length (unused)
            write(be16(textRecordCount))        // 0x08 text record count
            write(be16(4096))                   // 0x0A record size
            write(be16(0))                      // 0x0C encryption type
            write(be16(0))                      // 0x0E unknown
            write("MOBI".toByteArray(Charsets.US_ASCII)) // 0x10
            write(be32(mobiHeaderLength))       // 0x14
            write(be32(0))                      // 0x18
            write(be32(textEncoding))           // 0x1C text encoding
            while (size() < 84) write(0)
            write(be32(fullNameOffset))         // 0x54
            write(be32(fullNameBytes.size))     // 0x58
            while (size() < 108) write(0)
            write(be32(firstImageIndex))        // 0x6C
            while (size() < 128) write(0)
            write(be32(0x40))                   // 0x80 EXTH flags
            while (size() < 164) write(0)
            write(be32(1))                      // 0xA4 first content (fallback)
            write(be32(textRecordCount))        // 0xA8 last content (fallback)
            while (size() < 16 + mobiHeaderLength) write(0)
            write("EXTH".toByteArray(Charsets.US_ASCII))
            write(be32(exthLength))
            write(be32(3))
            write(exthBody)
            while (size() < fullNameOffset) write(0)
            write(fullNameBytes)
        }.toByteArray()
    }

    /** Writes a complete uncompressed .mobi container to [file]. */
    fun writeMobiFile(
        file: File,
        textRecords: List<ByteArray>,
        images: List<ByteArray> = emptyList(),
        record0: ByteArray = buildRecord0()
    ): File {
        val records = listOf(record0) + textRecords + images
        val tableOffset = 78
        var offset = tableOffset + records.size * 8
        val header = ByteArray(78)
        writeAscii(header, 60, "BOOK")
        writeAscii(header, 64, "MOBI")
        writeBe16(header, 76, records.size)
        return ByteArrayOutputStream().apply {
            write(header)
            for ((index, record) in records.withIndex()) {
                write(be32(offset))
                write(byteArrayOf(0, 0, 0, index.toByte()))
                offset += record.size
            }
            for (record in records) write(record)
        }.toByteArray().let { bytes ->
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            file
        }
    }

    fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun writeAscii(target: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).forEachIndexed { i, b -> target[offset + i] = b }
    }

    private fun writeBe16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value shr 8).toByte()
        target[offset + 1] = value.toByte()
    }
}
