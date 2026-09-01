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

internal interface CurlFrameSource {
    fun acquireCurlFrame(): RenderResourceLease<Bitmap>?
}

enum class CurlBackTextureMode {
    PAPER,
    FADED_MIRROR
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
    var prevPageView: View,
    var curPageView: View,
    var nextPageView: View,
    private val backgroundColorProvider: () -> Int,
    private val reversePageProgressProvider: () -> Boolean = { false },
    val snapTranslationsToPixels: Boolean = false,
    val animatePageViewsDirectly: Boolean = false,
    val curlBackTextureMode: CurlBackTextureMode = CurlBackTextureMode.PAPER,
    private val directPageRenderer: ((Canvas, View) -> Boolean)? = null
) {
    val context: Context get() = root.context
    val resources: Resources get() = root.resources
    val width: Int get() = root.width
    val height: Int get() = root.height
    val bgColor: Int get() = backgroundColorProvider()
    val isPageProgressReversed: Boolean get() = reversePageProgressProvider()
    val hasDirectPageRenderer: Boolean get() = directPageRenderer != null

    fun drawPageDirectly(canvas: Canvas, pageView: View): Boolean =
        directPageRenderer?.invoke(canvas, pageView) == true

    fun invalidate() = root.invalidate()

    fun postInvalidateOnAnimation() = root.postInvalidateOnAnimation()

    fun post(action: () -> Unit) = root.post(action)

    fun postDelayed(delayMillis: Long, action: () -> Unit) = root.postDelayed(action, delayMillis)
}
