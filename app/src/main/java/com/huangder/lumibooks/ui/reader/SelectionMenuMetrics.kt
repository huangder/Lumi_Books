package com.huangder.lumibooks.ui.reader

/**
 * 选区菜单（长按文本弹出的菜单）的纵向尺寸计算。
 *
 * 菜单行原先写死 52dp：系统字体放大后，chip 里的文字行高随 fontScale 一起变大，
 * 但仍被固定行高裁切，出现「文字下半截被切掉」。这里改为按实测文字行高推导行高
 * （测量值已经包含 fontScale），保证胶囊始终能完整容纳文字。
 */

/** 菜单 chip 文字字号（与 MenuChip/MenuDivider 排版保持一致） */
internal const val SELECTION_MENU_CHIP_FONT_SIZE_SP = 12f

/** chip 自身的上下内边距（MenuChip 的 padding(vertical)） */
internal const val SELECTION_MENU_CHIP_VERTICAL_PADDING_DP = 5f

/** 胶囊行的上下内边距（菜单 Row 的 padding(vertical)） */
internal const val SELECTION_MENU_ROW_VERTICAL_PADDING_DP = 3f

/** 常规字号下的菜单行高：细一些的胶囊，接近系统选择菜单 */
internal const val SELECTION_MENU_MIN_ROW_HEIGHT_DP = 36f

/**
 * @param chipLineHeightDp 菜单 chip 内单行文字的实测高度（已按系统 fontScale 换算成 dp）
 * @return 胶囊行高：文字高度 + chip 与行的上下内边距，且不小于 [SELECTION_MENU_MIN_ROW_HEIGHT_DP]
 */
internal fun selectionMenuRowHeightDp(chipLineHeightDp: Float): Float {
    val contentHeight = chipLineHeightDp.coerceAtLeast(0f) +
        (SELECTION_MENU_CHIP_VERTICAL_PADDING_DP + SELECTION_MENU_ROW_VERTICAL_PADDING_DP) * 2f
    return contentHeight.coerceAtLeast(SELECTION_MENU_MIN_ROW_HEIGHT_DP)
}

/** 折叠横条末尾「>」圆形按钮的直径 */
internal const val SELECTION_MENU_MORE_BUTTON_DP = 28f

/** 「>」按钮与前一个 chip 的间距 */
internal const val SELECTION_MENU_MORE_GAP_DP = 4f

/**
 * 折叠横条的排布结果。
 * @property inlineCount 横条上直接显示的操作数（按用户顺序取前几个）
 * @property showsMore 是否需要末尾的「>」展开按钮
 * @property contentWidthDp 横条内容宽度（不含左右行内边距）
 */
internal data class SelectionMenuInlineLayout(
    val inlineCount: Int,
    val showsMore: Boolean,
    val contentWidthDp: Float
)

/**
 * 按屏幕可用宽度决定横条放几个操作：全部放得下就不显示「>」；否则按用户顺序
 * 尽量多放，末尾留出「>」，且至少保留一个操作。
 *
 * @param chipWidthsDp 每个操作 chip 的宽度（文字实测宽度 + chip 左右内边距）
 * @param dividerDp chip 之间分隔线的宽度
 * @param availableDp 横条内容可用的最大宽度（已扣除行内边距）
 * @param trailingDp 行尾固定附加内容（如齿轮入口）的宽度
 */
internal fun selectionMenuInlineLayout(
    chipWidthsDp: List<Float>,
    dividerDp: Float,
    availableDp: Float,
    trailingDp: Float = 0f,
    moreButtonDp: Float = SELECTION_MENU_MORE_BUTTON_DP,
    moreGapDp: Float = SELECTION_MENU_MORE_GAP_DP
): SelectionMenuInlineLayout {
    if (chipWidthsDp.isEmpty()) return SelectionMenuInlineLayout(0, false, trailingDp)
    val allWidth = chipWidthsDp.sum() + dividerDp * (chipWidthsDp.size - 1) + trailingDp
    if (allWidth <= availableDp) return SelectionMenuInlineLayout(chipWidthsDp.size, false, allWidth)
    val reserved = trailingDp + moreGapDp + moreButtonDp
    var used = 0f
    var count = 0
    for (width in chipWidthsDp) {
        val next = used + (if (count > 0) dividerDp else 0f) + width
        if (count > 0 && next + reserved > availableDp) break
        used = next
        count++
    }
    return SelectionMenuInlineLayout(count, true, used + reserved)
}

/** 折叠横条的最大宽度：胶囊不宜过长，手机上通常放 4 个左右的操作再加「>」 */
internal const val SELECTION_MENU_MAX_BAR_WIDTH_DP = 300f
