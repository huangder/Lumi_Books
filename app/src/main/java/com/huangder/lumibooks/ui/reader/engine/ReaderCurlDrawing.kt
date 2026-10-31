package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/**
 * A curl owns its drawing commands for the whole turn. ViewGroup.drawChild on a
 * hardware canvas instead records references to live child RenderNodes: hidden
 * slots can lose or replace those display lists while the reader is idle/loading.
 * Draw the native reader's containers and leaf views directly in local coordinates.
 */
internal fun drawReaderCurlChildren(canvas: Canvas, parent: ViewGroup) {
    for (index in 0 until parent.childCount) {
        val child = parent.getChildAt(index)
        if (child.visibility != View.VISIBLE || child.alpha <= 0f) continue
        val save = canvas.save()
        try {
            canvas.translate((child.left - parent.scrollX).toFloat(), (child.top - parent.scrollY).toFloat())
            canvas.concat(child.matrix)
            canvas.clipRect(0, 0, child.width, child.height)
            if (child.alpha < 1f) {
                canvas.saveLayerAlpha(0f, 0f, child.width.toFloat(), child.height.toFloat(),
                    (child.alpha * 255f).roundToInt())
            }
            if (child is PageContentView) child.drawCurlFrame(canvas) else child.draw(canvas)
        } finally {
            canvas.restoreToCount(save)
        }
    }
}
