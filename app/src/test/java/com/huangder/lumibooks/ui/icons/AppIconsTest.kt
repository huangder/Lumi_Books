package com.huangder.lumibooks.ui.icons

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconsTest {
    @Test
    fun iconPairResolvesRegularAndFilledVariants() {
        assertSame(AppIcons.Heart.regular, AppIcons.Heart.resolve(selected = false))
        assertSame(AppIcons.Heart.filled, AppIcons.Heart.resolve(selected = true))
        assertSame(AppIcons.Bookmark.regular, AppIcons.Bookmark.resolve(selected = false))
        assertSame(AppIcons.Bookmark.filled, AppIcons.Bookmark.resolve(selected = true))
    }

    @Test
    fun allRequiredStatePairsExposeBothVectors() {
        listOf(
            AppIcons.House,
            AppIcons.BooksPair,
            AppIcons.ChartBarPair,
            AppIcons.HomeTab,
            AppIcons.BookshelfTab,
            AppIcons.StatisticsTab,
            AppIcons.BookOpenPair,
            AppIcons.ArrowsLeftRightPair,
            AppIcons.ArrowsDownUpPair,
            AppIcons.RowsPair,
            AppIcons.SunPair,
            AppIcons.MoonPair,
            AppIcons.LightningPair,
            AppIcons.KeyPair,
            AppIcons.MicrophonePair,
            AppIcons.FilmStripPair,
            AppIcons.PalettePair,
            AppIcons.TextAaPair
        ).forEach { pair ->
            assertTrue(pair.regular.defaultWidth.value > 0f)
            assertTrue(pair.regular.defaultHeight.value > 0f)
            assertTrue(pair.filled.defaultWidth.value > 0f)
            assertTrue(pair.filled.defaultHeight.value > 0f)
        }
    }

    @Test
    fun bottomTabsStayFilledInBothStatesAndDirectionsAreValid() {
        listOf(AppIcons.HomeTab, AppIcons.BookshelfTab, AppIcons.StatisticsTab).forEach { pair ->
            assertSame(pair.regular, pair.filled)
            assertTrue(pair.regular.defaultWidth.value > 0f)
        }
        listOf(AppIcons.ArrowLeft, AppIcons.ArrowRight, AppIcons.CaretLeft, AppIcons.CaretRight).forEach {
            assertTrue(it.defaultWidth.value > 0f)
            assertTrue(it.defaultHeight.value > 0f)
        }
    }
}
