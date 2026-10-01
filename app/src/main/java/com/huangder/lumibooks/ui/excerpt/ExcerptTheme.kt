package com.huangder.lumibooks.ui.excerpt

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.huangder.lumibooks.data.local.ReaderPreferencesSnapshot
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.ui.reader.darkenReaderSolidColor
import com.huangder.lumibooks.ui.reader.readerPresetBackgroundColor
import com.huangder.lumibooks.ui.reader.readerPresetTextColor

internal fun ExcerptRequest.withReaderTheme(preferences: ReaderPreferencesSnapshot, appDark: Boolean): ExcerptRequest {
    if (preferences.eInkModeEnabled) return copy(backgroundColor = Color.WHITE, textColor = 0xFF111111.toInt())
    val dark = when (preferences.readerDisplayMode) { "day" -> false; "night" -> true; else -> appDark }
    val layout = if (preferences.renderMode.name == "BOOK_LAYOUT") ReaderLayoutTarget.BOOK_LAYOUT else ReaderLayoutTarget.READER_LAYOUT
    val state = preferences.readerThemeSuiteState
    val scoped = if (layout == ReaderLayoutTarget.BOOK_LAYOUT) preferences.readerThemeSuiteBookActiveBookLayoutId
        else preferences.readerThemeSuiteBookActiveId
    val id = ReaderThemeSuites.resolveActiveId(state.suites,
        if (preferences.readerThemeSuiteBookScoped) scoped ?: state.activeSuiteIdFor(layout) else state.activeSuiteIdFor(layout), layout)
    val settings = state.suites.firstOrNull { it.id == id }?.settingsFor(layout, dark) ?: return this
    val preset = preferences.customReaderBackgrounds.firstOrNull { it.selectionKey == settings.backgroundSelection }
    val background = when {
        preset?.type == ReaderBackgroundType.COLOR -> {
            val color = runCatching { Color.parseColor(preset.value) }.getOrDefault(backgroundColor)
            if (dark) darkenReaderSolidColor(color) else color
        }
        preset != null -> preset.dominantColor ?: backgroundColor
        else -> readerPresetBackgroundColor(settings.backgroundSelection, dark)
    }
    val foreground = settings.textColor ?: if (preset != null) {
        if (ColorUtils.calculateLuminance(background) < 0.42) 0xFFE8E8EA.toInt() else 0xFF333333.toInt()
    } else readerPresetTextColor(settings.backgroundSelection, dark)
    return copy(backgroundColor = background, textColor = foreground)
}
