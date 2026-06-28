package com.ebook.reader.ui.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * 列表项交错入场动画
 *
 * @param index 项目索引（用于计算延迟）
 * @param delayPerItem 每项延迟（ms）
 */
@Composable
fun StaggeredItem(
    index: Int,
    delayPerItem: Int = 50,
    content: @Composable () -> Unit
) {
    val visible = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * delayPerItem.toLong())
        visible.value = true
    }

    AnimatedVisibility(
        visible = visible.value,
        enter = slideInVertically(
            animationSpec = tween(400, easing = AppEasing.Smooth)
        ) { it / 3 } + fadeIn(animationSpec = tween(350)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        content()
    }
}

/**
 * 列表项删除动画（向左滑出 + 高度收缩）
 */
@Composable
fun SwipeToDeleteItem(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            animationSpec = tween(300, easing = AppEasing.Accelerate)
        ) { -it } + fadeOut(animationSpec = tween(200))
    ) {
        content()
    }
}
