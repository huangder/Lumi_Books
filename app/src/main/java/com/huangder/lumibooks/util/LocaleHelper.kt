package com.huangder.lumibooks.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 语言切换辅助工具类
 *
 * 通过 createConfigurationContext 覆盖应用的 Locale，
 * 配合 Activity.attachBaseContext 实现应用内语言切换。
 *
 * 语言偏好使用 SharedPreferences（同步读取，适合 attachBaseContext）。
 */
object LocaleHelper {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "app_language"

    /** 支持的语言键值列表 */
    val SUPPORTED_LANGUAGES = listOf("system", "zh-CN", "zh-TW", "zh-HK", "zh-MO", "ko", "ja", "en")

    /** 语言键值 → 显示名称（目标语言） */
    val LANGUAGE_DISPLAY_NAMES = mapOf(
        "system" to "跟隨系統設定",
        "zh-CN" to "简体中文（中国大陆）",
        "zh-TW" to "繁體中文（中國台灣）",
        "zh-HK" to "繁體中文（中國香港）",
        "zh-MO" to "繁體中文（中國澳門）",
        "ko" to "한국어",
        "ja" to "日本語",
        "en" to "English"
    )

    /** 从 SharedPreferences 同步读取语言设置 */
    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "system") ?: "system"
    }

    /** 保存语言设置到 SharedPreferences */
    fun saveLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language).apply()
    }

    /**
     * 将语言键值转换为 Locale
     */
    fun getLocaleForLanguage(language: String): Locale {
        return when (language) {
            "zh-CN" -> Locale.SIMPLIFIED_CHINESE
            "zh-TW" -> Locale.TRADITIONAL_CHINESE
            "zh-HK" -> Locale("zh", "HK")
            "zh-MO" -> Locale("zh", "MO")
            "ko" -> Locale.KOREAN
            "ja" -> Locale.JAPANESE
            "en" -> Locale.ENGLISH
            else -> Locale.getDefault()
        }
    }

    /**
     * 用指定语言包装 Context，使资源系统使用对应 Locale
     */
    fun wrapContext(context: Context, language: String): Context {
        if (language == "system") return context
        val locale = getLocaleForLanguage(language)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * 读取语言设置并包装 Context（一步到位，供 attachBaseContext 使用）
     */
    fun applyLanguage(context: Context): Context {
        val language = getLanguage(context)
        return wrapContext(context, language)
    }
}
