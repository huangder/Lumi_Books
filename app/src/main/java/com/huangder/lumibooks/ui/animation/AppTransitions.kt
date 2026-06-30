package com.huangder.lumibooks.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * 页面过渡动画
 */
object PageTransitions {

    /** 新页面从右侧滑入 + 淡入 */
    val enter: EnterTransition = slideInHorizontally(
        animationSpec = tween(350, easing = AppEasing.Smooth)
    ) { it / 4 } + fadeIn(
        animationSpec = tween(300)
    )

    /** 当前页面向左滑出 + 淡出 */
    val exit: ExitTransition = slideOutHorizontally(
        animationSpec = tween(300, easing = AppEasing.Accelerate)
    ) { -it / 4 } + fadeOut(
        animationSpec = tween(200)
    )

    /** 返回：左侧页面从左侧滑入 */
    val popEnter: EnterTransition = slideInHorizontally(
        animationSpec = tween(300, easing = AppEasing.Decelerate)
    ) { -it / 4 } + fadeIn(
        animationSpec = tween(250)
    )

    /** 返回：当前页面向右滑出 */
    val popExit: ExitTransition = slideOutHorizontally(
        animationSpec = tween(300, easing = AppEasing.Smooth)
    ) { it / 4 } + fadeOut(
        animationSpec = tween(200)
    )
}
