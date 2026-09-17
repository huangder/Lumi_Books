package com.huangder.lumibooks.domain.model

import org.json.JSONArray
import org.json.JSONObject

data class ReaderThemeSettings(
    val backgroundSelection: String = ReaderThemeSuites.DAY_ID,
    val backgroundColorSelection: String = ReaderThemeSuites.DAY_ID,
    val backgroundImageOpacity: Float = 1f,
    val backgroundImageBlurDp: Float = 0f,
    val textColor: Int? = null,
    val fontSize: Float = 16f,
    val fontType: String = "system",
    val bodyFontWeight: Int = 400,
    val lineHeight: Float = 1.5f,
    val letterSpacing: Float = 0f,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
    val paragraphSpacing: Float = 2f,
    val firstLineIndent: Float = 2f,
    val marginLeft: Float = 38f,
    val marginRight: Float = 38f,
    val marginTop: Float = 64f,
    val marginBottom: Float = 64f
)

/** Which reader layout a set of typography/background settings belongs to. */
enum class ReaderLayoutTarget {
    /** Reader layout (TXT and EPUB/MOBI reflow). */
    READER_LAYOUT,

    /** Publisher layout (EPUB/MOBI book layout). */
    BOOK_LAYOUT
}

data class ReaderThemeSuite(
    val id: String,
    val customName: String? = null,
    val settings: ReaderThemeSettings,
    val bookLayoutSettings: ReaderThemeSettings = settings
) {
    val isBuiltIn: Boolean get() = id in ReaderThemeSuites.BUILT_IN_IDS

    /**
     * 只在书籍原排版可用的内置套装（「原排版」）。阅读器排版会重排文字，
     * 谈不上"原书配色"，因此它不出现在阅读器排版的套装列表里。
     */
    val isBookLayoutOnly: Boolean get() = id == ReaderThemeSuites.PUBLISHER_ID

    fun settingsFor(layout: ReaderLayoutTarget): ReaderThemeSettings = when (layout) {
        ReaderLayoutTarget.READER_LAYOUT -> settings
        ReaderLayoutTarget.BOOK_LAYOUT -> bookLayoutSettings
    }

    fun withSettings(layout: ReaderLayoutTarget, updated: ReaderThemeSettings): ReaderThemeSuite =
        when (layout) {
            ReaderLayoutTarget.READER_LAYOUT -> copy(settings = updated)
            ReaderLayoutTarget.BOOK_LAYOUT -> copy(bookLayoutSettings = updated)
        }
}

data class ReaderThemeSuiteState(
    val suites: List<ReaderThemeSuite>,
    val activeSuiteId: String,
    /** 书籍原排版单独记一套活动套装，默认「原排版」。 */
    val activeBookLayoutSuiteId: String = ReaderThemeSuites.PUBLISHER_ID
) {
    fun activeSuiteIdFor(layout: ReaderLayoutTarget): String = when (layout) {
        ReaderLayoutTarget.READER_LAYOUT -> activeSuiteId
        ReaderLayoutTarget.BOOK_LAYOUT -> activeBookLayoutSuiteId
    }
}

object ReaderThemeSuites {
    const val DAY_ID = "day"
    const val NIGHT_ID = "night"
    const val SEPIA_ID = "sepia"
    const val GREEN_ID = "green"
    const val PUBLISHER_ID = "publisher"

    /** 真正参与"日间/夜间/羊皮纸/护眼绿"配色的主题 id。 */
    val THEME_IDS = listOf(DAY_ID, NIGHT_ID, SEPIA_ID, GREEN_ID)

    /** 内置套装的默认顺序：「原排版」排第一，用户一眼就能看到书籍原排版的默认配色。 */
    val BUILT_IN_IDS = listOf(PUBLISHER_ID) + THEME_IDS

    fun supportsLayout(suite: ReaderThemeSuite, layout: ReaderLayoutTarget): Boolean =
        layout != ReaderLayoutTarget.READER_LAYOUT || !suite.isBookLayoutOnly

    /** 纠正某个排版模式下的活动套装 id：套装不存在或不支持该模式时回落。 */
    fun resolveActiveId(
        suites: List<ReaderThemeSuite>,
        requestedId: String?,
        layout: ReaderLayoutTarget
    ): String {
        val fallback = when (layout) {
            ReaderLayoutTarget.READER_LAYOUT -> DAY_ID
            ReaderLayoutTarget.BOOK_LAYOUT -> PUBLISHER_ID
        }
        val requested = requestedId?.let { id -> suites.firstOrNull { it.id == id } }
        if (requested != null && supportsLayout(requested, layout)) return requested.id
        return suites.firstOrNull { it.id == fallback && supportsLayout(it, layout) }?.id
            ?: suites.firstOrNull { supportsLayout(it, layout) }?.id
            ?: fallback
    }

