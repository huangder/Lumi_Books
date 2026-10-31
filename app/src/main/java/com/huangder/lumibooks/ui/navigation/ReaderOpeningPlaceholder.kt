package com.huangder.lumibooks.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.huangder.lumibooks.ui.theme.AppColors
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.reader.ReaderDocumentState
import kotlinx.coroutines.delay

/**
 * The reader now loads while the hero window moves, so an early error or an empty result must
 * also wait for the window: mounting any reader surface mid-motion would drop frames.
 */
internal fun shouldMountReaderContent(
    state: ReaderDocumentState,
    windowReady: Boolean = true
): Boolean = windowReady && (
    state.error != null ||
        state.book != null ||
        (!state.isLoading && state.book == null)
    )

/** Becomes true two frames after the window lands, once the full-size shell is on screen. */
@Composable
internal fun rememberReaderMountReady(bookId: String, windowReady: Boolean): Boolean {
    var ready by remember(bookId) { mutableStateOf(windowReady) }
    LaunchedEffect(bookId, windowReady) {
        if (windowReady && !ready) {
            withFrameNanos { }
            withFrameNanos { }
            ready = true
        }
    }
    return ready
}

@Composable
internal fun ReaderOpeningPlaceholder(onNavigateBack: () -> Unit, paperColor: Color = AppColors.PageBg) {
    com.huangder.lumibooks.ui.components.ConfigurableBackHandler(onBack = onNavigateBack)
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        showLoading = true
    }
    Box(Modifier.fillMaxSize().background(paperColor),
        contentAlignment = Alignment.Center) {
        if (showLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}
