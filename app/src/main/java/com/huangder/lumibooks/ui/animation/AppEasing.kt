package com.huangder.lumibooks.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 统一缓动曲线
 */
object AppEasing {
    /** 快启动 + 长尾减速（翻页、滑入） */
    val Smooth = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** 柔和弹性（回弹、弹性效果） */
    val Bounce = CubicBezierEasing(0.25f, 0.9f, 0.25f, 1f)

    /** 减速进入（元素入场） */
    val Decelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** 加速退出（元素退出） */
    val Accelerate = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** 标准缓入缓出 */
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 强弹性（收藏/点赞弹跳） */
    val SpringBounce = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)
}

/** Shared timing vocabulary for app chrome. Gesture-driven code may opt into
 * the under-damped spec, while programmatic state changes stay calm. */
object LumiMotion {
    const val PressMillis = 120
    const val MenuEnterMillis = 180
    const val MenuExitMillis = 140
    const val SheetEnterMillis = 260
    const val SheetExitMillis = 200
    const val PageEnterMillis = 240

    val ProgrammaticSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 500f,
        visibilityThreshold = 0.001f
    )

    val GestureSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 380f,
        visibilityThreshold = 0.001f
    )
}
