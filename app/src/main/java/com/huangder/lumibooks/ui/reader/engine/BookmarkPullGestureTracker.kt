package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.abs

internal data class BookmarkPullDragUpdate(
    val distancePx: Float,
    val armed: Boolean,
    val justClaimed: Boolean,
    val crossedThreshold: Boolean
)

internal data class BookmarkPullFinish(
    val wasActive: Boolean,
    val commit: Boolean
)

/** Pure gesture classifier shared by both paginated reader hosts. */
internal class BookmarkPullGestureTracker {
    companion object {
        const val START_REGION_DP = 160f
        const val START_REGION_HEIGHT_FRACTION = 0.8f
        const val CLAIM_SLOP_DP = 12f
        const val COMMIT_THRESHOLD_DP = 64f
        const val MAX_DISTANCE_DP = 120f
        const val VERTICAL_DOMINANCE = 1.8f
    }

    private var candidate = false
    private var active = false
    private var startX = 0f
    private var startY = 0f
    private var density = 1f
    private var distancePx = 0f
    private var armed = false
    private var thresholdFeedbackSent = false

    fun start(
        x: Float,
        y: Float,
        density: Float,
        enabled: Boolean,
        startRegionY: Float = y,
        gestureRegionHeight: Float? = null
    ) {
        reset()
        this.density = density.coerceAtLeast(0.1f)
        startX = x
        startY = y
        val startRegionLimit = gestureRegionHeight
            ?.takeIf { it > 0f }
            ?.times(START_REGION_HEIGHT_FRACTION)
            ?: (START_REGION_DP * this.density)
        candidate = enabled && startRegionY in 0f..startRegionLimit
    }

    fun move(x: Float, y: Float): BookmarkPullDragUpdate? {
        if (!candidate && !active) return null

        val rawDistance = (y - startY).coerceAtLeast(0f)
        val horizontalDistance = abs(x - startX)
        var justClaimed = false
        if (!active) {
            val claimSlopPx = CLAIM_SLOP_DP * density
            if (rawDistance <= claimSlopPx ||
                rawDistance < horizontalDistance * VERTICAL_DOMINANCE
            ) {
                return null
            }
            active = true
            justClaimed = true
        }

        distancePx = rawDistance.coerceAtMost(MAX_DISTANCE_DP * density)
        armed = rawDistance >= COMMIT_THRESHOLD_DP * density
        val crossedThreshold = armed && !thresholdFeedbackSent
        if (crossedThreshold) thresholdFeedbackSent = true

        return BookmarkPullDragUpdate(
            distancePx = distancePx,
            armed = armed,
            justClaimed = justClaimed,
            crossedThreshold = crossedThreshold
        )
    }

    fun finish(cancelled: Boolean): BookmarkPullFinish {
        val result = BookmarkPullFinish(
            wasActive = active,
            commit = active && armed && !cancelled
        )
        reset()
        return result
    }

    fun reset() {
        candidate = false
        active = false
        startX = 0f
        startY = 0f
        distancePx = 0f
        armed = false
        thresholdFeedbackSent = false
    }
}
