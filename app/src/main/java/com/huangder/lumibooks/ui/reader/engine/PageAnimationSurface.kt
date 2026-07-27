package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup

interface PageBitmapSource {
    val pageBitmap: Bitmap?
}

/**
 * The three page-sized views consumed by the native page animation engines.
 *
 * Keeping the animation surface independent from [ReadView] lets the EPUB
 * WebView reader feed isolated page snapshots into the same slide and curl
 * implementations used by the Canvas reader.
 */
class PageAnimationSurface(
    private val root: ViewGroup,
    val prevPageView: View,
    val curPageView: View,
    val nextPageView: View,
    private val backgroundColorProvider: () -> Int,
    val snapTranslationsToPixels: Boolean = false,
    val animatePageViewsDirectly: Boolean = false,
    private val directPageRenderer: ((Canvas, View) -> Boolean)? = null
) {
    val context: Context get() = root.context
    val resources: Resources get() = root.resources
    val width: Int get() = root.width
    val height: Int get() = root.height
    val bgColor: Int get() = backgroundColorProvider()
    val hasDirectPageRenderer: Boolean get() = directPageRenderer != null

    fun drawPageDirectly(canvas: Canvas, pageView: View): Boolean =
        directPageRenderer?.invoke(canvas, pageView) == true

    fun invalidate() = root.invalidate()

    fun postInvalidateOnAnimation() = root.postInvalidateOnAnimation()
}
