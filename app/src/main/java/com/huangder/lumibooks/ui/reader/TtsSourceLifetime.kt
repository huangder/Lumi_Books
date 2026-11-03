package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.TtsPageSource

/** The session can outlive the reader, but must release its parser when both owners finish. */
internal class TtsSourceLifetime(private val onReleased: () -> Unit) {
    private var sessions = 0
    private var readerReleased = false
    private var released = false

    fun retain(source: TtsPageSource): TtsPageSource {
        check(!readerReleased)
        sessions++
        return object : TtsPageSource by source {
            private var closed = false
            override fun close() {
                if (closed) return
                closed = true
                try { source.close() } finally {
                    sessions--
                    releaseIfFinished()
                }
            }
        }
    }

    fun releaseReader() {
        readerReleased = true
        releaseIfFinished()
    }

    private fun releaseIfFinished() {
        if (readerReleased && sessions == 0 && !released) {
            released = true
            onReleased()
        }
    }
}
