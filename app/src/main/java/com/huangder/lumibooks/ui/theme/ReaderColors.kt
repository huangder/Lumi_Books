package com.ebook.reader.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 阅读页专用颜色配置
 *
 * 通过 `LocalReaderColors` 提供，方便后续实现主题切换（日间/夜间/护眼/绿色）。
 * 使用方式：
 *   val colors = LocalReaderColors.current
 *   colors.background  // 阅读页背景色
 *   colors.textPrimary  // 正文颜色
 */
@Immutable
data class ReaderColors(
    val background: Color,      // 阅读页背景
    val surface: Color,         // 卡片/菜单背景
    val textPrimary: Color,     // 正文
    val textSecondary: Color,   // 辅助文字
    val accent: Color,          // 强调色
    val divider: Color,         // 分割线
    val scrim: Color            // 遮罩层
) {
    companion object {
        /** 日间模式（默认） */
        val Light = ReaderColors(
            background = Color(0xFFFBFBFC),
            surface = Color.White,
            textPrimary = Color(0xFF1C1C1E),
            textSecondary = Color(0xFF6E6E73),
            accent = Color(0xFF6C231D),
            divider = Color(0xFFE5E5EA),
            scrim = Color.Black.copy(alpha = 0.4f)
        )

        /** 夜间模式 */
        val Dark = ReaderColors(
            background = Color(0xFF000000),
            surface = Color(0xFF1C1C1E),
            textPrimary = Color(0xFFEBEBF5),
            textSecondary = Color(0xFF98989D),
            accent = Color(0xFFD4736A),
            divider = Color(0xFF38383A),
            scrim = Color.Black.copy(alpha = 0.6f)
        )

        /** 护眼模式 */
        val Sepia = ReaderColors(
            background = Color(0xFFF5E6D3),
            surface = Color(0xFFFAF0E6),
            textPrimary = Color(0xFF3E2723),
            textSecondary = Color(0xFF6D4C41),
            accent = Color(0xFF6C231D),
            divider = Color(0xFFD7CCC8),
            scrim = Color.Black.copy(alpha = 0.4f)
        )

        /** 绿色模式 */
        val Green = ReaderColors(
            background = Color(0xFFE8F5E9),
            surface = Color(0xFFF1F8F2),
            textPrimary = Color(0xFF1B5E20),
            textSecondary = Color(0xFF4E7A50),
            accent = Color(0xFF2E7D32),
            divider = Color(0xFFC8E6C9),
            scrim = Color.Black.copy(alpha = 0.4f)
        )
    }
}

/** 当前阅读页颜色（默认浅色，后续可通过 CompositionLocal 切换） */
val LocalReaderColors = androidx.compose.runtime.compositionLocalOf { ReaderColors.Light }

/** 便捷访问 */
val currentReaderColors: ReaderColors
    @Composable get() = LocalReaderColors.current
