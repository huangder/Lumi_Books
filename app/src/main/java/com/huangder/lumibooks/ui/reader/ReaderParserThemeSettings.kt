package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.data.local.ReaderPreferencesSnapshot
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.util.epub.EpubRenderMode

/** Resolves the same parser-affecting theme bucket for the reader and external readers. */
internal fun resolveReaderParserThemeSettings(
    preferences: ReaderPreferencesSnapshot,
    format: String?,
    renderMode: EpubRenderMode,
    dark: Boolean
): ReaderThemeSettings? {
    val suiteState = preferences.readerThemeSuiteState
    val layout = readerLayoutTargetFor(format, renderMode)
    val requestedId = when (layout) {
        ReaderLayoutTarget.READER_LAYOUT -> if (preferences.readerThemeSuiteBookScoped) {
            preferences.readerThemeSuiteBookActiveId
                ?.takeIf { id -> suiteState.suites.any { it.id == id } }
                ?: suiteState.activeSuiteId
        } else {
            suiteState.activeSuiteId
        }
        ReaderLayoutTarget.BOOK_LAYOUT -> if (preferences.readerThemeSuiteBookScoped) {
            preferences.readerThemeSuiteBookActiveBookLayoutId
                ?.takeIf { id -> suiteState.suites.any { it.id == id } }
                ?: suiteState.activeBookLayoutSuiteId
        } else {
            suiteState.activeBookLayoutSuiteId
        }
    }
    val resolvedId = ReaderThemeSuites.resolveActiveId(
        suites = suiteState.suites,
        requestedId = requestedId,
        layout = layout
    )
    return suiteState.suites.firstOrNull { it.id == resolvedId }
        ?.settingsFor(layout, dark)
}
