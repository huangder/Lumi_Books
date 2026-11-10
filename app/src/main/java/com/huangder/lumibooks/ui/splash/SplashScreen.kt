package com.huangder.lumibooks.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.AppIconStyle
import com.huangder.lumibooks.ui.brand.LumiWordmark
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme

// 经典图标的开屏图为竖图（853x1844）：横屏时按原比例居中显示，
// 两侧空白用图片边缘主题色填充（浅色/暗色各自取图边缘均值）。
private val LightSplashBackground = Color(0xFFFCE3E2)
private val DarkSplashBackground = Color(0xFF5B5252)

// Same as @color/launch_background, the neutral cold-start window colour.
private val NeutralLight = Color(0xFFF6F7FA)
private val NeutralDark = Color(0xFF0E1014)

@Composable
fun SplashScreen(isDark: Boolean, iconStyle: String) {
    val style = AppIconStyle.fromStoredValue(iconStyle)
    val trace = com.huangder.lumibooks.util.diagnostics.startupTraceModifier("app_splash")
    if (style == AppIconStyle.CLASSIC) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(trace)
                .background(if (isDark) DarkSplashBackground else LightSplashBackground),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(if (isDark) R.drawable.splash_dark else R.drawable.splash_light),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        return
    }

    // Lumi 2 / Lumi-chan: just the wordmark on a plain base, identical to the cold-start window
    // (splash_background_dawn), so there is no jump and nothing to animate while the home screen
    // is composed underneath.
    CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(trace)
                .background(if (isDark) NeutralDark else NeutralLight),
            contentAlignment = Alignment.Center
        ) {
            LumiWordmark(modifier = Modifier.padding(bottom = 24.dp), animate = false)
        }
    }
}
