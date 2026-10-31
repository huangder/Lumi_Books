package com.huangder.lumibooks.ui.reader

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.layout.AdaptiveWindowInfo
import kotlin.math.roundToInt

internal val READER_SIDE_RAIL_EDGE_PADDING = 20.dp
internal val READER_SIDE_RAIL_GAP = 12.dp
internal val READER_SIDE_BOTTOM_ROW_MAX_WIDTH = 640.dp
internal val READER_SIDE_BOTTOM_ROW_HEIGHT = 48.dp
internal val READER_SIDE_BOTTOM_PADDING = 36.dp
internal val READER_MENU_ITEM_GAP = 10.dp
internal val READER_MENU_TOP_PADDING = 42.dp

internal data class ReaderMenuLayout(
    val sideRailItemSize: DpSize?,
    val horizontalTopActions: Boolean
)

/** Both groups must fit between the top inset and the bottom controls before using the rail. */
internal fun resolveReaderMenuLayout(
    viewport: DpSize,
    preferredRailItemSize: DpSize,
    statusBarInset: Dp
): ReaderMenuLayout {
    val window = AdaptiveWindowInfo(viewport.width.value.roundToInt(), viewport.height.value.roundToInt())
    val requiredRailHeight = statusBarInset + READER_MENU_TOP_PADDING +
        preferredRailItemSize.height * 6 + READER_MENU_ITEM_GAP * 4 +
        READER_SIDE_RAIL_GAP + READER_SIDE_BOTTOM_PADDING +
        READER_SIDE_BOTTOM_ROW_HEIGHT + READER_SIDE_RAIL_GAP
    val useSideRail = window.usesWideLayout && viewport.height >= requiredRailHeight
    return ReaderMenuLayout(
        sideRailItemSize = preferredRailItemSize.takeIf { useSideRail },
        horizontalTopActions = window.usesSideChrome && !useSideRail
    )
}
