package com.huangder.lumibooks.util.parser

import kotlin.math.roundToInt

internal data class ReaderImageBounds(
    val width: Int,
    val height: Int
)

/**
 * 行内注释标记（脚注图标）的 drawable。
 *
 * 这类图片在书里常按原始像素给尺寸（例如 72px 的"注"字小图），直接按原始尺寸排版会明显
 * 比正文字号大。绘制时按正文字号缩放，排版槽位则按 [ReaderImageSizing.INLINE_MARKER_EM]
 * 的参考字号留白，避免正文字号变化时反复重排整章。
 */
internal interface InlineFootnoteMarkerDrawable {
    val isInlineFootnoteMarker: Boolean
}

/** Shared intrinsic-size policy for images embedded in reader-layout content. */
internal object ReaderImageSizing {
    /** 行内注释图标的参考字号（sp）：排版槽位大小 = 该字号 × [INLINE_MARKER_EM]。 */
    const val INLINE_MARKER_REFERENCE_SP = 20f

    /** 行内注释图标相对正文字号的倍率（约等于出版社常见的 0.7em）。 */
    const val INLINE_MARKER_EM = 0.72f

    fun inlineMarkerSizePx(density: Float): Int =
        (INLINE_MARKER_REFERENCE_SP * INLINE_MARKER_EM * density).roundToInt().coerceAtLeast(1)

    fun bounds(originalWidth: Int, originalHeight: Int, contentWidth: Int): ReaderImageBounds? {
        if (originalWidth <= 0 || originalHeight <= 0 || contentWidth <= 0) return null
        val width = minOf(originalWidth, contentWidth).coerceAtLeast(1)
        val scale = width.toDouble() / originalWidth.toDouble()
        val height = (originalHeight.toDouble() * scale)
            .roundToInt()
            .coerceAtLeast(1)
        return ReaderImageBounds(width, height)
    }

    /** Keep enough decoded width for the final display bounds without upscaling low-res input. */
    fun decodeSampleSize(originalWidth: Int, originalHeight: Int, contentWidth: Int): Int {
        val target = bounds(originalWidth, originalHeight, contentWidth) ?: return 1
        return (originalWidth / target.width).coerceAtLeast(1)
    }
}
