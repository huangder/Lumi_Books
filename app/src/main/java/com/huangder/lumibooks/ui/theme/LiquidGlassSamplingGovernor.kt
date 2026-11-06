package com.huangder.lumibooks.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.FrameMetrics
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun rememberAdaptiveLiquidGlassSamplingQuality(
    selected: LiquidGlassSamplingQuality,
    enabled: Boolean,
    view: View
): LiquidGlassSamplingQuality {
    val lifecycleOwner = LocalLifecycleOwner.current
    var effective by remember(selected) { mutableStateOf(selected) }

    SideEffect {
        if (!enabled && effective != selected) effective = selected
    }

    DisposableEffect(lifecycleOwner, view, selected, enabled) {
        val window = view.context.findActivity()?.window
        val displayRate = window?.decorView?.display?.refreshRate
            ?.takeIf { it.isFinite() && it > 0f } ?: 60f
        val fallbackBudgetNanos = if (displayRate <= 60.5f) {
            (1_000_000_000.0 / displayRate).toLong()
        } else {
            null
        }

        if (!enabled || selected == LiquidGlassSamplingQuality.PERFORMANCE || window == null ||
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && fallbackBudgetNanos == null)
        ) {
            onDispose { }
        } else {
            val governor = LiquidGlassSamplingGovernor(selected)
            val worker = HandlerThread("LumiGlassFrameMetrics").apply { start() }
            val workerHandler = Handler(worker.looper)
            val mainHandler = Handler(Looper.getMainLooper())
            val active = AtomicBoolean(true)
            var latest = selected
            val listening = AtomicBoolean(false)

            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                if (!active.get() || !listening.get()) return@OnFrameMetricsAvailableListener
                val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                val reportedBudget = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    metrics.getMetric(FrameMetrics.DEADLINE).takeIf { it > 0L }
                } else {
                    null
                }
                val budget = reportedBudget ?: fallbackBudgetNanos
                if (budget != null) {
                    val next = governor.recordFrame(duration, budget, android.os.SystemClock.elapsedRealtime())
                    if (next != latest) {
                        latest = next
                        mainHandler.post {
                            if (active.get() && listening.get()) effective = next
                        }
                    }
                }
            }

            fun startListening() {
                if (active.get() && listening.compareAndSet(false, true)) {
                    governor.resetWindow()
                    runCatching { window.addOnFrameMetricsAvailableListener(listener, workerHandler) }
                        .onFailure { listening.set(false) }
                }
            }

            fun stopListening() {
                if (listening.getAndSet(false)) {
                    window.removeOnFrameMetricsAvailableListener(listener)
                    governor.resetWindow()
                }
            }

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> startListening()
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> stopListening()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startListening()

            onDispose {
                active.set(false)
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                stopListening()
                worker.quitSafely()
            }
        }
    }

    return if (enabled) effective else selected
}

internal class LiquidGlassSamplingGovernor(
    private val ceiling: LiquidGlassSamplingQuality
) {
    var quality: LiquidGlassSamplingQuality = ceiling
        private set

    private var windowStartMs = -1L
    private var frames = 0
    private var missedFrames = 0
    private var slowWindows = 0
    private var stableWindows = 0
    private var lastChangeMs = 0L

    @Synchronized
    fun recordFrame(durationNanos: Long, budgetNanos: Long, nowMs: Long): LiquidGlassSamplingQuality {
        if (durationNanos <= 0L || budgetNanos <= 0L) return quality
        if (windowStartMs < 0L || nowMs < windowStartMs) windowStartMs = nowMs

        frames++
        if (durationNanos > budgetNanos) missedFrames++
        if (nowMs - windowStartMs < WINDOW_MS) return quality

        if (frames >= MIN_WINDOW_FRAMES) {
            val missedRatio = missedFrames.toFloat() / frames
            when {
                missedRatio >= SLOW_WINDOW_RATIO -> {
                    slowWindows++
                    stableWindows = 0
                }
                missedRatio <= STABLE_WINDOW_RATIO -> {
                    stableWindows++
                    slowWindows = 0
                }
                else -> {
                    slowWindows = 0
                    stableWindows = 0
                }
            }

            if (slowWindows >= SLOW_WINDOWS_TO_DOWNGRADE &&
                quality != LiquidGlassSamplingQuality.PERFORMANCE &&
                nowMs - lastChangeMs >= CHANGE_COOLDOWN_MS
            ) {
                quality = LiquidGlassSamplingQuality.entries[quality.ordinal + 1]
                slowWindows = 0
                stableWindows = 0
                lastChangeMs = nowMs
            } else if (stableWindows >= STABLE_WINDOWS_TO_RESTORE &&
                quality.ordinal > ceiling.ordinal &&
                nowMs - lastChangeMs >= CHANGE_COOLDOWN_MS
            ) {
                quality = LiquidGlassSamplingQuality.entries[quality.ordinal - 1]
                stableWindows = 0
                slowWindows = 0
                lastChangeMs = nowMs
            }
        } else {
            slowWindows = 0
            stableWindows = 0
        }

        windowStartMs = nowMs
        frames = 0
        missedFrames = 0
        return quality
    }

    @Synchronized
    fun resetWindow() {
        windowStartMs = -1L
        frames = 0
        missedFrames = 0
        slowWindows = 0
        stableWindows = 0
    }

    private companion object {
        const val WINDOW_MS = 1_000L
        const val MIN_WINDOW_FRAMES = 8
        const val SLOW_WINDOW_RATIO = 0.20f
        const val STABLE_WINDOW_RATIO = 0.02f
        const val SLOW_WINDOWS_TO_DOWNGRADE = 2
        const val STABLE_WINDOWS_TO_RESTORE = 3
        const val CHANGE_COOLDOWN_MS = 1_000L
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.findActivity()
    else -> null
}