    fun defaults(): List<ReaderThemeSuite> = listOf(
        // 「原排版」：背景跟随书籍自身、文字颜色不覆盖（textColor = null），
        // 仅用于书籍原排版，且背景与文字颜色在界面上不可更改。默认排在最前面。
        ReaderThemeSuite(
            PUBLISHER_ID,
            settings = ReaderThemeSettings(),
            bookLayoutSettings = ReaderThemeSettings(
                backgroundSelection = PUBLISHER_ID,
                backgroundColorSelection = PUBLISHER_ID
            )
        ),
        ReaderThemeSuite(
            DAY_ID,
            settings = ReaderThemeSettings(
                backgroundSelection = DAY_ID,
                backgroundColorSelection = DAY_ID
            )
        ),
        ReaderThemeSuite(
            NIGHT_ID,
            settings = ReaderThemeSettings(
                backgroundSelection = NIGHT_ID,
                backgroundColorSelection = NIGHT_ID
            )
        ),
        ReaderThemeSuite(
            SEPIA_ID,
            settings = ReaderThemeSettings(
                backgroundSelection = SEPIA_ID,
                backgroundColorSelection = SEPIA_ID,
                fontType = "serif"
            )
        ),
        ReaderThemeSuite(
            GREEN_ID,
            settings = ReaderThemeSettings(
                backgroundSelection = GREEN_ID,
                backgroundColorSelection = GREEN_ID
            )
        ),
    )

    /** 把「原排版」挪到列表最前面（只用于升级时的一次性排序迁移）。 */
    fun withPublisherFirst(suites: List<ReaderThemeSuite>): List<ReaderThemeSuite> {
        val publisher = suites.firstOrNull { it.id == PUBLISHER_ID } ?: return suites
        if (suites.first().id == PUBLISHER_ID) return suites
        return listOf(publisher) + suites.filterNot { it.id == PUBLISHER_ID }
    }

    fun newCustom(id: String, name: String): ReaderThemeSuite = ReaderThemeSuite(
        id = id,
        customName = name.trim(),
        settings = ReaderThemeSettings()
    )

    fun fromLegacy(settings: ReaderThemeSettings): ReaderThemeSuiteState {
        val activeId = settings.backgroundSelection.takeIf { it in BUILT_IN_IDS } ?: DAY_ID
        return ReaderThemeSuiteState(
            suites = defaults().map { suite ->
                if (suite.id == activeId) {
                    suite.copy(settings = settings, bookLayoutSettings = settings)
                } else {
                    suite
                }
            },
            activeSuiteId = activeId
        )
    }

    fun normalized(suites: List<ReaderThemeSuite>): List<ReaderThemeSuite> {
        val seen = mutableSetOf<String>()
        val sanitized = suites.mapNotNull { suite ->
            if (suite.id.isBlank() || !seen.add(suite.id)) return@mapNotNull null
            when {
                suite.isBuiltIn -> suite.copy(
                    customName = null,
                    settings = suite.settings.sanitized(),
                    bookLayoutSettings = suite.bookLayoutSettings.sanitized()
                )
                suite.customName.isNullOrBlank() -> null
                else -> suite.copy(
                    customName = suite.customName.trim(),
                    settings = suite.settings.sanitized(),
                    bookLayoutSettings = suite.bookLayoutSettings.sanitized()
                )
            }
        }.toMutableList()

        defaults().forEach { defaultSuite ->
            if (sanitized.none { it.id == defaultSuite.id }) sanitized += defaultSuite
        }
        return sanitized
    }

    private fun ReaderThemeSettings.sanitized() = copy(
        backgroundSelection = backgroundSelection.takeIf(String::isNotBlank) ?: DAY_ID,
        backgroundColorSelection = backgroundColorSelection.takeIf(String::isNotBlank) ?: DAY_ID,
        backgroundImageOpacity = backgroundImageOpacity.coerceIn(0f, 1f),
        backgroundImageBlurDp = backgroundImageBlurDp.coerceIn(0f, 40f),
        fontSize = fontSize.coerceIn(12f, 28f),
        fontType = fontType.takeIf(String::isNotBlank) ?: "system",
        bodyFontWeight = bodyFontWeight.coerceIn(100, 900),
        lineHeight = lineHeight.coerceIn(1f, 2.5f),
        letterSpacing = letterSpacing.coerceIn(0f, 10f),
        paragraphSpacing = paragraphSpacing.coerceIn(0f, 30f),
        firstLineIndent = firstLineIndent.coerceIn(0f, 4f),
        marginLeft = marginLeft.coerceIn(0f, 80f),
        marginRight = marginRight.coerceIn(0f, 80f),
        marginTop = marginTop.coerceIn(0f, 120f),
        marginBottom = marginBottom.coerceIn(0f, 120f)
    )
}

