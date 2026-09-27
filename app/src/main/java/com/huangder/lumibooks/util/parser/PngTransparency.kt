package com.huangder.lumibooks.util.parser

import java.io.DataInputStream
import java.io.InputStream

/** Metadata retained by lazy drawables, before rasterization loses PNG channel information. */
internal interface ReaderImageTransparencyInfo {
    val hasAlphaChannel: Boolean
}

/** Reads PNG metadata, not pixels: even an entirely opaque RGBA PNG has an alpha channel. */
internal fun pngHasAlphaChannel(input: InputStream): Boolean = runCatching {
    val data = DataInputStream(input)
    if (data.readLong() != -8552249625308161526L) return@runCatching false // PNG signature
    var scanned = 8L
    while (scanned < 8 * 1024 * 1024) {
        val length = data.readInt()
        if (length < 0) return@runCatching false
        val type = data.readInt()
        when (type) {
            0x49484452 -> { // IHDR
                if (length != 13) return@runCatching false
                data.readInt() // width
                data.readInt() // height
                data.readUnsignedByte() // bit depth
                val colorType = data.readUnsignedByte()
                if (colorType == 4 || colorType == 6) return@runCatching true
                data.skipBytes(7) // remaining IHDR fields and CRC
            }
            0x74524E53 -> return@runCatching true // tRNS (palette/color-key transparency)
            0x49444154, 0x49454E44 -> return@runCatching false // IDAT / IEND
            else -> {
                if (length > 8 * 1024 * 1024 - scanned) return@runCatching true
                if (data.skipBytes(length) != length || data.skipBytes(4) != 4) return@runCatching false
            }
        }
        scanned += length.toLong() + 12
    }
    true // Conservatively retain the reader background for oversized metadata.
}.getOrDefault(false)
