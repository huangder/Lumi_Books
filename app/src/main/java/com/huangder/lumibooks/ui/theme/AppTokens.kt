package com.huangder.lumibooks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R

/** 全局深色模式状态，由 MainActivity 根据 DataStore 设置注入 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

// ─── 字体 ───
val FangSong = FontFamily(Font(R.font.fandol_fang, FontWeight.Normal))
val KaiTi = FontFamily(Font(R.font.lxgw_wenkai, FontWeight.Normal))
val SansSerif = FontFamily.Default

// ─── 颜色（自动适配深色模式）───
object AppColors {
    // 浅色
    private val LightWindowBg = Color(0xFFFBFBFC)
    private val LightCardBg = Color.White
    private val LightTextPrimary = Color(0xFF000000)
    private val LightTextSecondary = Color(0xFF6E6E73)
    private val LightBgGray = Color(0xFFF2F2F7)
    private val LightDivider = Color(0xFFE5E5EA)

    // 深色
    private val DarkWindowBg = Color(0xFF000000)
    private val DarkCardBg = Color(0xFF1C1C1E)
    private val DarkTextPrimary = Color(0xFFFFFFFF)
    private val DarkTextSecondary = Color(0xFF98989D)
    private val DarkBgGray = Color(0xFF2C2C2E)
    private val DarkDivider = Color(0xFF38383A)

    // 强调色（粉红/珊瑚 #E85D5D）
    private val LightAccent = Color(0xFFE85D5D)
    private val DarkAccent = Color(0xFFFF8A80)
    val Accent: Color @Composable get() = if (LocalIsDarkTheme.current) DarkAccent else LightAccent
    val Shadow = Color(0x08000000)

    // 动态颜色（跟随 DataStore 深色模式设置）
    val WindowBg: Color @Composable get() = if (LocalIsDarkTheme.current) DarkWindowBg else LightWindowBg
    val CardBg: Color @Composable get() = if (LocalIsDarkTheme.current) DarkCardBg else LightCardBg
    val TextPrimary: Color @Composable get() = if (LocalIsDarkTheme.current) DarkTextPrimary else LightTextPrimary
    val TextSecondary: Color @Composable get() = if (LocalIsDarkTheme.current) DarkTextSecondary else LightTextSecondary
    val BgGray: Color @Composable get() = if (LocalIsDarkTheme.current) DarkBgGray else LightBgGray
    val Divider: Color @Composable get() = if (LocalIsDarkTheme.current) DarkDivider else LightDivider
}

// ─── 字阶 ───
object AppType {
    val Display = 32.sp
    val Title = 28.sp
    val Section = 20.sp
    val Body = 16.sp
    val BodySmall = 14.sp
    val Caption = 12.sp
    val Huge = 36.sp
}

// ─── 间距 ───
object AppSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

// ─── 圆角 ───
object AppRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val full = 999.dp
    val capsule = 28.dp
}
