package com.ebook.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebook.reader.R

// ─── 字体 ───
val DingliSong = FontFamily(
    Font(R.font.dingli_song, FontWeight.Normal),
    Font(R.font.dingli_song, FontWeight.Bold)
)
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

    // 强调色（深色模式用更柔和的暖红）
    private val LightAccent = Color(0xFF6C231D)
    private val DarkAccent = Color(0xFFD4736A)
    val Accent: Color @Composable get() = if (isSystemInDarkTheme()) DarkAccent else LightAccent
    val Shadow = Color(0x08000000)

    // 动态颜色
    val WindowBg: Color @Composable get() = if (isSystemInDarkTheme()) DarkWindowBg else LightWindowBg
    val CardBg: Color @Composable get() = if (isSystemInDarkTheme()) DarkCardBg else LightCardBg
    val TextPrimary: Color @Composable get() = if (isSystemInDarkTheme()) DarkTextPrimary else LightTextPrimary
    val TextSecondary: Color @Composable get() = if (isSystemInDarkTheme()) DarkTextSecondary else LightTextSecondary
    val BgGray: Color @Composable get() = if (isSystemInDarkTheme()) DarkBgGray else LightBgGray
    val Divider: Color @Composable get() = if (isSystemInDarkTheme()) DarkDivider else LightDivider
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
