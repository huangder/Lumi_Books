package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

internal class FullscreenOverlayState {
    var owner by mutableStateOf<Any?>(null)
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

private val LocalFullscreenOverlay = staticCompositionLocalOf<FullscreenOverlayState?> { null }

/** Places page-owned modal sheets above the navigation chrome in the same window. */
@Composable
internal fun FullscreenOverlayHost(content: @Composable () -> Unit) {
    val host = remember { FullscreenOverlayState() }
    CompositionLocalProvider(LocalFullscreenOverlay provides host) {
        Box(Modifier.fillMaxSize()) {
            content()
            host.content?.let { overlay ->
                Box(Modifier.fillMaxSize().zIndex(1f)) { overlay() }
            }
        }
    }
}

@Composable
internal fun FullscreenOverlay(content: @Composable () -> Unit) {
    val host = LocalFullscreenOverlay.current
    if (host == null) {
        content()
        return
    }
    val owner = remember { Any() }
    val latest by rememberUpdatedState(content)
    val locals = currentCompositionLocalContext
    DisposableEffect(host, owner) {
        host.owner = owner
        host.content = { CompositionLocalProvider(locals) { latest() } }
        onDispose {
            if (host.owner === owner) {
                host.content = null
                host.owner = null
            }
        }
    }
}
