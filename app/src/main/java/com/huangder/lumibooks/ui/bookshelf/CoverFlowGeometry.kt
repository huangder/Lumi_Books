package com.huangder.lumibooks.ui.bookshelf

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh

/** All distances are pixels. The perspective projection is shared by drawing and hit testing. */
internal data class CoverFlowGeometry(val coverWidth: Float, val coverHeight: Float) {
    val centerGap = coverWidth * 0.62f
    val stackDistance = coverWidth * 0.12f
    val camera = coverWidth * 6f
    val dragStep = centerGap.coerceAtLeast(1f)

    fun contains(distance: Float, xFromStageCenter: Float, yFromCoverCenter: Float): Boolean {
        val pose = transform(distance)
        if (pose.alpha < 0.05f) return false
        val angle = Math.toRadians(pose.rotationY.toDouble())
        val sine = kotlin.math.sin(angle).toFloat()
        val cosine = kotlin.math.cos(angle).toFloat()
        val focal = camera + pose.depth
        val x = xFromStageCenter - pose.x
        val denominator = pose.scale * cosine - x * sine / focal
        if (denominator <= 0f) return false
        val localX = x / denominator
        val localY = yFromCoverCenter * (1f + localX * sine / focal) / pose.scale
        return abs(localX) <= coverWidth / 2f && abs(localY) <= coverHeight / 2f
    }

    fun transform(distance: Float): CoverFlowTransform {
        val a = abs(distance)
        // tanh opens the middle rapidly, then compresses the side stack. Both curves are smooth
        // through zero; no index-based switch is involved when a book becomes the center book.
        val focus = 1f - exp(-a * a * 2.5f)
        val side = (a - 1f).coerceAtLeast(0f)
        val rotation = -sign(distance) * (50f * tanh(a * 1.5f) / tanh(1.5f) +
            7f * (1f - exp(-side * side))).coerceAtMost(62f)
        val depth = coverWidth * (0.383f * focus / (1f - exp(-2.5f)) + 0.35f * side * side / (1f + side))
        val projection = camera / (camera + depth)
        val projectedX = centerGap * tanh(distance * 1.6f) / tanh(1.6f) +
            stackDistance * distance * (1f - exp(-a * a)) * 0.8f -
            stackDistance * tanh(distance * 1.6f) / tanh(1.6f) * (1f - exp(-1f)) * 0.8f
        val edgeFade = (4.5f - a).coerceIn(0f, 1f)
        return CoverFlowTransform(
            x = projectedX,
            depth = depth,
            rotationY = rotation,
            scale = projection,
            alpha = (1f - 0.055f * a).coerceAtLeast(0.72f) * edgeFade,
            blurDp = (0.8f * focus / (1f - exp(-2.5f)) + 0.65f * side).coerceAtMost(3f),
            // Android's RenderNode camera uses Skia's 72-units-per-inch camera space.
            // Passing our pixel focal length directly flattens the visible trapezoids.
            cameraDistance = (camera + depth) / AndroidCameraUnit
        )
    }

    companion object {
        private const val AndroidCameraUnit = 72f
    }
}

internal data class CoverFlowTransform(
    val x: Float,
    val depth: Float,
    val rotationY: Float,
    val scale: Float,
    val alpha: Float,
    val blurDp: Float,
    val cameraDistance: Float
)

internal object CoverFlowPhysics {
    const val EdgeLimit = 0.35f
    const val VisibilityThreshold = 0.001f

    fun resist(position: Float, count: Int): Float {
        if (count <= 1) return 0f
        val edge = position.coerceIn(0f, (count - 1).toFloat())
        val overflow = position - edge
        return edge + overflow / (1f + abs(overflow) / EdgeLimit)
    }

    fun releaseTarget(position: Float, velocity: Float, count: Int, motionEnabled: Boolean): Int {
        if (count <= 1) return 0
        val prediction = if (motionEnabled && abs(velocity) >= 0.4f) {
            (velocity * 0.18f).coerceIn(-3f, 3f)
        } else 0f
        return (position + prediction).roundToInt().coerceIn(0, count - 1)
    }

    fun restoredIndex(ids: List<String>, focusedId: String?, previousPosition: Float): Int {
        if (ids.isEmpty()) return 0
        val byId = ids.indexOf(focusedId)
        return if (byId >= 0) byId else previousPosition.roundToInt().coerceIn(ids.indices)
    }

    fun visibleRange(position: Float, count: Int): IntRange {
        if (count == 0) return IntRange.EMPTY
        val center = position.roundToInt().coerceIn(0, count - 1)
        return (center - 5).coerceAtLeast(0)..(center + 5).coerceAtMost(count - 1)
    }
}
