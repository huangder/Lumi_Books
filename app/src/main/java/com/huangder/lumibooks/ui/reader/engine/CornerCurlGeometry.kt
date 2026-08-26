package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.abs

internal enum class CurlGeometryMode {
    EDGE_VERTICAL,
    CORNER_TOP,
    CORNER_BOTTOM
}

internal fun isCurlSwipeIntent(deltaX: Float, deltaY: Float): Boolean =
    abs(deltaX) > abs(deltaY) * 0.55f

internal fun directCornerTouchX(width: Float, pointerX: Float, physicalTurnSign: Float): Float {
    if (width <= 0f || !width.isFinite() || !pointerX.isFinite()) return 0f
    val canonicalX = if (physicalTurnSign > 0f) width - pointerX else pointerX
    return canonicalX.coerceIn(-width, width)
}

internal fun cornerShadowEndpointOpacity(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    fadeDistance: Float
): Float {
    if (width <= 0f || height <= 0f || fadeDistance <= 0f) return 0f
    val edgeDistance = minOf(x, width - x, y, height - y).coerceAtLeast(0f)
    val progress = (edgeDistance / fadeDistance).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}

/** Locks the geometry selected from ACTION_DOWN for the lifetime of one gesture. */
internal class CurlGestureModeLock {
    companion object {
        private const val CORNER_X_FRACTION = 0.14f
        private const val CORNER_Y_FRACTION = 0.13f
        private const val DIAGONAL_EDGE_X_FRACTION = 0.38f
        private const val DIAGONAL_SLOPE = 0.42f
        private const val CLASSIFICATION_X_FRACTION = 0.045f
    }

    var mode: CurlGeometryMode = CurlGeometryMode.EDGE_VERTICAL
        private set
    var isLocked: Boolean = false
        private set

    private var downX = 0f
    private var downY = 0f

    fun begin(x: Float, y: Float) {
        downX = x
        downY = y
        mode = CurlGeometryMode.EDGE_VERTICAL
        isLocked = false
    }

    fun lock(
        width: Float,
        height: Float,
        physicalTurnSign: Float,
        deltaX: Float,
        deltaY: Float
    ): CurlGeometryMode {
        if (isLocked) return mode
        if (width <= 0f || height <= 0f || physicalTurnSign == 0f) return mode

        val edgeDistance = if (physicalTurnSign < 0f) width - downX else downX
        if (edgeDistance < 0f) {
            isLocked = true
            return mode
        }

        val nearTop = downY in 0f..(height * CORNER_Y_FRACTION)
        val nearBottom = downY in (height * (1f - CORNER_Y_FRACTION))..height
        val startsInLiteralCorner = edgeDistance <= width * CORNER_X_FRACTION &&
            (nearTop || nearBottom)
        if (!startsInLiteralCorner && abs(deltaX) < width * CLASSIFICATION_X_FRACTION) {
            return mode
        }

        isLocked = true
        val diagonalSlope = abs(deltaY) / abs(deltaX).coerceAtLeast(1f)
        val startsFromTurnEdge = edgeDistance <= width * DIAGONAL_EDGE_X_FRACTION
        val diagonalCorner = startsFromTurnEdge && diagonalSlope >= DIAGONAL_SLOPE
        if (!startsInLiteralCorner && !diagonalCorner) return mode

        mode = when {
            nearTop -> CurlGeometryMode.CORNER_TOP
            nearBottom -> CurlGeometryMode.CORNER_BOTTOM
            downY < height * 0.5f -> CurlGeometryMode.CORNER_TOP
            else -> CurlGeometryMode.CORNER_BOTTOM
        }
        return mode
    }

    fun reset() {
        mode = CurlGeometryMode.EDGE_VERTICAL
        isLocked = false
    }
}

internal class CornerCurlFrame {
    var cornerX = 0f
    var cornerY = 0f
    var touchX = 0f
    var touchY = 0f
    var start1X = 0f
    var start1Y = 0f
    var control1X = 0f
    var control1Y = 0f
    var vertex1X = 0f
    var vertex1Y = 0f
    var end1X = 0f
    var end1Y = 0f
    var start2X = 0f
    var start2Y = 0f
    var control2X = 0f
    var control2Y = 0f
    var vertex2X = 0f
    var vertex2Y = 0f
    var end2X = 0f
    var end2Y = 0f
}

/** Allocation-free Bezier geometry for a curl originating at the right page corner. */
internal object CornerCurlGeometry {
    private const val EPSILON = 0.1f

