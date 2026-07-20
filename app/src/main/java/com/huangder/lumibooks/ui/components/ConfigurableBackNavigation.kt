package com.huangder.lumibooks.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CancellationException

val LocalPredictiveBackEnabled = staticCompositionLocalOf { true }

private class DetachedBackDispatcherOwner(
    override val lifecycle: Lifecycle
) : OnBackPressedDispatcherOwner {
    override val onBackPressedDispatcher = OnBackPressedDispatcher()
}

@Composable
fun ConfigurableNavigationBack(
    predictiveBackEnabled: Boolean,
    bridgeEnabled: Boolean,
    content: @Composable () -> Unit
) {
    if (predictiveBackEnabled) {
        content()
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val detachedOwner = remember(lifecycleOwner) {
        DetachedBackDispatcherOwner(lifecycleOwner.lifecycle)
    }

    BackHandler(enabled = bridgeEnabled) {
        detachedOwner.onBackPressedDispatcher.onBackPressed()
    }
    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides detachedOwner) {
        content()
    }
}

@Composable
fun ConfigurableActivityBack(
    predictiveBackEnabled: Boolean,
    onBack: () -> Unit
) {
    if (!predictiveBackEnabled) {
        BackHandler(onBack = onBack)
    }
}

/**
 * Returns the current predictive-back gesture progress so transient UI can follow the finger.
 * A completed gesture keeps progress at 1 until the caller removes the handled UI.
 */
@Composable
fun ConfigurableBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
): Float {
    val predictiveBackEnabled = LocalPredictiveBackEnabled.current
    val currentOnBack by rememberUpdatedState(onBack)
    val progress = remember { Animatable(0f) }

    LaunchedEffect(enabled, predictiveBackEnabled) {
        if (!enabled || !predictiveBackEnabled) {
            progress.snapTo(0f)
        }
    }

    if (enabled && predictiveBackEnabled) {
        PredictiveBackHandler(enabled = enabled) { events ->
            try {
                events.collect { event ->
                    val rawProgress = event.progress.coerceIn(0f, 1f)
                    progress.snapTo(rawProgress * rawProgress * rawProgress)
                }
                progress.snapTo(1f)
                currentOnBack()
            } catch (_: CancellationException) {
                progress.animateTo(0f, tween(180))
            }
        }
    } else if (enabled) {
        BackHandler(onBack = currentOnBack)
    }

    return progress.value
}
