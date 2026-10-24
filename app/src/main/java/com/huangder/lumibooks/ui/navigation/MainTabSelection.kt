package com.huangder.lumibooks.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.huangder.lumibooks.ui.animation.VisualMotion.tween

/** Tab clicks retain the original 700 ms fade without travelling through other tabs. */
internal class MainTabSelection {
    var fromPage by mutableIntStateOf(0)
        private set
    var toPage by mutableIntStateOf(0)
        private set
    var running by mutableStateOf(false)
        private set
    val progress = Animatable(1f)

    suspend fun select(state: PagerState, page: Int, motionEnabled: Boolean) {
        if (page == state.settledPage && !state.isScrollInProgress) return
        fromPage = state.settledPage
        toPage = page
        progress.snapTo(0f)
        running = true
        try {
            state.scrollToPage(page)
            if (motionEnabled) progress.animateTo(1f, tween(700))
        } finally {
            running = false
        }
    }

    fun alpha(page: Int): Float = when (page) {
        toPage -> progress.value
        fromPage -> 1f - progress.value
        else -> 0f
    }
}
