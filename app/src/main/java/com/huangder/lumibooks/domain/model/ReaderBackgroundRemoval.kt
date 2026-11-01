package com.huangder.lumibooks.domain.model

/** Include fallback settings and both modes, even when a mode is not being edited. */
internal fun ReaderThemeSuite.usesReaderBackground(selection: String): Boolean =
    listOfNotNull(settings, bookLayoutSettings, lightSettings, darkSettings).any {
        it.backgroundSelection == selection || it.backgroundColorSelection == selection
    }

/** Repair every layout/mode without changing suite identities or unrelated settings. */
internal fun ReaderThemeSuite.withoutReaderBackground(selection: String): ReaderThemeSuite {
    fun ReaderThemeSettings.repaired(): ReaderThemeSettings {
        val base = backgroundColorSelection.takeUnless { it == selection } ?: ReaderThemeSuites.DAY_ID
        return copy(
            backgroundColorSelection = base,
            backgroundSelection = backgroundSelection.takeUnless { it == selection } ?: base
        )
    }
    return copy(
        settings = settings.repaired(),
        bookLayoutSettings = bookLayoutSettings.repaired(),
        lightSettings = lightSettings?.repaired(),
        darkSettings = darkSettings?.repaired()
    )
}
