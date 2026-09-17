package com.huangder.lumibooks.ui.icons

import org.junit.Assert.assertSame
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
            AppIcons.LightningPair,
            AppIcons.KeyPair,
            AppIcons.MicrophonePair,
            AppIcons.FilmStripPair,
            AppIcons.PalettePair,
            AppIcons.TextAaPair
        ).forEach { pair ->
            check(pair.regular.defaultWidth.value > 0f)
            check(pair.filled.defaultHeight.value > 0f)
        }
    }
}
