package com.huangder.lumibooks.ui.reader.engine

/** Direction-specific flat, terminal and fallback states for the curl. */
internal object SimulationCurlTurnMotion {
    fun flatTouchX(width: Float, direction: SimulationCurlTurnDirection): Float {
        val safeWidth = safeWidth(width)
        return when (direction) {
            SimulationCurlTurnDirection.NEXT -> (safeWidth - 1f).coerceAtLeast(0f)
            SimulationCurlTurnDirection.PREVIOUS -> -safeWidth
        }
    }

    fun completionTouchX(
        width: Float,
        height: Float,
        direction: SimulationCurlTurnDirection,
        extendedNextTerminal: Boolean
    ): Float {
        val safeWidth = safeWidth(width)
        return when (direction) {
            SimulationCurlTurnDirection.NEXT -> -if (extendedNextTerminal) {
                CurlTerminalGeometry.completionDistance(safeWidth, height)
            } else {
                safeWidth
            }
            SimulationCurlTurnDirection.PREVIOUS -> safeWidth
        }
    }

    fun hasReachedCompletion(
        touchX: Float,
        targetX: Float,
        direction: SimulationCurlTurnDirection
    ): Boolean {
        if (!touchX.isFinite() || !targetX.isFinite()) return false
        return when (direction) {
            SimulationCurlTurnDirection.NEXT -> touchX <= targetX
            SimulationCurlTurnDirection.PREVIOUS -> touchX >= targetX
        }
    }

    /** Which full page is safest when Bezier geometry degenerates. */
    fun fallbackShowsTurningPage(
        touchX: Float,
        width: Float,
        direction: SimulationCurlTurnDirection
    ): Boolean {
        if (!touchX.isFinite()) return false
        return when (direction) {
            SimulationCurlTurnDirection.NEXT -> touchX > 0f
            SimulationCurlTurnDirection.PREVIOUS -> touchX >= safeWidth(width)
        }
    }

    private fun safeWidth(width: Float): Float =
        if (width.isFinite() && width > 0f) width else 1f
}
