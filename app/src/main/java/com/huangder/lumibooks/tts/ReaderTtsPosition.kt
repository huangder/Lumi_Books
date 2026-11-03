package com.huangder.lumibooks.tts

enum class ReaderTtsDock { NONE, LEFT, RIGHT }

/** Book-independent position of the controls inside the reader, separate from system subtitles. */
data class ReaderTtsPosition(
    val xFraction: Float = 0f,
    val yFraction: Float = 1f,
    val dock: ReaderTtsDock = ReaderTtsDock.NONE,
    val saved: Boolean = false
) {
    fun normalized() = copy(
        xFraction = if (xFraction.isFinite()) xFraction.coerceIn(0f, 1f) else 0f,
        yFraction = if (yFraction.isFinite()) yFraction.coerceIn(0f, 1f) else 1f
    )
}