    fun evaluate(
        width: Float,
        height: Float,
        positionX: Float,
        positionY: Float,
        fromBottom: Boolean,
        out: CornerCurlFrame
    ): Boolean {
        if (!width.isFinite() || !height.isFinite() || !positionX.isFinite() ||
            !positionY.isFinite() || width <= 0f || height <= 0f
        ) return false

        out.cornerX = width
        out.cornerY = if (fromBottom) height else 0f
        out.touchX = positionX.coerceIn(-width, width - EPSILON)
        out.touchY = positionY.coerceIn(1f, height - 1f)
        if (abs(out.touchY - out.cornerY) < EPSILON) {
            out.touchY = out.cornerY + if (fromBottom) -EPSILON else EPSILON
        }

        if (!calculateControlPoints(out)) return false

        if (out.touchX > 0f && out.touchX < width &&
            (out.start1X < 0f || out.start1X > width)
        ) {
            val horizontalDistance = abs(out.cornerX - out.touchX).coerceAtLeast(EPSILON)
            val normalizedStartX = if (out.start1X < 0f) {
                width - out.start1X
            } else {
                out.start1X
            }.coerceAtLeast(EPSILON)
            val correctedDistance = width * horizontalDistance / normalizedStartX
            val correctedX = abs(out.cornerX - correctedDistance)
            val correctedY = abs(
                out.cornerY - abs(out.cornerX - correctedX) *
                    abs(out.cornerY - out.touchY) / horizontalDistance
            )
            out.touchX = correctedX
            out.touchY = correctedY.coerceIn(1f, height - 1f)
            if (!calculateControlPoints(out)) return false
        }

        out.start2X = out.cornerX
        out.start2Y = out.control2Y - (out.cornerY - out.control2Y) * 0.5f
        if (!intersection(
                out.touchX, out.touchY, out.control1X, out.control1Y,
                out.start1X, out.start1Y, out.start2X, out.start2Y,
                out, first = true
            ) || !intersection(
                out.touchX, out.touchY, out.control2X, out.control2Y,
                out.start1X, out.start1Y, out.start2X, out.start2Y,
                out, first = false
            )
        ) return false

        out.vertex1X = (out.start1X + 2f * out.control1X + out.end1X) * 0.25f
        out.vertex1Y = (out.start1Y + 2f * out.control1Y + out.end1Y) * 0.25f
        out.vertex2X = (out.start2X + 2f * out.control2X + out.end2X) * 0.25f
        out.vertex2Y = (out.start2Y + 2f * out.control2Y + out.end2Y) * 0.25f

        return finite(out.start1X, out.start1Y, out.start2X, out.start2Y) &&
            finite(out.control1X, out.control1Y, out.control2X, out.control2Y) &&
            finite(out.end1X, out.end1Y, out.end2X, out.end2Y) &&
            finite(out.vertex1X, out.vertex1Y, out.vertex2X, out.vertex2Y)
    }

    private fun calculateControlPoints(out: CornerCurlFrame): Boolean {
        val middleX = (out.touchX + out.cornerX) * 0.5f
        val middleY = (out.touchY + out.cornerY) * 0.5f
        val denominatorX = out.cornerX - middleX
        val denominatorY = out.cornerY - middleY
        if (abs(denominatorX) < EPSILON || abs(denominatorY) < EPSILON) return false

        out.control1X = middleX -
            (out.cornerY - middleY) * (out.cornerY - middleY) / denominatorX
        out.control1Y = out.cornerY
        out.control2X = out.cornerX
        out.control2Y = middleY -
            (out.cornerX - middleX) * (out.cornerX - middleX) / denominatorY
        out.start1X = out.control1X - (out.cornerX - out.control1X) * 0.5f
        out.start1Y = out.cornerY
        return finite(out.control1X, out.control1Y, out.control2X, out.control2Y)
    }

    private fun intersection(
        p1x: Float,
        p1y: Float,
        p2x: Float,
        p2y: Float,
        p3x: Float,
        p3y: Float,
        p4x: Float,
        p4y: Float,
        out: CornerCurlFrame,
        first: Boolean
    ): Boolean {
        val denominator = (p1x - p2x) * (p3y - p4y) -
            (p1y - p2y) * (p3x - p4x)
        if (abs(denominator) < 0.001f) return false
        val determinant1 = p1x * p2y - p1y * p2x
        val determinant2 = p3x * p4y - p3y * p4x
        val x = (determinant1 * (p3x - p4x) - (p1x - p2x) * determinant2) /
            denominator
        val y = (determinant1 * (p3y - p4y) - (p1y - p2y) * determinant2) /
            denominator
        if (!x.isFinite() || !y.isFinite()) return false
        if (first) {
            out.end1X = x
            out.end1Y = y
        } else {
            out.end2X = x
            out.end2Y = y
        }
        return true
    }

    private fun finite(a: Float, b: Float, c: Float, d: Float): Boolean =
        a.isFinite() && b.isFinite() && c.isFinite() && d.isFinite()
}
