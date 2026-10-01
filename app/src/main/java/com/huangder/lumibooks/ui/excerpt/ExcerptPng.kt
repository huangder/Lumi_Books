package com.huangder.lumibooks.ui.excerpt

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** Stream tiles into one PNG so long excerpts never require a full-height bitmap allocation. */
internal fun ExcerptDocument.writePng(destination: OutputStream) {
    val output = DataOutputStream(destination)
    output.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
    fun chunk(type: String, bytes: ByteArray, count: Int = bytes.size) {
        val name = type.toByteArray(Charsets.US_ASCII)
        output.writeInt(count)
        output.write(name)
        output.write(bytes, 0, count)
        val crc = CRC32().apply { update(name); update(bytes, 0, count) }
        output.writeInt(crc.value.toInt())
    }
    val header = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).apply {
            writeInt(width); writeInt(height)
            write(byteArrayOf(8, 6, 0, 0, 0))
        }
    }.toByteArray()
    chunk("IHDR", header)
    val chunks = object : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()))
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (offset == 0) chunk("IDAT", bytes, length)
            else chunk("IDAT", bytes.copyOfRange(offset, offset + length))
        }
    }
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
    try {
        val compressed = DeflaterOutputStream(chunks, deflater, 64 * 1024)
        val pixels = IntArray(width)
        val row = ByteArray(1 + width * 4)
        for (top in 0 until height step 512) {
            val tile = render(top, minOf(512, height - top))
            try {
                repeat(tile.height) { y ->
                    tile.getPixels(pixels, 0, width, 0, y, width, 1)
                    pixels.forEachIndexed { index, argb ->
                        val offset = 1 + index * 4
                        row[offset] = (argb ushr 16).toByte()
                        row[offset + 1] = (argb ushr 8).toByte()
                        row[offset + 2] = argb.toByte()
                        row[offset + 3] = (argb ushr 24).toByte()
                    }
                    compressed.write(row)
                }
            } finally { tile.recycle() }
        }
        compressed.finish()
    } finally { deflater.end() }
    chunk("IEND", byteArrayOf())
    output.flush()
}
