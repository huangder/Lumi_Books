package com.huangder.lumibooks.ui.reader.engine

import com.huangder.lumibooks.domain.model.ReaderThemeSettings

/**
 * 用户在主题套装里调整的笔记高亮/划线几何参数（单位 dp）。
 *
 * 只作用于笔记与高亮规则的高亮/划线；选区、搜索、朗读高亮和书籍自带样式保持默认几何，
 * 以免选区手柄与选区方块错位。所有值为默认时，绘制结果与调整前完全一致。
 */
data class ReaderAnnotationStyle(
    /** 高亮左右两端各额外延伸。 */
    val highlightWidthDp: Float = 0f,
    /** 高亮上下各额外延伸。 */
    val highlightHeightDp: Float = 0f,
    /** 高亮整体平移，正值向下（竖排时向左，即远离阅读方向的行首侧）。 */
    val highlightOffsetYDp: Float = 0f,
    /** 划线粗细。 */
    val underlineThicknessDp: Float = ReaderThemeSettings.DEFAULT_UNDERLINE_THICKNESS_DP,
    /** 划线平移，正值远离文字。 */
    val underlineOffsetYDp: Float = 0f
) {
    /** 双线间距、波浪振幅随粗细放大的比例；细于默认值时保持原有间距。 */
    val underlinePatternScale: Float
        get() = (underlineThicknessDp / ReaderThemeSettings.DEFAULT_UNDERLINE_THICKNESS_DP).coerceAtLeast(1f)

    /**
     * 原排版（EPUB WebView）里同一组参数在原生绘制器上的等效值。
     *
     * WebView 的高亮从字形框向内收 1.5px，原生绘制向外扩 2dp；WebView 划线中心位于字形框底部
     * 下方半个 8px 画布（随粗细放大），原生划线中心位于 descent 下方 1dp。设置页的原生预览
     * 用它换算，才能和实际原排版的位置一致。
     */
    fun webViewEquivalent(): ReaderAnnotationStyle = copy(
        highlightHeightDp = highlightHeightDp - WEB_HIGHLIGHT_HEIGHT_DELTA_DP,
        underlineOffsetYDp = underlineOffsetYDp + WEB_UNDERLINE_HALF_BAND_DP * underlinePatternScale -
            NATIVE_UNDERLINE_GAP_DP
    )

    companion object {
        val DEFAULT = ReaderAnnotationStyle()

        /** 原生 glyphPadding 2dp 与 WebView blockInset 1.5px 之差。 */
        private const val WEB_HIGHLIGHT_HEIGHT_DELTA_DP = 3.5f
        private const val WEB_UNDERLINE_HALF_BAND_DP = 4f
        private const val NATIVE_UNDERLINE_GAP_DP = 1f
    }
}

fun ReaderThemeSettings.toAnnotationStyle(): ReaderAnnotationStyle = ReaderAnnotationStyle(
    highlightWidthDp = highlightWidthDp,
    highlightHeightDp = highlightHeightDp,
    highlightOffsetYDp = highlightOffsetYDp,
    underlineThicknessDp = underlineThicknessDp,
    underlineOffsetYDp = underlineOffsetYDp
)
