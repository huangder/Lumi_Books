package com.huangder.lumibooks.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp

/** Geometry is independent of surface material and follows the parent's live return frame. */
internal object CascadingMenuGeometry {
    fun parentScale(progress: Float) = lerp(1f, 0.96f, progress.coerceIn(0f, 1f))
    fun parentContentAlpha(progress: Float) = lerp(1f, 0.60f, progress.coerceIn(0f, 1f))

    fun retreatBounds(bounds: Rect, progress: Float): Rect {
        return scaleBounds(bounds, parentScale(progress))
    }

    fun scaleBounds(bounds: Rect, scale: Float): Rect {
        val dx = bounds.width * (1f - scale) / 2f
        val dy = bounds.height * (1f - scale) / 2f
        return Rect(bounds.left + dx, bounds.top + dy, bounds.right - dx, bounds.bottom - dy)
    }

    fun rowAnchor(parent: Rect, row: Rect, contentWidth: Float, contentHeight: Float): Rect = Rect(
        parent.left + row.left / contentWidth.coerceAtLeast(1f) * parent.width,
        parent.top + row.top / contentHeight.coerceAtLeast(1f) * parent.height,
        parent.left + row.right / contentWidth.coerceAtLeast(1f) * parent.width,
        parent.top + row.bottom / contentHeight.coerceAtLeast(1f) * parent.height
    )

    fun targetRect(
        parent: Rect,
        row: Rect,
        width: Float,
        height: Float,
        safeArea: Rect,
        gap: Float,
        rtl: Boolean,
        protectedBounds: Rect? = null
    ): Rect {
        val w = width.coerceIn(1f, safeArea.width.coerceAtLeast(1f))
        val h = height.coerceIn(1f, safeArea.height.coerceAtLeast(1f))
        val right = opensRight(parent, w, safeArea, gap, rtl)
        val x = (if (right) parent.right + gap else parent.left - gap - w)
            .coerceIn(safeArea.left, (safeArea.right - w).coerceAtLeast(safeArea.left))
        var y = (row.top - gap).coerceIn(safeArea.top, (safeArea.bottom - h).coerceAtLeast(safeArea.top))
        if (protectedBounds != null && Rect(x, y, x + w, y + h).overlaps(protectedBounds)) {
            val above = protectedBounds.top - gap - h
            val below = protectedBounds.bottom + gap
            y = when {
                above >= safeArea.top -> above
                below + h <= safeArea.bottom -> below
                else -> y
            }
        }
        return Rect(x, y, x + w, y + h)
    }

    /** Move the retreating parent only as far as needed to make room for its child. */
    fun parentShift(parent: Rect, childWidth: Float, safeArea: Rect, gap: Float, rtl: Boolean): Float {
        val bounds = retreatBounds(parent, 1f)
        val idealLeft = if (opensRight(bounds, childWidth, safeArea, gap, rtl)) {
            minOf(bounds.left, safeArea.right - childWidth - gap - bounds.width)
        } else {
            maxOf(bounds.left, safeArea.left + childWidth + gap)
        }
        val fitted = idealLeft.coerceIn(safeArea.left, (safeArea.right - bounds.width).coerceAtLeast(safeArea.left))
        return fitted - bounds.left
    }

    private fun opensRight(parent: Rect, width: Float, safeArea: Rect, gap: Float, rtl: Boolean): Boolean {
        val rightSpace = safeArea.right - parent.right - gap
        val leftSpace = parent.left - gap - safeArea.left
        return when {
            !rtl && rightSpace >= width -> true
            rtl && leftSpace >= width -> false
            rightSpace >= width -> true
            leftSpace >= width -> false
            else -> rightSpace >= leftSpace
        }
    }
}
