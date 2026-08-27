package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.abs
import kotlin.math.hypot

/** Mutable output for legado-E's two-quadratic-bezier simulation. */
internal class SimulationCurlFrame {
    var cornerX = 0f
    var cornerY = 0f
    var touchX = 0f
    var touchY = 0f
    var middleX = 0f
    var middleY = 0f
    var touchToCornerDistance = 0f
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

internal enum class SimulationCurlCorner { TOP, BOTTOM }

internal enum class SimulationCurlTurnDirection { NEXT, PREVIOUS }

/**
 * Canonical curl geometry ported from legado-E's SimulationPageDelegate
 * (https://github.com/Luoyacheng/legado-E). This file is a modified derivative
 * distributed under GPLv3 together with the Lumi project.
 *
 * Coordinates are always expressed as a page curling from the right edge;
 * callers mirror the Canvas for PREV/reversed layout. All operations are
 * guarded against non-finite and near-zero denominators.
 */
internal object SimulationCurlGeometry {
    private const val EPSILON = 0.1f
    private const val MIN_CORNER_DISTANCE = EPSILON * 3f
    private const val INTERSECTION_EPSILON = 0.001f

    fun cornerForTouchY(height: Float, touchY: Float): SimulationCurlCorner? {
        if (!height.isFinite() || !touchY.isFinite() || height <= 0f) return null
        return if (touchY <= height * 0.5f) SimulationCurlCorner.TOP
        else SimulationCurlCorner.BOTTOM
    }

    /** Map a physical pointer to the canonical right-edge curl coordinate. */
    fun canonicalTouchX(
        width: Float,
        gestureStartX: Float,
        pointerX: Float,
        direction: SimulationCurlTurnDirection
    ): Float {
        if (!width.isFinite() || !gestureStartX.isFinite() || !pointerX.isFinite() || width <= 0f) {
            return 0f
        }
        val delta = pointerX - gestureStartX
        val inward = when (direction) {
            SimulationCurlTurnDirection.NEXT -> minOf(delta, 0f)
            SimulationCurlTurnDirection.PREVIOUS -> -maxOf(delta, 0f)
        }
        val maxTouchX = (width - 1f).coerceAtLeast(-width)
        return (width + inward).coerceIn(-width, maxTouchX)
    }

    fun evaluate(
        width: Float,
        height: Float,
        positionX: Float,
        positionY: Float,
        corner: SimulationCurlCorner,
        out: SimulationCurlFrame,
        horizontalLimit: Float = width
    ): Boolean {
        if (!width.isFinite() || !height.isFinite() || !positionX.isFinite() ||
            !positionY.isFinite() || !horizontalLimit.isFinite() ||
            width <= 0f || height <= 2f || horizontalLimit <= 0f
        ) return false

        val safeLimit = horizontalLimit.coerceAtLeast(width)
        out.cornerX = width
        out.cornerY = if (corner == SimulationCurlCorner.BOTTOM) height else 0f
        out.touchX = positionX.coerceIn(-safeLimit, safeLimit)
        out.touchY = positionY.coerceIn(1f, height - 1f)
        if (abs(out.touchX - out.cornerX) < MIN_CORNER_DISTANCE) {
            out.touchX = out.cornerX - MIN_CORNER_DISTANCE
        }
        if (abs(out.touchY - out.cornerY) < EPSILON) {
            out.touchY = out.cornerY + if (corner == SimulationCurlCorner.TOP) EPSILON else -EPSILON
        }

        if (!calculateControlPoints(out)) return false

        // legado-E keeps the horizontal Bezier start on screen by correcting
        // the projected touch point when the first control segment overshoots.
        if (out.touchX > 0f && out.touchX < width &&
            (out.start1X < 0f || out.start1X > width)
        ) {
            val horizontalDistance = abs(out.cornerX - out.touchX).coerceAtLeast(EPSILON)
            val normalizedStartX = if (out.start1X < 0f) width - out.start1X else out.start1X
            if (!normalizedStartX.isFinite() || normalizedStartX < EPSILON) return false
            val correctedDistance = width * horizontalDistance / normalizedStartX
            val correctedX = abs(out.cornerX - correctedDistance)
            val correctedY = abs(
                out.cornerY - abs(out.cornerX - correctedX) *
                    abs(out.cornerY - out.touchY) / horizontalDistance
            )
            out.touchX = correctedX.coerceIn(-safeLimit, safeLimit)
            out.touchY = correctedY.coerceIn(1f, height - 1f)
            if (abs(out.touchX - out.cornerX) < MIN_CORNER_DISTANCE) {
                out.touchX = out.cornerX - MIN_CORNER_DISTANCE
            }
            if (!calculateControlPoints(out)) return false
        }

        out.start2X = out.cornerX
        out.start2Y = out.control2Y - (out.cornerY - out.control2Y) * 0.5f
        out.touchToCornerDistance = hypot(
            (out.touchX - out.cornerX).toDouble(),
            (out.touchY - out.cornerY).toDouble()
        ).toFloat()

        if (!intersection(
                out.touchX, out.touchY, out.control1X, out.control1Y,
                out.start1X, out.start1Y, out.start2X, out.start2Y,
                out, true
            ) || !intersection(
                out.touchX, out.touchY, out.control2X, out.control2Y,
                out.start1X, out.start1Y, out.start2X, out.start2Y,
                out, false
            )
        ) return false

        out.vertex1X = (out.start1X + 2f * out.control1X + out.end1X) * 0.25f
        out.vertex1Y = (out.start1Y + 2f * out.control1Y + out.end1Y) * 0.25f
        out.vertex2X = (out.start2X + 2f * out.control2X + out.end2X) * 0.25f
        out.vertex2Y = (out.start2Y + 2f * out.control2Y + out.end2Y) * 0.25f

        return allFinite(out)
    }

