package com.huangder.lumibooks.ui.reader.engine

import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import kotlin.math.roundToInt

/**
 * 收尾动画的缓动：与 legado-E 一致用线性插值（`Scroller(context, LinearInterpolator())`）。
 * 全程匀速，不会出现"走到一半突然减速、再加速"这种分段感。
 */
internal val CURL_SETTLE_INTERPOLATOR: Interpolator = LinearInterpolator()

/**
 * 收尾时长：与 legado-E 的 `startScroll` 一样按时长/行程比例算
 * （`animationSpeed * |dx| / viewWidth`，这里把一次完整翻页的行程 2 屏宽对应到配置时长），
 * 也就是全程匀速。松手点越靠后、剩余行程越短，收尾就越短。
 */
internal fun curlSettleDurationMs(
    baseDurationMs: Int,
    distance: Float,
    viewWidth: Float
): Int {
    val base = baseDurationMs.coerceAtLeast(1)
    val width = viewWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
    val travel = distance.takeIf { it.isFinite() && it > 0f } ?: 0f
    // 一次完整翻页折角 tip 走过 2 屏宽（右缘 → -viewWidth）。
    val scaled = (base.toFloat() * travel / (2f * width)).roundToInt()
    return scaled.coerceIn(minOf(CURL_SETTLE_MIN_MS, base), base)
}

/** 收尾时长下限，避免极短行程算出 0ms 让动画瞬移。 */
internal const val CURL_SETTLE_MIN_MS = 120

internal fun curlExpeditedDurationMs(baseDurationMs: Int): Int =
    (baseDurationMs * 0.3f).roundToInt().coerceIn(120, 240)
