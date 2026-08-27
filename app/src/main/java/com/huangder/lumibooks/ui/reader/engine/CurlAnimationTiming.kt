package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.roundToInt

internal const val CURL_MIN_SETTLE_FRACTION = 0.425f

internal fun curlSettleDurationMs(
    baseDurationMs: Int,
    remainingDistance: Float,
    fullDistance: Float
): Int {
    val base = baseDurationMs.coerceAtLeast(1)
    val minimum = (base * CURL_MIN_SETTLE_FRACTION).roundToInt()
    val remainingFraction = if (fullDistance > 0f) {
        (remainingDistance / fullDistance).coerceIn(0f, 1f)
    } else {
        0f
    }
    return (minimum + (base - minimum) * remainingFraction).roundToInt()
        .coerceIn(minimum, base)
}

internal fun curlExpeditedDurationMs(baseDurationMs: Int): Int =
    (baseDurationMs * 0.3f).roundToInt().coerceIn(120, 240)
