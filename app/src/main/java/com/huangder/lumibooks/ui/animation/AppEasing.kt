package com.huangder.lumibooks.ui.animation

import androidx.compose.animation.core.CubicBezierEasing

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