    private fun calculateControlPoints(out: SimulationCurlFrame): Boolean {
        out.middleX = (out.touchX + out.cornerX) * 0.5f
        out.middleY = (out.touchY + out.cornerY) * 0.5f
        val denominatorX = out.cornerX - out.middleX
        val denominatorY = out.cornerY - out.middleY
        if (abs(denominatorX) < EPSILON || abs(denominatorY) < EPSILON) return false

        out.control1X = out.middleX -
            (out.cornerY - out.middleY) * (out.cornerY - out.middleY) / denominatorX
        out.control1Y = out.cornerY
        out.control2X = out.cornerX
        out.control2Y = out.middleY -
            (out.cornerX - out.middleX) * (out.cornerX - out.middleX) / denominatorY
        out.start1X = out.control1X - (out.cornerX - out.control1X) * 0.5f
        out.start1Y = out.cornerY
        return out.middleX.isFinite() && out.middleY.isFinite() &&
            out.control1X.isFinite() && out.control2Y.isFinite() && out.start1X.isFinite()
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
        out: SimulationCurlFrame,
        first: Boolean
    ): Boolean {
        val denominator = (p1x - p2x) * (p3y - p4y) -
            (p1y - p2y) * (p3x - p4x)
        if (!denominator.isFinite() || abs(denominator) < INTERSECTION_EPSILON) return false
        val determinant1 = p1x * p2y - p1y * p2x
        val determinant2 = p3x * p4y - p3y * p4x
        val x = (determinant1 * (p3x - p4x) - (p1x - p2x) * determinant2) / denominator
        val y = (determinant1 * (p3y - p4y) - (p1y - p2y) * determinant2) / denominator
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

    private fun allFinite(out: SimulationCurlFrame): Boolean =
        out.cornerX.isFinite() && out.cornerY.isFinite() &&
            out.touchX.isFinite() && out.touchY.isFinite() &&
            out.middleX.isFinite() && out.middleY.isFinite() &&
            out.touchToCornerDistance.isFinite() &&
            out.start1X.isFinite() && out.start1Y.isFinite() &&
            out.control1X.isFinite() && out.control1Y.isFinite() &&
            out.vertex1X.isFinite() && out.vertex1Y.isFinite() &&
            out.end1X.isFinite() && out.end1Y.isFinite() &&
            out.start2X.isFinite() && out.start2Y.isFinite() &&
            out.control2X.isFinite() && out.control2Y.isFinite() &&
            out.vertex2X.isFinite() && out.vertex2Y.isFinite() &&
            out.end2X.isFinite() && out.end2Y.isFinite()
}
