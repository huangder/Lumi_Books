package com.huangder.lumibooks.ui.theme

import androidx.compose.ui.unit.dp

/** Material belongs to a layer of the interface, independently of its click handling. */
enum class AppSurfaceRole { Content, Control, Overlay }

/** App dimensions, expressed in Android dp rather than Apple platform points. */
object AppPresentation {
    val AlertMaxWidth = 320.dp
    val SheetMaxWidth = 480.dp
    val DialogCorner = 32.dp
    val SheetCorner = 28.dp
    // These are degradation strengths, never minimum blur for a working lens.
    val ControlFallbackBlur = 8.dp
    val OverlayFallbackBlur = 12.dp
    val ActionMinHeight = 48.dp
    val ActionGap = 8.dp
}
