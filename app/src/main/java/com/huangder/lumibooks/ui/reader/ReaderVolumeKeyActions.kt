package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderVolumeKeyAction

internal fun resolveReaderVolumeKeyAction(
    action: ReaderVolumeKeyAction,
    supportsFontSize: Boolean,
    supportsListening: Boolean = true
): ReaderVolumeKeyAction {
    val supported = when (action) {
        ReaderVolumeKeyAction.FONT_SIZE_DOWN,
        ReaderVolumeKeyAction.FONT_SIZE_UP -> supportsFontSize
        ReaderVolumeKeyAction.TOGGLE_TTS_SESSION,
        ReaderVolumeKeyAction.TOGGLE_TTS_PLAY_PAUSE -> supportsListening
        else -> true
    }
    return if (supported) action else ReaderVolumeKeyAction.SYSTEM_VOLUME
}

internal fun adjustReaderBrightness(current: Float, increase: Boolean): Float {
    val base = current.takeIf { it >= 0f } ?: 0.8f
    return (base + if (increase) 0.05f else -0.05f).coerceIn(0f, 1f)
}

internal fun adjustReaderFontSize(current: Float, increase: Boolean): Float =
    (current + if (increase) 1f else -1f).coerceIn(ReaderThemeSettings.FONT_SIZE_RANGE)
