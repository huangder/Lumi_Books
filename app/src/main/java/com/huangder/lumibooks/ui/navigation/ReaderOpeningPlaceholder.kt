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
import androidx.compose.runtime.rememberUpdatedState
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
 * Keep a solid paper surface visible while the reader document loads behind the window.
 */
internal fun shouldMountReaderContent(
    state: ReaderDocumentState,
    windowReady: Boolean = true
): Boolean = windowReady && (
    state.error != null ||
        state.book != null ||
        (!state.isLoading && state.book == null)
    )

/** Delays mounting reader content until the route transition has settled. */
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

/** A new reader loads Room itself; only later requests need to replace its existing session. */
@Composable
internal fun ObserveReaderPositionReload(readerIdentity: Any, requestToken: Long, onReload: (Long) -> Unit) {
    val initialToken = remember(readerIdentity) { requestToken }
    val latestReload by rememberUpdatedState(onReload)
    LaunchedEffect(readerIdentity, requestToken) {
        if (requestToken != 0L && requestToken != initialToken) latestReload(requestToken)
    }
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
