package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingSubtitleScrollTest {
    @Test
    fun textThatFitsNeedsNoScroll() {
        assertEquals(0, subtitleScrollDistancePx(textWidthPx = 300, viewWidthPx = 400, horizontalPaddingPx = 40))
        // Exactly filling the view still needs no scroll.
        assertEquals(0, subtitleScrollDistancePx(textWidthPx = 360, viewWidthPx = 400, horizontalPaddingPx = 40))
    }

    @Test
    fun overflowingTextScrollsByTheOverflowOnly() {
        assertEquals(60, subtitleScrollDistancePx(textWidthPx = 420, viewWidthPx = 400, horizontalPaddingPx = 40))
        assertEquals(1_600, subtitleScrollDistancePx(textWidthPx = 2_000, viewWidthPx = 400, horizontalPaddingPx = 0))
    }

    @Test
    fun degenerateSizesNeedNoScroll() {
        assertEquals(0, subtitleScrollDistancePx(textWidthPx = 900, viewWidthPx = 0, horizontalPaddingPx = 20))
        assertEquals(0, subtitleScrollDistancePx(textWidthPx = 0, viewWidthPx = 400, horizontalPaddingPx = 20))
        // Negative padding must not shrink the measured content.
        assertEquals(0, subtitleScrollDistancePx(textWidthPx = 380, viewWidthPx = 400, horizontalPaddingPx = -50))
    }

    @Test
    fun durationFollowsTheConfiguredSpeed() {
        // 600px at 60px/s = 10s.
        assertEquals(
            10_000L,
            subtitleScrollDurationMs(distancePx = 600, speedPxPerSecond = 60f, minDurationMs = 900L)
        )
    }

    @Test
    fun durationKeepsTheMinimumForTinyOverflow() {
        assertEquals(
            900L,
            subtitleScrollDurationMs(distancePx = 4, speedPxPerSecond = 600f, minDurationMs = 900L)
        )
    }

    @Test
    fun durationIsZeroWhenNothingHasToMove() {
        assertEquals(0L, subtitleScrollDurationMs(distancePx = 0, speedPxPerSecond = 60f, minDurationMs = 900L))
        assertEquals(0L, subtitleScrollDurationMs(distancePx = 300, speedPxPerSecond = 0f, minDurationMs = 900L))
        assertEquals(0L, subtitleScrollDurationMs(distancePx = -5, speedPxPerSecond = 60f, minDurationMs = 900L))
    }

    @Test
    fun spokenPositionIgnoresWholeSentenceAndForeignRanges() {
        val sentence = TtsTextSegment("abcdefghij", 100, 110)
        assertEquals(null, subtitleSpokenPosition(sentence, sentence))
        assertEquals(null, subtitleSpokenPosition(sentence, TtsTextSegment("xy", 90, 92)))
        assertEquals(null, subtitleSpokenPosition(sentence, null))
        // An estimated clause pages to where it starts, never ahead to its end.
        assertEquals(0, subtitleSpokenPosition(sentence, TtsTextSegment("abcde", 100, 105)))
        assertEquals(3, subtitleSpokenPosition(sentence, TtsTextSegment("de", 103, 105)))
        assertEquals(9, subtitleSpokenPosition(sentence, TtsTextSegment("j", 109, 110)))
    }

    @Test
    fun estimatedDurationFollowsScriptAndSpeechRate() {
        val chinese = "\u4e00".repeat(9)
        assertEquals(2_000L, subtitleEstimatedDurationMs(chinese, 1f))
        assertEquals(1_000L, subtitleEstimatedDurationMs(chinese, 2f))
        assertEquals(1_000L, subtitleEstimatedDurationMs("a".repeat(14), 1f))
        assertEquals(0L, subtitleEstimatedDurationMs("   ", 1f))
    }

    @Test
    fun lyricsKeepTheSpokenLineVisible() {
        assertEquals(0, lyricsTopLine(1, 2, 2))
        assertEquals(0, lyricsTopLine(0, 4, 2))
        assertEquals(2, lyricsTopLine(2, 4, 2))
        assertEquals(2, lyricsTopLine(3, 4, 2))
    }
}
