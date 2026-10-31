package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookReviewTest {
    @Test
    fun ratingTierTreatsZeroAsUnratedAndRoundsHalfStarsUp() {
        assertEquals(0, reviewRatingTier(0f))
        assertEquals(1, reviewRatingTier(0.1f))
        assertEquals(3, reviewRatingTier(3.4f))
        assertEquals(4, reviewRatingTier(3.5f))
        assertEquals(5, reviewRatingTier(5f))
    }

    @Test
    fun tapSelectsTheTouchedWholeStar() {
        assertEquals(1f, reviewRatingAt(0f, wholeStar = true), 0f)
        assertEquals(1f, reviewRatingAt(0.19f, wholeStar = true), 0f)
        assertEquals(4f, reviewRatingAt(0.7f, wholeStar = true), 0f)
        assertEquals(5f, reviewRatingAt(1f, wholeStar = true), 0f)
    }

    @Test
    fun dragKeepsTenthsAndClampsOutsideTheStars() {
        assertEquals(3.7f, reviewRatingAt(0.74f, wholeStar = false), 0.0001f)
        assertEquals(0f, reviewRatingAt(-0.2f, wholeStar = false), 0f)
        assertEquals(5f, reviewRatingAt(1.3f, wholeStar = false), 0f)
    }

    @Test
    fun previewJoinsParagraphsIntoOneLine() {
        assertEquals("第一段 第二段", reviewPreviewText("  第一段\n\n  第二段\r\n"))
        assertEquals("", reviewPreviewText(" \n "))
    }

    @Test
    fun singleParagraphPreviewMatchesTheFullText() {
        // The details card only offers expanding when the preview differs or is cut off.
        assertEquals("一段话", reviewPreviewText("  一段话  "))
        assertEquals("一段话", "  一段话  ".trim())
    }

    @Test
    fun grayCoverFallsBackToTheAccent() {
        val accent = Color(0xFFE85D5D)
        val gray = Color(0xFF777777)
        assertEquals(reviewAmbientTone(null, accent, isDark = false), reviewAmbientTone(gray, accent, isDark = false))
        assertEquals(reviewAmbientTone(null, accent, isDark = true), reviewAmbientTone(gray, accent, isDark = true))
    }

    @Test
    fun colorfulCoverKeepsItsHueAndFollowsTheTheme() {
        val accent = Color(0xFFE85D5D)
        val blue = Color(0xFF2050C0)
        val light = reviewAmbientTone(blue, accent, isDark = false)
        val dark = reviewAmbientTone(blue, accent, isDark = true)
        assertTrue(light != reviewAmbientTone(null, accent, isDark = false))
        assertTrue(light.blue > light.red)
        assertTrue(light.luminance() > blue.luminance())
        assertTrue(dark.luminance() < blue.luminance())
    }
}
