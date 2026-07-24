package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

/**
 * 单页绘制 View。
 *
 * 持有两层 Bitmap：文字层 + 标注层（高亮/选区）。
 * onDraw 先画文字层再叠加标注层，实现分层渲染。
 * 选区变化时只重绘标注层（轻量），文字层仅在翻页/配置变更时重绘。
 */
class PageSurfaceView(context: Context) : View(context) {

    private var pageBitmap: Bitmap? = null
    private var annotationBitmap: Bitmap? = null
    private var isSet: Boolean = false

    fun setPageBitmap(bitmap: Bitmap?) {
        pageBitmap = bitmap
        isSet = bitmap != null
        invalidate()
    }

    fun hasPage(): Boolean = isSet

    fun getPageBitmap(): Bitmap? = pageBitmap

    /** 设置标注层 Bitmap（透明叠加层）。null 表示清除标注。 */
    fun setAnnotationBitmap(bitmap: Bitmap?) {
        annotationBitmap?.let { if (!it.isRecycled) it.recycle() }
        annotationBitmap = bitmap
        invalidate()
    }

    fun getAnnotationBitmap(): Bitmap? = annotationBitmap

    fun clearPage() {
        pageBitmap = null
        annotationBitmap?.let { if (!it.isRecycled) it.recycle() }
        annotationBitmap = null
        isSet = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pageBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            }
        }
        annotationBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            }
        }
    }
}
