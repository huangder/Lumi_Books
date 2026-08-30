package com.huangder.lumibooks.ui.settings

/**
 * Produces stable visual shares for the storage cylinder. Positive categories retain a thin
 * visible layer while the exact byte values remain authoritative in the adjacent labels.
 */
internal fun storageSegmentFractions(bytes: List<Long>): List<Float> {
    val safeBytes = bytes.map { it.coerceAtLeast(0L) }
    val total = safeBytes.sum()
    if (total <= 0L) return List(bytes.size) { 0f }

    val minimumVisibleFraction = 0.018f
    val adjusted = safeBytes.map { value ->
        if (value == 0L) 0f else (value.toDouble() / total.toDouble()).toFloat().coerceAtLeast(minimumVisibleFraction)
    }
    val adjustedTotal = adjusted.sum()
    return adjusted.map { it / adjustedTotal }
}

/** Keeps zero-byte categories present as hairline layers without changing their displayed value. */
internal fun storageDisplayFractions(
    fractions: List<Float>,
    zeroFraction: Float = 0.008f
): List<Float> {
    if (fractions.isEmpty()) return emptyList()
    val safeFractions = fractions.map { it.coerceAtLeast(0f) }
    val positiveTotal = safeFractions.sum()
    val zeroCount = safeFractions.count { it == 0f }
    val reservedForZero = (zeroFraction.coerceAtLeast(0f) * zeroCount).coerceAtMost(1f)
    if (positiveTotal == 0f) {
        return safeFractions.map { zeroFraction.coerceAtLeast(0f) }
    }
    val positiveBudget = 1f - reservedForZero
    return safeFractions.map { fraction ->
        if (fraction == 0f) zeroFraction.coerceAtLeast(0f)
        else fraction / positiveTotal * positiveBudget
    }
}
