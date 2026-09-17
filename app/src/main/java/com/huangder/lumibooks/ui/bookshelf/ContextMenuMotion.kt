package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import com.huangder.lumibooks.ui.animation.AppEasing

/**
 * 书库长按菜单（书籍封面 / 分类文件夹共用同一套浮层）的动效参数。
 *
 * 弹簧的周期是 `2π / √stiffness`：旧参数 `stiffness = 145` 一个周期约 522ms，
 * 再叠上 80ms / 190ms 的分步延迟，信息面板和操作面板要 700ms 以上才完全成形，
 * 长按后明显发粘。现在把分步延迟压到 0ms / 60ms、stiffness 提到 420f / 380f，
 * 信息面板 ~150ms、操作面板 ~200ms 就基本成形，退场同步收紧。
 *
 * 宫格、列表和分类页共用这里的参数，改一处即三处一致。
 */
internal object ContextMenuMotion {
    // ── 入场 ──────────────────────────────────────────────

    /** 信息面板（书名 / 作者）。 */
    val InfoPanelEnter: SpringSpec<Float> = spring(dampingRatio = 0.75f, stiffness = 420f)

    /** 操作面板（详情、收藏、删除…）。 */
    val ActionsPanelEnter: SpringSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 380f)

    /** 被长按封面的放大。 */
    val CoverScaleEnter: SpringSpec<Float> = spring(dampingRatio = 0.62f, stiffness = 420f)

    /** 封面放大后的位置微调。 */
    val CoverPositionEnter: SpringSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 380f)

    /** 信息面板不再等待，长按确认后立刻开始。 */
    const val InfoPanelEnterDelayMillis = 0L

    /** 操作面板只比信息面板慢一拍，保留先后关系但不再拖时间。 */
    const val ActionsPanelEnterDelayMillis = 60L

    /** 原位书本让位。 */
    const val ItemHideMillis = 80

    /** 长按时的按压回弹。 */
    const val PressReleaseMillis = 140

    /** 遮罩压暗。 */
    const val ScrimEnterMillis = 180

    /** 封面放大到的比例。 */
    const val CoverScaleValue = 1.08f

    // ── 退场 ──────────────────────────────────────────────

    /** 两个面板的退出（几乎不过冲，收得干净）。 */
    val ActionsPanelExit: SpringSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 320f)
    val InfoPanelExit: SpringSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 320f)

    const val ActionsPanelExitDelayMillis = 0L
    const val InfoPanelExitDelayMillis = 60L

    /** 封面归位与遮罩退场。 */
    const val CoverReturnMillis = 260
    const val ScrimExitMillis = 260

    val ScrimEnterEasing: Easing = AppEasing.Decelerate
    val ScrimExitEasing: Easing = AppEasing.Accelerate
    val CoverReturnEasing: Easing = AppEasing.Decelerate
}
