package com.huangder.lumibooks.util.kindle

import java.io.File
import java.io.RandomAccessFile

/** Content-based routing; an extension never determines the MOBI generation. */
internal data class KindleProbe(val version: Int, val compression: Int, val hybrid: Boolean) {
    val needsReconstruction: Boolean get() = version == 8 || hybrid || compression == 17480

    companion object {
        fun read(file: File): KindleProbe = RandomAccessFile(file, "r").use { input ->
            fun u32(): Long = input.readInt().toLong() and 0xffffffffL
            fun invalid(): Nothing = throw KindleException(KindleFailure.INVALID)
            if (input.length() < 86 || input.length() > 256L * 1024 * 1024) invalid()
            input.seek(60)
            if (String(ByteArray(8).also(input::readFully), Charsets.US_ASCII) != "BOOKMOBI") invalid()
            input.seek(76)
            val count = input.readUnsignedShort()
            if (count < 2 || 78L + count * 8L > input.length()) invalid()
            val offsets = LongArray(count)
            repeat(count) { index -> offsets[index] = u32(); input.skipBytes(4) }
            if (offsets[0] < 78L + count * 8L || offsets.last() >= input.length() ||
                (1 until offsets.size).any { offsets[it - 1] >= offsets[it] }) invalid()
            val start = offsets[0]
            val end = offsets[1]
            if (end - start < 132) invalid()
            input.seek(start)
            val compression = input.readUnsignedShort()
            input.seek(start + 12)
            if (input.readUnsignedShort() != 0) throw KindleException(KindleFailure.ENCRYPTED)
            input.seek(start + 16)
            if (input.readInt() != 0x4d4f4249) invalid()
            val headerLength = u32()
            if (headerLength < 116 || start + 16 + headerLength > end) invalid()
            input.seek(start + 36)
            val version = u32().toInt()
            if (version !in 1..8) throw KindleException(KindleFailure.UNSUPPORTED)
            input.seek(start + 128)
            val flags = u32()
            var hybrid = false
            val exth = start + 16 + headerLength
            if (flags and 0x40L != 0L && exth + 12 <= end) {
                input.seek(exth)
                if (input.readInt() != 0x45585448) invalid()
                val length = u32()
                val entries = u32()
                if (length < 12 || exth + length > end || entries > 4096) invalid()
                var position = exth + 12
                repeat(entries.toInt()) {
                    if (position + 8 > exth + length) invalid()
                    input.seek(position)
                    val type = u32()
                    val size = u32()
                    if (size < 8 || position + size > exth + length) invalid()
                    if (type == 121L && size == 12L) {
                        val boundary = u32()
                        if (boundary != 0xffffffffL) {
                            if (boundary !in 1 until count.toLong() - 1) invalid()
                            input.seek(offsets[boundary.toInt()])
                            if (String(ByteArray(8).also(input::readFully), Charsets.US_ASCII) != "BOUNDARY") invalid()
                            input.seek(offsets[boundary.toInt() + 1] + 12)
                            if (input.readUnsignedShort() != 0) throw KindleException(KindleFailure.ENCRYPTED)
                            hybrid = true
                        }
                    }
                    position += size
                }
            }
            KindleProbe(version, compression, hybrid)
        }
    }
}
