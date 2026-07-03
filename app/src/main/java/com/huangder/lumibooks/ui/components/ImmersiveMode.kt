package com.huangder.lumibooks.ui.components

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 阅读页沉浸模式（隐藏状态栏 + 导航栏）
 * 使用 BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE 实现平滑过渡
 */
@Composable
fun ImmersiveMode() {
    val view = LocalView.current
    if (view.isInEditMode) return

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)

        // 隐藏系统栏，使用滑动临时显示的方式
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            // 恢复系统栏
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }
}
