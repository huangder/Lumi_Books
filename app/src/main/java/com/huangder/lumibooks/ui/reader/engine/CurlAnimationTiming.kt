package com.huangder.lumibooks.ui.reader.engine

import com.huangder.lumibooks.ui.animation.VisualMotion
import kotlin.math.roundToInt

/** Scale a configured duration to a remaining normalized distance. */
internal fun curlSettleDurationMs(
    baseDurationMs: Int,
    distance: Float,
    viewWidth: Float
): Int {
    val base = baseDurationMs.coerceAtLeast(1)
    val width = viewWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
    val travel = distance.takeIf { it.isFinite() && it > 0f } ?: 0f
    val scaled = (base.toFloat() * travel / (2f * width)).roundToInt()
    return scaled.coerceIn(minOf(VisualMotion.durationMillis(CURL_SETTLE_MIN_MS), base), base)
}

/** 收尾时长下限，避免极短行程算出 0ms 让动画瞬移。 */
internal const val CURL_SETTLE_MIN_MS = 120

internal fun curlExpeditedDurationMs(baseDurationMs: Int): Int =
    (baseDurationMs * 0.3f).roundToInt().coerceIn(
        VisualMotion.durationMillis(120), VisualMotion.durationMillis(240))
