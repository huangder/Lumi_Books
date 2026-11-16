package com.huangder.lumibooks.ui.animation

import android.os.Build
import android.view.RoundedCorner
import android.view.View

/** Radius in px the display reports for one physical corner, or null when unknown. */
internal fun reportedScreenCornerPx(view: View, position: Int): Float? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        view.rootView.rootWindowInsets?.getRoundedCorner(position)?.radius?.toFloat()
    } else null

/**
 * Smallest physical corner radius in px, or 0 when the display reports none.
 * A clip never exceeds the hardware curve, so no sliver of the background shows at a corner.
 */
internal fun screenCornerRadiusPx(view: View): Float {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0f
    val radii = listOf(
        RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
        RoundedCorner.POSITION_BOTTOM_RIGHT, RoundedCorner.POSITION_BOTTOM_LEFT
    ).map { reportedScreenCornerPx(view, it) ?: 0f }
    return radii.min().coerceAtLeast(0f)
}
