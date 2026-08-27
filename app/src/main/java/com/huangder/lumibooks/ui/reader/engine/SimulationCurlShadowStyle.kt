package com.huangder.lumibooks.ui.reader.engine

/**
 * Shared shadow style for the SimulationPageDelegate port. Values are kept
 * as alpha-only constants so geometry/render tests can verify the lightened
 * limits without depending on Android Canvas internals.
 */
internal object SimulationCurlShadowStyle {
    const val FOLD_SHADOW_MAX_ALPHA: Int = 0x38
    const val BACK_PAGE_SHADOW_MAX_ALPHA: Int = 0x30
    const val CREASE_SHADOW_MAX_ALPHA: Int = 0x20
    const val BACK_SURFACE_TINT_ALPHA: Int = 0x14
    const val SHADOW_WIDTH_DP: Float = 25f

    fun widthPx(density: Float): Float {
        val safeDensity = if (density.isFinite()) density.coerceAtLeast(0f) else 1f
        return (SHADOW_WIDTH_DP * safeDensity).coerceAtLeast(1f)
    }

    fun fold(alpha: Int): Int = (alpha.coerceIn(0, FOLD_SHADOW_MAX_ALPHA) shl 24)

    fun backPage(alpha: Int): Int =
        (alpha.coerceIn(0, BACK_PAGE_SHADOW_MAX_ALPHA) shl 24)

    fun crease(alpha: Int): Int = (alpha.coerceIn(0, CREASE_SHADOW_MAX_ALPHA) shl 24)
}
