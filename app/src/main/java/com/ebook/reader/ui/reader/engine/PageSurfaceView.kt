package com.ebook.reader.ui.reader.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

/**
 * 单页绘制 View。
 *
 * 持有一个 Bitmap 引用，在 onDraw 中绘制。
 * 不做任何逻辑处理，只负责绘制。
 */
class PageSurfaceView(context: Context) : View(context) {

    private var pageBitmap: Bitmap? = null
    private var isSet: Boolean = false

    fun setPageBitmap(bitmap: Bitmap?) {
        pageBitmap = bitmap
        isSet = bitmap != null
        invalidate()
    }

    fun hasPage(): Boolean = isSet

    fun getPageBitmap(): Bitmap? = pageBitmap

    fun clearPage() {
        pageBitmap = null
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
    }
}
