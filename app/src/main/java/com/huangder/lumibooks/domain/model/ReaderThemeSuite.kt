package com.huangder.lumibooks.domain.model

import org.json.JSONArray
import org.json.JSONObject

data class ReaderThemeSettings(
    val backgroundSelection: String = ReaderThemeSuites.DAY_ID,
    val textColor: Int? = null,
    val fontSize: Float = 16f,
    val fontType: String = "system",
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

data class ReaderThemeSuite(
    val id: String,
    val customName: String? = null,
    val settings: ReaderThemeSettings
) {
    val isBuiltIn: Boolean get() = id in ReaderThemeSuites.BUILT_IN_IDS
}

data class ReaderThemeSuiteState(
    val suites: List<ReaderThemeSuite>,
    val activeSuiteId: String
)

object ReaderThemeSuites {
    const val DAY_ID = "day"
    const val NIGHT_ID = "night"
    const val SEPIA_ID = "sepia"
    const val GREEN_ID = "green"

    val BUILT_IN_IDS = listOf(DAY_ID, NIGHT_ID, SEPIA_ID, GREEN_ID)

    fun defaults(): List<ReaderThemeSuite> = listOf(
        ReaderThemeSuite(DAY_ID, settings = ReaderThemeSettings(backgroundSelection = DAY_ID)),
        ReaderThemeSuite(NIGHT_ID, settings = ReaderThemeSettings(backgroundSelection = NIGHT_ID)),
        ReaderThemeSuite(
            SEPIA_ID,
            settings = ReaderThemeSettings(backgroundSelection = SEPIA_ID, fontType = "serif")
        ),
        ReaderThemeSuite(GREEN_ID, settings = ReaderThemeSettings(backgroundSelection = GREEN_ID))
    )

    fun newCustom(id: String, name: String): ReaderThemeSuite = ReaderThemeSuite(
        id = id,
        customName = name.trim(),
        settings = ReaderThemeSettings()
    )

    fun fromLegacy(settings: ReaderThemeSettings): ReaderThemeSuiteState {
        val activeId = settings.backgroundSelection.takeIf { it in BUILT_IN_IDS } ?: DAY_ID
        return ReaderThemeSuiteState(
            suites = defaults().map { suite ->
                if (suite.id == activeId) suite.copy(settings = settings) else suite
            },
            activeSuiteId = activeId
        )
    }

    fun normalized(suites: List<ReaderThemeSuite>): List<ReaderThemeSuite> {
        val seen = mutableSetOf<String>()
        val sanitized = suites.mapNotNull { suite ->
            if (suite.id.isBlank() || !seen.add(suite.id)) return@mapNotNull null
            when {
                suite.isBuiltIn -> suite.copy(customName = null, settings = suite.settings.sanitized())
                suite.customName.isNullOrBlank() -> null
                else -> suite.copy(customName = suite.customName.trim(), settings = suite.settings.sanitized())
            }
        }.toMutableList()

        defaults().forEach { defaultSuite ->
            if (sanitized.none { it.id == defaultSuite.id }) sanitized += defaultSuite
        }
        return sanitized
    }

    private fun ReaderThemeSettings.sanitized() = copy(
        backgroundSelection = backgroundSelection.takeIf(String::isNotBlank) ?: DAY_ID,
        fontSize = fontSize.coerceIn(12f, 28f),
        fontType = fontType.takeIf(String::isNotBlank) ?: "system",
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
                    if (id.isNotBlank()) {
                        add(
                            ReaderThemeSuite(
                                id = id,
                                customName = item.optString("name").takeIf(String::isNotBlank),
                                settings = settings
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun ReaderThemeSettings.toJson() = JSONObject().apply {
        put("background", backgroundSelection)
        textColor?.let { put("textColor", it) }
        put("fontSize", fontSize.toDouble())
        put("fontType", fontType)
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

    private fun JSONObject.toThemeSettings() = ReaderThemeSettings(
        backgroundSelection = optString("background", ReaderThemeSuites.DAY_ID),
        textColor = if (has("textColor") && !isNull("textColor")) optInt("textColor") else null,
        fontSize = optDouble("fontSize", 16.0).toFloat(),
        fontType = optString("fontType", "system"),
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

fun normalizeReaderThemeSuiteName(name: String): String = name.trim()

fun readerThemeSuiteNameCodePointCount(name: String): Int =
    name.codePointCount(0, name.length)
