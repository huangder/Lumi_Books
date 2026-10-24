package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderVolumeKeyAction

internal fun resolveReaderVolumeKeyAction(
    action: ReaderVolumeKeyAction,
    supportsFontSize: Boolean
): ReaderVolumeKeyAction = if (
    !supportsFontSize && action in setOf(
        ReaderVolumeKeyAction.FONT_SIZE_DOWN,
        ReaderVolumeKeyAction.FONT_SIZE_UP
    )
) {
    ReaderVolumeKeyAction.SYSTEM_VOLUME
} else {
    action
}

internal fun adjustReaderBrightness(current: Float, increase: Boolean): Float {
    val base = current.takeIf { it >= 0f } ?: 0.8f
    return (base + if (increase) 0.05f else -0.05f).coerceIn(0f, 1f)
}

internal fun adjustReaderFontSize(current: Float, increase: Boolean): Float =
    (current + if (increase) 1f else -1f).coerceIn(12f, 28f)
