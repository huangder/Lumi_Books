package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.PageRenderMode

/**
 * 栅格页面（PDF / CBZ）按档位决定解码尺寸的纯逻辑，便于单测。
 *
 * 关键约束：正常档**不再解出比屏幕还小的图**。旧的策略是"只要还高于目标宽度 1.25 倍
 * 就继续减半"，于是 3000px 宽的漫画页会被解成 750px，再被 `ContentScale.FillWidth`
 * 放大到 1080px 显示，越大的源图越糊。
 */

/** `BitmapFactory.inSampleSize` 允许的最大档位（继续减半的收益已经很小）。 */
internal const val MAX_PAGE_SAMPLE_SIZE = 16

/** 正常档的最小解码宽度：屏幕很窄时也不至于解出太小的图。 */
internal const val MIN_DECODE_WIDTH_PX = 320

/** 正常档整页像素预算：只对超长 / 超大页生效（约 32MB）。 */
internal const val NORMAL_RENDER_MAX_PIXELS = 8_000_000L

/** 高清档整页像素预算：超出才按 2 的幂降采样（约 64MB）。 */
internal const val HIGH_RENDER_MAX_PIXELS = 16_000_000L

/**
 * CBZ 单页解码需要使用的 [`android.graphics.BitmapFactory.Options.inSampleSize`]。
 *
 * [targetWidthPx] 是这一页在屏幕上的显示宽度（连续滚动即屏幕宽，对开的一屏是半屏宽）。
 */
internal fun comicSampleSize(
    width: Int,
    height: Int,
    targetWidthPx: Int,
    mode: PageRenderMode
): Int {
    if (width <= 0 || height <= 0) return 1
    return when (mode) {
        PageRenderMode.NATIVE -> 1

        PageRenderMode.HIGH -> {
            var sampleSize = 1
            while (sampleSize < MAX_PAGE_SAMPLE_SIZE &&
                samplePixels(width, height, sampleSize) > HIGH_RENDER_MAX_PIXELS
            ) {
                sampleSize *= 2
            }
            sampleSize
        }

        PageRenderMode.NORMAL -> {
            val floor = targetWidthPx.coerceAtLeast(MIN_DECODE_WIDTH_PX)
            var sampleSize = 1
            // 解码宽度不低于显示宽度、最多 2 倍：宁可多花一点内存，
            // 也不要解出比屏幕还小的图再放大。
            while (sampleSize < MAX_PAGE_SAMPLE_SIZE && width / (sampleSize * 2) >= floor) {
                sampleSize *= 2
            }
            // 超长 / 超大页仍然收敛到像素预算内，避免一次分配上百 MB。
            while (sampleSize < MAX_PAGE_SAMPLE_SIZE &&
                samplePixels(width, height, sampleSize) > NORMAL_RENDER_MAX_PIXELS
            ) {
                sampleSize *= 2
            }
            sampleSize
        }
    }
}

/** 某档位的 PDF 渲染倍率上限（PDF 是矢量的，倍率即清晰度）。 */
internal fun pdfMaxRenderScale(mode: PageRenderMode): Float = when (mode) {
    PageRenderMode.NORMAL -> 4f
    PageRenderMode.HIGH -> 5f
    PageRenderMode.NATIVE -> 6f
}

/**
 * PDF 是矢量页，没有像漫画图片那样的“原图像素”。各档位用相对屏幕显示宽度的
 * 超采样倍率区分清晰度，再由绝对倍率和像素预算收敛内存占用。
 */
internal fun pdfRenderOversample(mode: PageRenderMode): Float = when (mode) {
    PageRenderMode.NORMAL -> 1.5f
    PageRenderMode.HIGH -> 2.5f
    PageRenderMode.NATIVE -> 4f
}

/** 某档位的 PDF 整页像素预算；null 表示不设上限。 */
internal fun pdfRenderPixelBudget(mode: PageRenderMode): Long? = when (mode) {
    PageRenderMode.NORMAL -> 4_000_000L
    PageRenderMode.HIGH -> 16_000_000L
    PageRenderMode.NATIVE -> null
}

/** PDF 页按屏幕宽度和画质档位计算最终渲染倍率。 */
internal fun pdfRenderScale(
    pageWidth: Int,
    pageHeight: Int,
    targetWidthPx: Int,
    mode: PageRenderMode
): Float {
    if (pageWidth <= 0 || pageHeight <= 0 || targetWidthPx <= 0) return MIN_PDF_RENDER_SCALE
    val requestedScale = (
        targetWidthPx.toFloat() / pageWidth.toFloat() * pdfRenderOversample(mode)
        ).coerceIn(MIN_PDF_RENDER_SCALE, pdfMaxRenderScale(mode))
    val requestedPixels = pageWidth.toDouble() * pageHeight.toDouble() *
        requestedScale.toDouble() * requestedScale.toDouble()
    val pixelBudget = pdfRenderPixelBudget(mode)
    return if (pixelBudget != null && requestedPixels > pixelBudget) {
        (requestedScale * kotlin.math.sqrt(pixelBudget.toDouble() / requestedPixels)).toFloat()
    } else {
        requestedScale
    }
}

/**
 * 缩放手势持续刷新时只在半倍分辨率边界触发新渲染，避免每一帧都分配大位图。
 * 向上取整保证新图的像素宽度不低于当前显示倍率。
 */
internal fun pdfZoomRenderBucket(scale: Float): Float {
    val normalized = scale.coerceIn(1f, 5f)
    return if (normalized <= 1.05f) {
        1f
    } else {
        kotlin.math.ceil(normalized * 2f) / 2f
    }
}

private const val PAGE_CACHE_WIDTH_BUCKET_PX = 128
private const val PAGE_CACHE_WIDTH_BITS = 20
private const val PAGE_CACHE_MODE_BITS = 2
private const val MIN_PDF_RENDER_SCALE = 0.2f

/** 返回缓存宽度桶的上边界，同桶首次渲染的图不会比后续请求小。 */
internal fun pageRenderTargetWidth(targetWidthPx: Int): Int {
    if (targetWidthPx <= 0) return 0
    return ((targetWidthPx + PAGE_CACHE_WIDTH_BUCKET_PX - 1) / PAGE_CACHE_WIDTH_BUCKET_PX) *
        PAGE_CACHE_WIDTH_BUCKET_PX
}

/**
 * 页面位图缓存键：同一页的不同档位、不同目标宽度分开缓存。
 * 宽度按 128px 分桶，避免系统栏尺寸小幅波动时重复渲染。
 */
internal fun pageRenderCacheKey(
    pageIndex: Int,
    mode: PageRenderMode,
    targetWidthPx: Int
): Long {
    val widthBucket = (pageRenderTargetWidth(targetWidthPx) / PAGE_CACHE_WIDTH_BUCKET_PX)
        .coerceAtMost((1 shl PAGE_CACHE_WIDTH_BITS) - 1)
    return (pageIndex.toLong() shl (PAGE_CACHE_WIDTH_BITS + PAGE_CACHE_MODE_BITS)) or
        (mode.ordinal.toLong() shl PAGE_CACHE_WIDTH_BITS) or
        widthBucket.toLong()
}

private fun samplePixels(width: Int, height: Int, sampleSize: Int): Long {
    val sampledWidth = (width / sampleSize).coerceAtLeast(1).toLong()
    val sampledHeight = (height / sampleSize).coerceAtLeast(1).toLong()
    return sampledWidth * sampledHeight
}
