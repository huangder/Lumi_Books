package com.huangder.lumibooks.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveWindowInfoTest {
    @Test
    fun `599dp remains compact and 600dp enters medium width`() {
        assertFalse(AdaptiveWindowInfo(widthDp = 599, heightDp = 900).isMediumWidthOrLarger)
        assertTrue(AdaptiveWindowInfo(widthDp = 600, heightDp = 900).isMediumWidthOrLarger)
    }

    @Test
    fun `folded and narrow multi-window sizes use compact layout`() {
        assertFalse(AdaptiveWindowInfo(widthDp = 412, heightDp = 915).isMediumWidthOrLarger)
        assertFalse(AdaptiveWindowInfo(widthDp = 500, heightDp = 800).isMediumWidthOrLarger)
    }

    @Test
    fun `unfolded foldable and tablet sizes share medium width layout`() {
        assertTrue(AdaptiveWindowInfo(widthDp = 717, heightDp = 512).isMediumWidthOrLarger)
        assertTrue(AdaptiveWindowInfo(widthDp = 1280, heightDp = 800).isMediumWidthOrLarger)
    }

    @Test
    fun `wide layout includes near square windows but excludes clearly tall portrait`() {
        assertTrue(AdaptiveWindowInfo(widthDp = 717, heightDp = 512).usesWideLayout)
        assertFalse(AdaptiveWindowInfo(widthDp = 717, heightDp = 900).usesWideLayout)
        assertFalse(AdaptiveWindowInfo(widthDp = 599, heightDp = 400).usesWideLayout)
        assertTrue(AdaptiveWindowInfo(widthDp = 600, heightDp = 600).usesWideLayout)
        assertTrue(AdaptiveWindowInfo(widthDp = 852, heightDp = 883).usesWideLayout)
        assertTrue(AdaptiveWindowInfo(widthDp = 883, heightDp = 852).usesWideLayout)
        assertTrue(AdaptiveWindowInfo(widthDp = 673, heightDp = 841).usesWideLayout)
    }

    @Test
    fun `near square layout requires 600dp and ends beyond 5 to 4 height`() {
        assertFalse(AdaptiveWindowInfo(599, 599).usesWideLayout)
        assertTrue(AdaptiveWindowInfo(600, 750).usesWideLayout)
        assertTrue(AdaptiveWindowInfo(600, 750).usesSideChrome)
        assertFalse(AdaptiveWindowInfo(600, 751).usesWideLayout)
        assertFalse(AdaptiveWindowInfo(600, 751).usesSideChrome)
        assertTrue(AdaptiveWindowInfo(600, 751).usesCardSheets)
        assertFalse(isLandscapeOrNearSquare(0, 0))
    }

    @Test
    fun `width and height tiers follow M3 breakpoints`() {
        assertEquals(WindowSizeTier.COMPACT, AdaptiveWindowInfo(599, 900).widthTier)
        assertEquals(WindowSizeTier.MEDIUM, AdaptiveWindowInfo(600, 900).widthTier)
        assertEquals(WindowSizeTier.MEDIUM, AdaptiveWindowInfo(839, 900).widthTier)
        assertEquals(WindowSizeTier.EXPANDED, AdaptiveWindowInfo(840, 900).widthTier)

        assertEquals(WindowSizeTier.COMPACT, AdaptiveWindowInfo(900, 479).heightTier)
        assertEquals(WindowSizeTier.MEDIUM, AdaptiveWindowInfo(900, 480).heightTier)
        assertEquals(WindowSizeTier.MEDIUM, AdaptiveWindowInfo(900, 899).heightTier)
        assertEquals(WindowSizeTier.EXPANDED, AdaptiveWindowInfo(900, 900).heightTier)
    }

    @Test
    fun `phone keeps bottom chrome in portrait and moves bars to the edge in landscape`() {
        val portrait = AdaptiveWindowInfo(widthDp = 412, heightDp = 915, smallestWidthDp = 412)
        assertFalse(portrait.usesSideChrome)
        assertFalse(portrait.usesCardSheets)
        assertTrue(portrait.isPhoneSized)

        val landscape = AdaptiveWindowInfo(widthDp = 915, heightDp = 412, smallestWidthDp = 412)
        assertTrue(landscape.usesSideChrome)
        assertTrue(landscape.usesWideLayout)
        assertTrue(landscape.usesCardSheets)
        // Device identity stays the same while the window enters the tablet layout.
        assertTrue(landscape.isPhoneSized)
    }

    @Test
    fun `landscape phone enters card layout at 600dp regardless of compact height`() {
        assertFalse(AdaptiveWindowInfo(599, 360, 360).usesCardSheets)
        assertTrue(AdaptiveWindowInfo(600, 360, 360).usesCardSheets)
        assertTrue(AdaptiveWindowInfo(800, 360, 360).usesCardSheets)
        assertFalse(AdaptiveWindowInfo(360, 800, 360).usesCardSheets)
    }

    @Test
    fun `tall tablet uses card sheets in both orientations but side chrome only in landscape`() {
        val portrait = AdaptiveWindowInfo(widthDp = 800, heightDp = 1280, smallestWidthDp = 800)
        assertFalse(portrait.usesSideChrome)
        assertTrue(portrait.usesCardSheets)
        assertFalse(portrait.isPhoneSized)

        val landscape = AdaptiveWindowInfo(widthDp = 1280, heightDp = 800, smallestWidthDp = 800)
        assertTrue(landscape.usesSideChrome)
        assertTrue(landscape.usesCardSheets)
        assertFalse(landscape.isPhoneSized)
    }

    @Test
    fun `unfolded foldable behaves like a small tablet`() {
        val landscape = AdaptiveWindowInfo(widthDp = 841, heightDp = 673, smallestWidthDp = 673)
        assertTrue(landscape.usesSideChrome)
        assertTrue(landscape.usesCardSheets)
        assertFalse(landscape.isPhoneSized)

        val portrait = AdaptiveWindowInfo(widthDp = 673, heightDp = 841, smallestWidthDp = 673)
        assertTrue(portrait.usesSideChrome)
        assertTrue(portrait.usesCardSheets)
    }

    @Test
    fun `foldable layout is independent of stale outer or inner smallest width`() {
        // Fold, rotate, resize into a narrow split, and return to the cover display.
        val windows = listOf(
            AdaptiveWindowInfo(412, 915),
            AdaptiveWindowInfo(673, 841),
            AdaptiveWindowInfo(841, 673),
            AdaptiveWindowInfo(673, 673),
            AdaptiveWindowInfo(599, 841),
            AdaptiveWindowInfo(600, 841),
            AdaptiveWindowInfo(500, 800),
            AdaptiveWindowInfo(412, 915)
        )
        for (window in windows) {
            for (reportedSmallestWidth in listOf(412, 673, 800)) {
                val stale = window.copy(smallestWidthDp = reportedSmallestWidth)
                assertEquals(window.widthTier, stale.widthTier)
                assertEquals(window.isMediumWidthOrLarger, stale.isMediumWidthOrLarger)
                assertEquals(window.usesWideLayout, stale.usesWideLayout)
                assertEquals(window.usesSideChrome, stale.usesSideChrome)
                assertEquals(window.usesCardSheets, stale.usesCardSheets)
            }
        }
    }

    @Test
    fun `split screen windows follow the window shape rather than the device`() {
        // Tablet top/bottom split: landscape-shaped window.
        val tabletTopBottom = AdaptiveWindowInfo(widthDp = 800, heightDp = 600, smallestWidthDp = 800)
        assertTrue(tabletTopBottom.usesSideChrome)
        assertTrue(tabletTopBottom.usesCardSheets)

        // Tablet side-by-side half: a near-square window still uses tablet chrome.
        val tabletHalf = AdaptiveWindowInfo(widthDp = 640, heightDp = 800, smallestWidthDp = 800)
        assertTrue(tabletHalf.usesSideChrome)
        assertTrue(tabletHalf.usesCardSheets)

        // Tablet one-third split is phone-width, but the device is still not a phone.
        val tabletThird = AdaptiveWindowInfo(widthDp = 400, heightDp = 800, smallestWidthDp = 800)
        assertFalse(tabletThird.usesSideChrome)
        assertFalse(tabletThird.usesCardSheets)
        assertFalse(tabletThird.isPhoneSized)

        // Landscape phone split: short window, bars move to the edge, sheets stay at the bottom.
        val phoneSplit = AdaptiveWindowInfo(widthDp = 456, heightDp = 388, smallestWidthDp = 412)
        assertTrue(phoneSplit.usesSideChrome)
        assertFalse(phoneSplit.usesCardSheets)
    }

    @Test
    fun `square windows use tablet chrome`() {
        val square = AdaptiveWindowInfo(widthDp = 600, heightDp = 600)
        assertTrue(square.usesSideChrome)
        assertTrue(square.usesCardSheets)
    }
}
