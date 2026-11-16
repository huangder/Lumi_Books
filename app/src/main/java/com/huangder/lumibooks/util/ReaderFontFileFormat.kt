package com.huangder.lumibooks.util

import java.io.DataInputStream
import java.io.File

/** Older imports were all stored with a .ttf suffix. Use the font container signature. */
internal enum class ReaderFontFileFormat(val extension: String, val mimeType: String) {
    TRUETYPE("ttf", "font/ttf"),
    OPENTYPE("otf", "font/otf"),
    COLLECTION("ttc", "font/collection"),
    WOFF("woff", "font/woff"),
    WOFF2("woff2", "font/woff2");

    companion object {
        fun mimeTypeForExtension(extension: String): String =
            entries.firstOrNull { it.extension == extension }?.mimeType ?: TRUETYPE.mimeType

        fun detect(file: File): ReaderFontFileFormat? = runCatching {
            DataInputStream(file.inputStream()).use { stream ->
                when (stream.readInt()) {
                    0x00010000, 0x74727565 -> TRUETYPE // sfnt 1.0, true
                    0x4F54544F -> OPENTYPE // OTTO
                    0x74746366 -> COLLECTION // ttcf
                    0x774F4646 -> WOFF // wOFF
                    0x774F4632 -> WOFF2 // wOF2
                    else -> null
                }
            }
        }.getOrNull()
    }
}
