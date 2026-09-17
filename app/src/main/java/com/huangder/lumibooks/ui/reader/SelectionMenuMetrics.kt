package com.huangder.lumibooks.ui.reader

/**
 * 选区菜单（长按文本弹出的菜单）的纵向尺寸计算。
 *
 * 菜单行原先写死 52dp：系统字体放大后，chip 里的文字行高随 fontScale 一起变大，
 * 但仍被固定行高裁切，出现「文字下半截被切掉」。这里改为按实测文字行高推导行高
 * （测量值已经包含 fontScale），保证胶囊始终能完整容纳文字。
 */

/** 菜单 chip 文字字号（与 MenuChip/MenuDivider 排版保持一致） */
internal const val SELECTION_MENU_CHIP_FONT_SIZE_SP = 13f

/** chip 自身的上下内边距（MenuChip 的 padding(vertical)） */
internal const val SELECTION_MENU_CHIP_VERTICAL_PADDING_DP = 8f

/** 胶囊行的上下内边距（菜单 Row 的 padding(vertical)） */
internal const val SELECTION_MENU_ROW_VERTICAL_PADDING_DP = 8f

/** 常规字号下的菜单行高，保持既有视觉尺寸 */
internal const val SELECTION_MENU_MIN_ROW_HEIGHT_DP = 52f

/**
 * @param chipLineHeightDp 菜单 chip 内单行文字的实测高度（已按系统 fontScale 换算成 dp）
 * @return 胶囊行高：文字高度 + chip 与行的上下内边距，且不小于 [SELECTION_MENU_MIN_ROW_HEIGHT_DP]
 */
internal fun selectionMenuRowHeightDp(chipLineHeightDp: Float): Float {
    val contentHeight = chipLineHeightDp.coerceAtLeast(0f) +
        (SELECTION_MENU_CHIP_VERTICAL_PADDING_DP + SELECTION_MENU_ROW_VERTICAL_PADDING_DP) * 2f
    return contentHeight.coerceAtLeast(SELECTION_MENU_MIN_ROW_HEIGHT_DP)
}
