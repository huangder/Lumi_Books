package com.huangder.lumibooks.tts

/** Durable listening location, independent of the reader and of any engine's audio cache. */
data class TtsListeningPosition(
    val bookId: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val characterOffset: Int,
    val updatedAt: Long
) {
    fun encode(): String = "$chapterIndex|$pageIndex|$characterOffset|$updatedAt"

    companion object {
        fun decode(bookId: String, raw: String): TtsListeningPosition? {
            val fields = raw.split('|')
            if (bookId.isBlank() || fields.size != 4) return null
            val chapter = fields[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val page = fields[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val offset = fields[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val timestamp = fields[3].toLongOrNull()?.takeIf { it > 0 } ?: return null
            return TtsListeningPosition(bookId, chapter, page, offset, timestamp)
        }
    }
}
