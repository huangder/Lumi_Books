package com.huangder.lumibooks.data.backup

import com.huangder.lumibooks.domain.model.CustomFontPresetCodec
import com.huangder.lumibooks.domain.model.ReaderBackgroundPresetCodec
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderThemeSuiteCodec
import org.json.JSONArray
import org.json.JSONException

internal const val CUSTOM_FONTS_KEY = "custom_fonts"
internal const val CUSTOM_FONT_PATH_KEY = "custom_font_path"
internal const val CUSTOM_READER_BACKGROUNDS_KEY = "custom_reader_backgrounds"
private const val READER_THEME_SUITES_KEY = "reader_theme_suites"

private val themeKeys = setOf(
    CUSTOM_FONTS_KEY, CUSTOM_FONT_PATH_KEY, CUSTOM_READER_BACKGROUNDS_KEY,
    READER_THEME_SUITES_KEY, "reader_theme_suites_version", "active_reader_theme_suite_id",
    "active_book_layout_theme_suite_id", "reader_theme", "reader_background_selection",
    "reader_background_color_selection", "reader_background_image_opacity",
    "reader_background_image_blur_dp", "reader_text_color", "font_type", "font_size",
    "body_font_weight", "body_font_weight_precise", "line_height", "letter_spacing",
    "text_alignment", "paragraph_spacing", "first_line_indent", "margin_horiz", "margin_vert",
    "margin_left", "margin_right", "margin_top", "margin_bottom"
)

internal fun isReaderThemePreference(key: String): Boolean =
    key in themeKeys || key.startsWith("reader_corner_margin_") || key.startsWith("reader_theme_suite_book_")

internal fun PortablePreference.withoutProcessedBackgrounds(): PortablePreference {
    if (key != CUSTOM_READER_BACKGROUNDS_KEY || type != PortablePreference.TYPE_STRING) return this
    val items = try {
        JSONArray(value)
    } catch (_: JSONException) {
        return this
    }
    var changed = false
    for (i in 0 until items.length()) {
        val item = items.optJSONObject(i) ?: continue
        if (item.remove("processedValue") != null) changed = true
        if (item.remove("processedBlurDp") != null) changed = true
    }
    return if (changed) copy(value = items.toString()) else this
}

internal data class ReaderThemeAssetPath(val path: String, val kind: String, val presetId: String? = null)

internal fun PortablePreference.readerThemeAssetPaths(): List<ReaderThemeAssetPath> {
    if (type != PortablePreference.TYPE_STRING) return emptyList()
    return when (key) {
        CUSTOM_FONT_PATH_KEY -> listOf(ReaderThemeAssetPath(value, "font"))
        CUSTOM_FONTS_KEY -> CustomFontPresetCodec.decode(value).map {
            ReaderThemeAssetPath(it.path, "font", it.id)
        }
        CUSTOM_READER_BACKGROUNDS_KEY -> ReaderBackgroundPresetCodec.decode(value)
            .filter { it.type == ReaderBackgroundType.IMAGE }
            .map { ReaderThemeAssetPath(it.value, "reader_background", it.id) }
        else -> emptyList()
    }.filter { (path, _) -> path.isNotBlank() }
}

internal fun List<PortablePreference>.hasMissingReaderThemePresets(): Boolean {
    val active = filter { it.type == PortablePreference.TYPE_STRING }.associate { it.key to it.value }
    val fonts = CustomFontPresetCodec.decode(active[CUSTOM_FONTS_KEY]).mapTo(mutableSetOf()) { it.fontTypeKey }
    if (!active[CUSTOM_FONT_PATH_KEY].isNullOrBlank()) fonts += "custom"
    val backgrounds = ReaderBackgroundPresetCodec.decode(active[CUSTOM_READER_BACKGROUNDS_KEY])
        .mapTo(mutableSetOf()) { it.selectionKey }
    fun missingFont(value: String?) =
        value != null && (value == "custom" || value.startsWith("custom:")) && value !in fonts
    fun missingBackground(value: String?) = value?.startsWith("custom:") == true && value !in backgrounds
    if (missingFont(active["font_type"]) || missingBackground(active["reader_background_selection"]) ||
        missingBackground(active["reader_background_color_selection"])) return true

    return ReaderThemeSuiteCodec.decode(active[READER_THEME_SUITES_KEY]).any { suite ->
        listOfNotNull(
            suite.settings, suite.bookLayoutSettings, suite.lightSettings, suite.darkSettings,
            suite.bookLayoutLightSettings, suite.bookLayoutDarkSettings
        ).any { settings ->
            missingFont(settings.fontType) || missingBackground(settings.backgroundSelection) ||
                missingBackground(settings.backgroundColorSelection)
        }
    }
}

internal fun PortableAsset.hasSafeFileName(): Boolean =
    fileName.isNotBlank() && fileName != "." && fileName != ".." && '/' !in fileName && '\\' !in fileName
