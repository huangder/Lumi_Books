package com.huangder.lumibooks.ui.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 阅读模式图标。
 * 翻页图标使用 Material Rounded（圆润版）：
 * 打开的书 / 上下箭头 / 闪电 / 合上的书。
 * 显示图标使用 Material Rounded：太阳 / 月亮；自动模式为字母 A。
 */

/** 左右翻页：一本打开的书 */
val ReaderIconPageSlide: ImageVector = Icons.Rounded.AutoStories

/** 上下滑动：上下箭头 */
val ReaderIconPageScroll: ImageVector = Icons.Rounded.SwapVert

/** 渐变：闪电 */
val ReaderIconPageFade: ImageVector = Icons.Rounded.Bolt

/** 仿真卷曲：一本合上的书 */
val ReaderIconPageCurl: ImageVector = Icons.AutoMirrored.Rounded.MenuBook

/** 日间模式：太阳 */
val ReaderIconDisplayDay: ImageVector = Icons.Rounded.WbSunny

/** 夜间模式：月亮 */
val ReaderIconDisplayNight: ImageVector = Icons.Rounded.DarkMode

/** 自动模式：字母 A（Material 无单字母 A 图标，保留自绘） */
val ReaderIconDisplayAuto: ImageVector = readerModeIcon("ReaderIconDisplayAuto") {
    moveTo(12f, 3f)
    lineTo(4f, 21f)
    lineTo(7.8f, 21f)
    lineTo(9.6f, 16.8f)
    lineTo(14.4f, 16.8f)
    lineTo(16.2f, 21f)
    lineTo(20f, 21f)
    close()
    addRect(9.6f, 12.8f, 14.4f, 14.6f)
}

private fun readerModeIcon(
    name: String,
    fillType: PathFillType = PathFillType.NonZero,
    block: PathBuilder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = fillType) {
        block()
    }
}.build()

/** PathBuilder 辅助：直角矩形 */
private fun PathBuilder.addRect(left: Float, top: Float, right: Float, bottom: Float) {
    moveTo(left, top)
    lineTo(right, top)
    lineTo(right, bottom)
    lineTo(left, bottom)
    close()
}
