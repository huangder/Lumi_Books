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

private val LightSplashBackground = Color(0xFFFFF6F5)
private val DarkSplashBackground = Color(0xFF5B5252)

@Composable
fun SplashScreen(isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) DarkSplashBackground else LightSplashBackground),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(if (isDark) R.drawable.splash_dark else R.drawable.splash_light),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    }
}
