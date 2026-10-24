package com.huangder.lumibooks.ui.reader

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.huangder.lumibooks.ui.reader.engine.ReaderSelectionMagnifier
import org.json.JSONObject

/** Chromium hides its native handles when JavaScript changes the selection range. */
internal class WebViewSelectionHandles(
    private val host: View,
    private val move: (start: Boolean, x: Float, y: Float) -> Unit
) {
    private data class Anchor(val x: Float, val top: Float, val bottom: Float)
    private var anchors: List<Anchor> = emptyList()
    private val density get() = host.resources.displayMetrics.density
    private val radius get() = 8f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(68, 138, 255) }
    private val magnifier = ReaderSelectionMagnifier(host)
    private var dragging: Int? = null
    private var grabX = 0f
    private var grabY = 0f
    private var pixelRatio = 1f
    val isVisible get() = anchors.size == 2

    fun update(payload: JSONObject?, ratio: Float) {
        pixelRatio = ratio
        anchors = if (payload == null) emptyList() else listOf("start", "end").mapNotNull { key ->
            val point = payload.optJSONObject(key) ?: return@mapNotNull null
            val x = point.optDouble("x", Double.NaN).toFloat() * ratio
            val top = point.optDouble("top", Double.NaN).toFloat() * ratio
            val bottom = point.optDouble("bottom", Double.NaN).toFloat() * ratio
            if (x.isFinite() && top.isFinite() && bottom.isFinite()) Anchor(x, top, bottom) else null
        }
        host.invalidate()
    }

    fun clear() {
        anchors = emptyList()
        finishDrag()
        host.invalidate()
    }

    fun draw(canvas: Canvas) {
        if (!isVisible) return
        for (anchor in anchors) {
            canvas.drawRect(anchor.x - density, anchor.bottom, anchor.x + density,
                anchor.bottom + radius, paint)
            canvas.drawCircle(anchor.x, anchor.bottom + radius, radius, paint)
        }
    }

    fun onTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && isVisible) {
            val hitRadius = 26f * density
            dragging = anchors.indices.filter { index ->
                val anchor = anchors[index]
                kotlin.math.abs(event.x - anchor.x) <= hitRadius &&
                    kotlin.math.abs(event.y - anchor.bottom - radius) <= hitRadius
            }.minByOrNull { index ->
                val anchor = anchors[index]
                val dx = event.x - anchor.x
                val dy = event.y - anchor.bottom - radius
                dx * dx + dy * dy
            }
            val anchor = dragging?.let(anchors::get) ?: return false
            grabX = event.x - anchor.x
            grabY = event.y - (anchor.top + anchor.bottom) / 2f
            host.parent?.requestDisallowInterceptTouchEvent(true)
            magnifier.showAt(anchor.x, (anchor.top + anchor.bottom) / 2f, anchor.bottom - anchor.top)
            return true
        }
        val index = dragging ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val x = event.x - grabX
                val y = event.y - grabY
                move(index == 0, x / pixelRatio, y / pixelRatio)
                val anchor = anchors.getOrNull(index)
                if (anchor != null) magnifier.showAt(x, y, anchor.bottom - anchor.top)
                if (event.actionMasked == MotionEvent.ACTION_UP) finishDrag()
            }
            MotionEvent.ACTION_CANCEL -> finishDrag()
        }
        return true
    }

    private fun finishDrag() {
        if (dragging != null) host.parent?.requestDisallowInterceptTouchEvent(false)
        dragging = null
        magnifier.dismiss()
    }
}
