package com.huangder.lumibooks.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.AppIconStyle

// 开屏图为竖图（853x1844）：横屏时按原比例居中显示，
// 两侧空白用图片边缘主题色填充（浅色/暗色各自取图边缘均值）。
private val LightSplashBackground = Color(0xFFFCE3E2)
private val DarkSplashBackground = Color(0xFF5B5252)
private val Lumi2LightSplashBackground = Color(0xFFFFF5F5)
private val Lumi2DarkSplashBackground = Color(0xFF130F10)

@Composable
fun SplashScreen(isDark: Boolean, iconStyle: String) {
    val style = AppIconStyle.fromStoredValue(iconStyle)
    val background = when (style) {
        AppIconStyle.LUMI_2 -> if (isDark) Lumi2DarkSplashBackground else Lumi2LightSplashBackground
        AppIconStyle.CLASSIC -> if (isDark) DarkSplashBackground else LightSplashBackground
    }
    val imageResource = when (style) {
        AppIconStyle.LUMI_2 -> R.drawable.splash_lumi2
        AppIconStyle.CLASSIC -> if (isDark) R.drawable.splash_dark else R.drawable.splash_light
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(imageResource),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
