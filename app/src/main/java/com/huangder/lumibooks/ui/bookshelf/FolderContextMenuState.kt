package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.animation.AppEasing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class FolderContextMenuState(private val scope: CoroutineScope) {
    private var transitionJob: Job? = null

    var phase by mutableStateOf(ContextMenuPhase.Idle)
        private set

    var selectedFolder by mutableStateOf<LibraryFolder?>(null)
        private set

    var coverBounds by mutableStateOf(Rect.Zero)
        private set

    val coverScale = Animatable(1f)
    val coverPositionProgress = Animatable(0f)
    val scrimAlpha = Animatable(0f)
    val menuAlpha = Animatable(0f)
    val actionsAlpha = Animatable(0f)
    val itemAlpha = Animatable(1f)

    fun onLongPressConfirmed(
        folder: LibraryFolder,
        bounds: Rect,
        onHaptic: () -> Unit
    ) {
        if (phase != ContextMenuPhase.Idle) return
        selectedFolder = folder
        coverBounds = bounds
        phase = ContextMenuPhase.Enlarging
        onHaptic()
        transitionJob?.cancel()
        transitionJob = scope.launch {
            coroutineScope {
                launch {
                    itemAlpha.animateTo(
                        0f,
                        tween(ContextMenuMotion.ItemHideMillis, easing = AppEasing.Decelerate)
                    )
                }
                launch {
                    coverScale.snapTo(1f)
                    coverScale.animateTo(
                        ContextMenuMotion.CoverScaleValue,
                        ContextMenuMotion.CoverScaleEnter
                    )
                }
                launch {
                    coverPositionProgress.snapTo(0f)
                    coverPositionProgress.animateTo(
                        1f,
                        ContextMenuMotion.CoverPositionEnter
                    )
                }
                launch {
                    scrimAlpha.animateTo(
                        1f,
                        tween(ContextMenuMotion.ScrimEnterMillis, easing = ContextMenuMotion.ScrimEnterEasing)
                    )
                }
                launch {
                    if (ContextMenuMotion.InfoPanelEnterDelayMillis > 0L) {
                        delay(ContextMenuMotion.InfoPanelEnterDelayMillis)
                    }
                    menuAlpha.animateTo(1f, ContextMenuMotion.InfoPanelEnter)
                }
                launch {
                    delay(ContextMenuMotion.ActionsPanelEnterDelayMillis)
                    actionsAlpha.animateTo(1f, ContextMenuMotion.ActionsPanelEnter)
                }
            }
            if (phase == ContextMenuPhase.Enlarging) phase = ContextMenuPhase.Visible
        }
    }

    fun dismiss() {
        if (phase != ContextMenuPhase.Visible && phase != ContextMenuPhase.Enlarging) return
        transitionJob?.cancel()
        phase = ContextMenuPhase.Dismissing
        transitionJob = scope.launch {
            coroutineScope {
                launch {
                    actionsAlpha.animateTo(0f, ContextMenuMotion.ActionsPanelExit)
                }
                launch {
                    delay(ContextMenuMotion.InfoPanelExitDelayMillis)
                    menuAlpha.animateTo(0f, ContextMenuMotion.InfoPanelExit)
                }
            }
            coroutineScope {
                launch {
                    coverScale.animateTo(
                        1f,
                        tween(ContextMenuMotion.CoverReturnMillis, easing = ContextMenuMotion.CoverReturnEasing)
                    )
                }
                launch {
                    coverPositionProgress.animateTo(
                        0f,
                        tween(ContextMenuMotion.CoverReturnMillis, easing = ContextMenuMotion.CoverReturnEasing)
                    )
                }
                launch {
                    scrimAlpha.animateTo(
                        0f,
                        tween(ContextMenuMotion.ScrimExitMillis, easing = ContextMenuMotion.ScrimExitEasing)
                    )
                }
            }
            itemAlpha.snapTo(1f)
            coverScale.snapTo(1f)
            coverPositionProgress.snapTo(0f)
            scrimAlpha.snapTo(0f)
            menuAlpha.snapTo(0f)
            actionsAlpha.snapTo(0f)
            selectedFolder = null
            phase = ContextMenuPhase.Idle
        }
    }
}

@Composable
internal fun rememberFolderContextMenuState(): FolderContextMenuState {
    val scope = rememberCoroutineScope()
    return remember { FolderContextMenuState(scope) }
}
