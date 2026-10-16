package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderThemeSuites

internal fun publisherFallbackPaperColor(nightDisplay: Boolean): Int =
    if (nightDisplay) 0xFF111111.toInt() else 0xFFFBFBFC.toInt()

internal fun readerTransitionPaperColor(color: Int): Int {
    val red = color shr 16 and 0xFF
    val green = color shr 8 and 0xFF
    val blue = color and 0xFF
    return if (red >= 0xF2 && green >= 0xF2 && blue >= 0xF2) 0xFFF0F0F0.toInt() else color
}

/** Resolve the paper without constructing a renderer or waiting for document content. */
internal fun ReaderUiState.resolveReaderPaperColor(appIsDark: Boolean): Int {
    val eInkMode = eInkModeEnabled
    val nightDisplay = !eInkMode && when (readerDisplayMode) {
        "day" -> false
        "night" -> true
        else -> appIsDark
    }
    val uiState = withActiveThemeSettingsForMode(nightDisplay)
    val effectiveReaderBackgroundSelection = if (eInkMode) "day" else uiState.readerBackgroundSelection
    val selectedCustomBackground = if (eInkMode) null else customReaderBackgrounds.firstOrNull {
        it.selectionKey == effectiveReaderBackgroundSelection
    }
    val publisherPaintSuiteActive = uiState.keepsPublisherPaint() && !eInkMode
    val publisherFallbackPaperColor = publisherFallbackPaperColor(nightDisplay)
    val selectedImageBaseBackground = uiState.customReaderBackgrounds.firstOrNull {
        it.selectionKey == uiState.readerBackgroundColorSelection &&
            it.type == ReaderBackgroundType.COLOR
    }
    val imageBaseColor = when {
        selectedImageBaseBackground != null -> runCatching {
            android.graphics.Color.parseColor(selectedImageBaseBackground.value)
        }.getOrDefault(0xFFFBFBFC.toInt())
        uiState.readerBackgroundColorSelection == "night" -> 0xFF1a1a1a.toInt()
        uiState.readerBackgroundColorSelection == "sepia" -> 0xFFf5e6d3.toInt()
        uiState.readerBackgroundColorSelection == "green" -> 0xFFe8f5e9.toInt()
        else -> 0xFFFBFBFC.toInt()
    }
    return when {
        publisherPaintSuiteActive -> publisherFallbackPaperColor
        // The image itself is decoded by the reader later. During the shared
        // transition use its persisted dominant color immediately, then fall
        // back to the configured paper color only for older presets without it.
        selectedCustomBackground?.type == ReaderBackgroundType.IMAGE ->
            selectedCustomBackground.dominantColor ?: imageBaseColor
        selectedCustomBackground?.type == ReaderBackgroundType.COLOR -> {
            val base = runCatching { android.graphics.Color.parseColor(selectedCustomBackground.value) }
                .getOrDefault(0xFFFBFBFC.toInt())
            if (nightDisplay) darkenReaderSolidColor(base) else base
        }
        effectiveReaderBackgroundSelection in ReaderThemeSuites.THEME_IDS ->
            readerPresetBackgroundColor(effectiveReaderBackgroundSelection, nightDisplay)
        else -> 0xFFFBFBFC.toInt()
    }
}
