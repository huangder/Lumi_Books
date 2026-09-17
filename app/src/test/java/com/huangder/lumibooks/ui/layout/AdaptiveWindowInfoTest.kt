package com.huangder.lumibooks.ui.layout

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
    fun `wide landscape additionally requires landscape orientation`() {
        assertTrue(AdaptiveWindowInfo(widthDp = 717, heightDp = 512).isWideLandscape)
        assertFalse(AdaptiveWindowInfo(widthDp = 717, heightDp = 900).isWideLandscape)
        assertFalse(AdaptiveWindowInfo(widthDp = 599, heightDp = 400).isWideLandscape)
        assertFalse(AdaptiveWindowInfo(widthDp = 600, heightDp = 600).isWideLandscape)
    }
}
