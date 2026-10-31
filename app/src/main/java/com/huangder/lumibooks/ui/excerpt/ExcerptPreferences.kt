package com.huangder.lumibooks.ui.excerpt

import com.huangder.lumibooks.data.local.StoredExcerptPreferences

internal data class ExcerptSelection(
    val style: ExcerptStyle = ExcerptStyle.READER,
    val aspectRatio: ExcerptAspectRatio = ExcerptAspectRatio.FREE,
    val signature: String = ""
) {
    val options: ExcerptRenderOptions
        get() = ExcerptRenderOptions(aspectRatio = aspectRatio, signature = normalizeExcerptSignature(signature))

    fun normalized(): ExcerptSelection = ExcerptEdition.normalize(copy(signature = normalizeExcerptSignature(signature)))
}

internal fun initialExcerptSelection(isPremium: Boolean, stored: ExcerptSelection?): ExcerptSelection =
    if (isPremium) stored?.normalized() ?: ExcerptSelection() else ExcerptSelection()

internal fun StoredExcerptPreferences.toExcerptSelection(): ExcerptSelection = ExcerptSelection(
    style = ExcerptStyle.entries.firstOrNull { it.name == styleName } ?: ExcerptStyle.READER,
    aspectRatio = ExcerptAspectRatio.entries.firstOrNull { it.name == aspectRatioName } ?: ExcerptAspectRatio.FREE,
    signature = normalizeExcerptSignature(signature)
).normalized()

internal fun ExcerptSelection.toStoredPreferences(): StoredExcerptPreferences = StoredExcerptPreferences(
    styleName = style.name,
    aspectRatioName = aspectRatio.name,
    signature = normalizeExcerptSignature(signature)
)
