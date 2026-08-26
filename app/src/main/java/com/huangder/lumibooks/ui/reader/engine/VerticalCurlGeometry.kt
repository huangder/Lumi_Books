package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.abs
import kotlin.math.min

internal class VerticalCurlFrame {
    var progress: Float = 0f
    var creaseX: Float = 0f
    var foldedEdgeX: Float = 0f
    var curveInset: Float = 0f
    var foldWidth: Float = 0f
    var terminal: Boolean = false
}

/**
 * Geometry for a vertical cylindrical fold.
 *
 * [positionX] is the projected position of the dragged page edge. The crease is
 * halfway between that edge and the page's original right edge. A complete turn
 * therefore ends at `-width`, not at zero.
 */
internal object VerticalCurlGeometry {
    fun evaluate(width: Float, height: Float, positionX: Float, out: VerticalCurlFrame): Boolean {
        if (!width.isFinite() || !height.isFinite() || !positionX.isFinite() ||
            width <= 0f || height <= 0f
        ) return false

        val foldedEdgeX = positionX.coerceIn(-width, width)
        val creaseX = (width + foldedEdgeX) * 0.5f
        val foldWidth = abs(creaseX - foldedEdgeX)

        out.progress = ((width - foldedEdgeX) / (2f * width)).coerceIn(0f, 1f)
        out.creaseX = creaseX
        out.foldWidth = foldWidth
        out.foldedEdgeX = foldedEdgeX
        out.curveInset = min(foldWidth * 0.055f, height * 0.009f)
        out.terminal = foldedEdgeX <= -width + 0.5f
        return out.creaseX.isFinite() && out.foldedEdgeX.isFinite() &&
            out.curveInset.isFinite() && out.foldWidth.isFinite()
    }
}
