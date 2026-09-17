package com.huangder.lumibooks.ui.reader.engine

import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan

/**
 * dip 标记的 span（TXT 章首标题的 [AbsoluteSizeSpan]）由框架按 [TextPaint.density]
 * 换算成像素，而该字段默认是 `1.0`（不是屏幕密度）。
 *
 * 任何自建 StaticLayout 的绘制层都必须显式把 density 设成屏幕密度；否则同一段文本
 * 在那一层会被量成 `1 / density` 的字号，与按屏幕密度绘制的字形对不上，
 * 大标题会被挤成一团。
 *
 * 非正数视为无效，退回 TextPaint 的默认值，避免出现零宽排版。
 */
internal fun readerSpanPaintDensity(displayMetricsDensity: Float): Float =
    displayMetricsDensity.takeIf { it.isFinite() && it > 0f } ?: 1f
