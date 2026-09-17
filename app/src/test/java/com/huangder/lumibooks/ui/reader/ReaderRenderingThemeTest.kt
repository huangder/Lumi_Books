package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderRenderingThemeTest {
    @Test
    fun publisherSuiteIgnoresSystemDarkMode() {
        assertEquals(
            "day",
            resolveReaderRenderingTheme(
                eInkMode = false,
                publisherPaintSuiteActive = true,
                nightDisplay = true,
                readerTheme = "day",
                hasImageBackground = false
            )
        )
    }

    @Test
    fun publisherSuiteIgnoresExplicitNightMode() {
        assertEquals(
            "day",
            resolveReaderRenderingTheme(
                eInkMode = false,
                publisherPaintSuiteActive = true,
                nightDisplay = true,
                readerTheme = "night",
                hasImageBackground = false
            )
        )
    }

    @Test
    fun regularSuitesStillFollowDarkMode() {
        assertEquals(
            "night",
            resolveReaderRenderingTheme(
                eInkMode = false,
                publisherPaintSuiteActive = false,
                nightDisplay = true,
                readerTheme = "day",
                hasImageBackground = false
            )
        )
        assertEquals(
            "sepia_dark",
            resolveReaderRenderingTheme(
                eInkMode = false,
                publisherPaintSuiteActive = false,
                nightDisplay = true,
                readerTheme = "sepia",
                hasImageBackground = false
            )
        )
        // 自定义图片背景不做夜间映射（沿用既有行为）。
        assertEquals(
            "day",
            resolveReaderRenderingTheme(
                eInkMode = false,
                publisherPaintSuiteActive = false,
                nightDisplay = true,
                readerTheme = "day",
                hasImageBackground = true
            )
        )
    }

    @Test
    fun eInkAlwaysUsesDay() {
        assertEquals(
            "day",
            resolveReaderRenderingTheme(
                eInkMode = true,
                publisherPaintSuiteActive = true,
                nightDisplay = true,
                readerTheme = "night",
                hasImageBackground = false
            )
        )
    }
}
