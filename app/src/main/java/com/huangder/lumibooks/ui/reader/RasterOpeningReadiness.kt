package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalRasterPagePrepared = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** Only pages belonging to the opening viewport can release its cover. */
internal class RasterOpeningReadiness(private val pages: Set<Int>) {
    private val prepared = mutableSetOf<Int>()
    private var reported = false

    fun pagePrepared(page: Int): Boolean {
        if (reported || page !in pages) return false
        prepared += page
        if (!prepared.containsAll(pages)) return false
        reported = true
        return true
    }
}
