package com.huangder.lumibooks.ui.bookshelf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderCoverLuminanceTest {
    @Test
    fun lightCoverUsesDarkBadge() {
        assertTrue(badgeForSolidColor(0xFFFFFFFF.toInt()))
    }

    @Test
    fun darkCoverUsesLightBadge() {
        assertFalse(badgeForSolidColor(0xFF101010.toInt()))
    }

    @Test
    fun middleToneUsesLightBadge() {
        assertFalse(badgeForSolidColor(0xFFB0B0B0.toInt()))
    }

    @Test
    fun samplingUsesVisibleCropTopRight() {
        val width = 400
        val height = 400
        val usesDark = FolderCoverLuminance.usesDarkBadge(width, height) { x, y ->
            // A square source is center-cropped to x=50..349 for a 3:4 cover. Only that crop's
            // upper-right sample is light; the source image's outer-right strip stays dark.
            if (x in 290..349 && y in 0..79) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        assertTrue(usesDark)
    }

    private fun badgeForSolidColor(color: Int): Boolean =
        FolderCoverLuminance.usesDarkBadge(300, 400) { _, _ -> color }
}