object ReaderThemeSuiteCodec {
    fun encode(suites: List<ReaderThemeSuite>): String {
        val array = JSONArray()
        ReaderThemeSuites.normalized(suites).forEach { suite ->
            array.put(JSONObject().apply {
                put("id", suite.id)
                suite.customName?.let { put("name", it) }
                put("settings", suite.settings.toJson())
                put("bookLayoutSettings", suite.bookLayoutSettings.toJson())
            })
        }
        return array.toString()
    }

    fun decode(raw: String?): List<ReaderThemeSuite> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val settings = item.optJSONObject("settings")?.toThemeSettings() ?: continue
                    // Older payloads only carry one set; the reader layout values
                    // start as the baseline for both layouts so nothing shifts on upgrade.
                    val bookLayoutSettings = item.optJSONObject("bookLayoutSettings")
                        ?.toThemeSettings()
                        ?: settings
                    if (id.isNotBlank()) {
                        add(
                            ReaderThemeSuite(
                                id = id,
                                customName = item.optString("name").takeIf(String::isNotBlank),
                                settings = settings,
                                bookLayoutSettings = bookLayoutSettings
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun ReaderThemeSettings.toJson() = JSONObject().apply {
        put("background", backgroundSelection)
        put("backgroundColor", backgroundColorSelection)
        put("backgroundImageOpacity", backgroundImageOpacity.toDouble())
        put("backgroundImageBlurDp", backgroundImageBlurDp.toDouble())
        textColor?.let { put("textColor", it) }
        put("fontSize", fontSize.toDouble())
        put("fontType", fontType)
        put("bodyFontWeight", bodyFontWeight)
        put("lineHeight", lineHeight.toDouble())
        put("letterSpacing", letterSpacing.toDouble())
        put("textAlignment", textAlignment.key)
        put("paragraphSpacing", paragraphSpacing.toDouble())
        put("firstLineIndent", firstLineIndent.toDouble())
        put("marginLeft", marginLeft.toDouble())
        put("marginRight", marginRight.toDouble())
        put("marginTop", marginTop.toDouble())
        put("marginBottom", marginBottom.toDouble())
    }

    private fun JSONObject.toThemeSettings(): ReaderThemeSettings {
        val background = optString("background", ReaderThemeSuites.DAY_ID)
        return ReaderThemeSettings(
        backgroundSelection = background,
        backgroundColorSelection = optString(
            "backgroundColor",
            background.takeUnless { it.startsWith("custom:") } ?: ReaderThemeSuites.DAY_ID
        ),
        backgroundImageOpacity = optDouble("backgroundImageOpacity", 1.0).toFloat(),
        backgroundImageBlurDp = optDouble("backgroundImageBlurDp", 0.0).toFloat(),
        textColor = if (has("textColor") && !isNull("textColor")) optInt("textColor") else null,
        fontSize = optDouble("fontSize", 16.0).toFloat(),
        fontType = optString("fontType", "system"),
        bodyFontWeight = optInt("bodyFontWeight", 400),
        lineHeight = optDouble("lineHeight", 1.5).toFloat(),
        letterSpacing = optDouble("letterSpacing", 0.0).toFloat(),
        textAlignment = ReaderTextAlignment.fromKey(optString("textAlignment")),
        paragraphSpacing = optDouble("paragraphSpacing", 2.0).toFloat(),
        firstLineIndent = optDouble("firstLineIndent", 2.0).toFloat(),
        marginLeft = optDouble("marginLeft", 38.0).toFloat(),
        marginRight = optDouble("marginRight", 38.0).toFloat(),
        marginTop = optDouble("marginTop", 64.0).toFloat(),
        marginBottom = optDouble("marginBottom", 64.0).toFloat()
        )
    }
}

fun normalizeReaderThemeSuiteName(name: String): String = name.trim()

fun readerThemeSuiteNameCodePointCount(name: String): Int =
    name.codePointCount(0, name.length)
