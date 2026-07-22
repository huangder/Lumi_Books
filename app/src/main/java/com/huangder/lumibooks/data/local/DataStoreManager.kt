package com.huangder.lumibooks.data.local

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundPresetCodec
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import com.huangder.lumibooks.domain.model.defaultReaderCornerContent
import com.huangder.lumibooks.util.LaunchThemeController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val deviceSupportsHdr: Boolean by lazy {
        context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.isHdr == true
    }

    companion object {
        // 阅读设置
        private val FONT_SIZE = floatPreferencesKey("font_size")
        private val LINE_HEIGHT = floatPreferencesKey("line_height")
        private val LETTER_SPACING = floatPreferencesKey("letter_spacing")
        private val FONT_TYPE = stringPreferencesKey("font_type")
        private val READER_THEME = stringPreferencesKey("reader_theme")
        private val MARGIN_HORIZ = floatPreferencesKey("margin_horiz")
        private val MARGIN_VERT = floatPreferencesKey("margin_vert")
        private val MARGIN_LEFT = floatPreferencesKey("margin_left")
        private val MARGIN_RIGHT = floatPreferencesKey("margin_right")
        private val MARGIN_TOP = floatPreferencesKey("margin_top")
        private val MARGIN_BOTTOM = floatPreferencesKey("margin_bottom")
        private val BRIGHTNESS = floatPreferencesKey("brightness")
        private val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        private val READER_BACKGROUND_SELECTION = stringPreferencesKey("reader_background_selection")
        private val CUSTOM_READER_BACKGROUNDS = stringPreferencesKey("custom_reader_backgrounds")
        private val READER_TEXT_COLOR = intPreferencesKey("reader_text_color")
        private val PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing")
        private val FIRST_LINE_INDENT = floatPreferencesKey("first_line_indent")
        private val ADVANCED_DEFAULTS_VERSION = intPreferencesKey("advanced_defaults_version")
        private val PDF_PAGE_MODE = stringPreferencesKey("pdf_page_mode")
        private val SHOW_READER_CHAPTER_PROGRESS = booleanPreferencesKey("show_reader_chapter_progress")
        private val SHOW_READER_PAGE_NUMBER = booleanPreferencesKey("show_reader_page_number")
        private val SHOW_READER_BATTERY = booleanPreferencesKey("show_reader_battery")
        private val VOLUME_KEY_PAGE_TURN = booleanPreferencesKey("volume_key_page_turn")
        private val READER_EDGE_TAP_MODE = stringPreferencesKey("reader_edge_tap_mode")
        private val READER_TOP_LEFT_CONTENT = stringPreferencesKey("reader_top_left_content")
        private val READER_TOP_RIGHT_CONTENT = stringPreferencesKey("reader_top_right_content")
        private val READER_BOTTOM_LEFT_CONTENT = stringPreferencesKey("reader_bottom_left_content")
        private val READER_BOTTOM_RIGHT_CONTENT = stringPreferencesKey("reader_bottom_right_content")
        private val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        private val TTS_PITCH = floatPreferencesKey("tts_pitch")

        // 统计设置
        private val DAILY_GOAL = intPreferencesKey("daily_goal")

        // 应用设置
        private val APP_THEME = stringPreferencesKey("app_theme")
        private val LIQUID_GLASS_TRANSPARENCY = floatPreferencesKey("liquid_glass_transparency")
        private val LIQUID_GLASS_HDR_HIGHLIGHT_ENABLED = booleanPreferencesKey("liquid_glass_hdr_highlight_enabled")
        private val DARK_MODE = stringPreferencesKey("dark_mode")
        private val ENTRANCE_ANIMATIONS_ENABLED = booleanPreferencesKey("entrance_animations_enabled")
        private val PREDICTIVE_BACK_ENABLED = booleanPreferencesKey("predictive_back_enabled")
        private val SPLASH_ENABLED = booleanPreferencesKey("splash_enabled")
        private val LAST_READ_BOOK = stringPreferencesKey("last_read_book")
        private val HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")
        private val COMPLETED_WELCOME_INSTALL_TIME = longPreferencesKey("completed_welcome_install_time")

        // MinerU 第三方云解析设置
        private val MINERU_MODE = stringPreferencesKey("mineru_mode")
        private val MINERU_CONSENT_VERSION = intPreferencesKey("mineru_consent_version")
        private val MINERU_CONSENT_ACCEPTED_AT = longPreferencesKey("mineru_consent_accepted_at")

        // 应用语言
        private val APP_LANGUAGE = stringPreferencesKey("app_language")

        // 个人信息
        private val AVATAR_URI = stringPreferencesKey("avatar_uri")
        private val NICKNAME = stringPreferencesKey("nickname")

        // 已接受的条款/政策版本（用于检查更新）
        private val ACCEPTED_TERMS_VERSION = intPreferencesKey("accepted_terms_version")
        private val ACCEPTED_PRIVACY_VERSION = intPreferencesKey("accepted_privacy_version")

        // 是否已完成首次启动的更新检查
        private val HAS_CHECKED_UPDATE_ON_START = booleanPreferencesKey("has_checked_update_on_start")
    }

    // 阅读设置
    val fontSize: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[FONT_SIZE] ?: 16f
    }

    val lineHeight: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[LINE_HEIGHT] ?: 1.5f
    }

    val letterSpacing: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[LETTER_SPACING] ?: 0f
    }

    val fontType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[FONT_TYPE] ?: "system"
    }

    val marginHoriz: Flow<Float> = context.dataStore.data.map { preferences ->
        val fallback = preferences[MARGIN_HORIZ] ?: 38f
        ((preferences[MARGIN_LEFT] ?: fallback) + (preferences[MARGIN_RIGHT] ?: fallback)) / 2f
    }

    val marginVert: Flow<Float> = context.dataStore.data.map { preferences ->
        val fallback = preferences[MARGIN_VERT] ?: 64f
        ((preferences[MARGIN_TOP] ?: fallback) + (preferences[MARGIN_BOTTOM] ?: fallback)) / 2f
    }

    val marginLeft: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[MARGIN_LEFT] ?: preferences[MARGIN_HORIZ] ?: 38f
    }

    val marginRight: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[MARGIN_RIGHT] ?: preferences[MARGIN_HORIZ] ?: 38f
    }

    val marginTop: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[MARGIN_TOP] ?: preferences[MARGIN_VERT] ?: 64f
    }

    val marginBottom: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[MARGIN_BOTTOM] ?: preferences[MARGIN_VERT] ?: 64f
    }

    val readerTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[READER_THEME] ?: "day"
    }

    /** 亮度值 0f~1f，-1f 表示跟随系统 */
    val brightness: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BRIGHTNESS] ?: -1f
    }

    /** 自定义导入字体文件路径 */
    val customFontPath: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_FONT_PATH]
    }

    val readerBackgroundSelection: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[READER_BACKGROUND_SELECTION] ?: preferences[READER_THEME] ?: "day"
    }

    val customReaderBackgrounds: Flow<List<ReaderBackgroundPreset>> =
        context.dataStore.data.map { preferences ->
            ReaderBackgroundPresetCodec.decode(preferences[CUSTOM_READER_BACKGROUNDS])
        }

    val readerTextColor: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[READER_TEXT_COLOR]
    }

    /** PDF 阅读方向："vertical" | "horizontal"，所有 PDF 共用。 */
    val pdfPageMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PDF_PAGE_MODE].takeIf { it == "horizontal" } ?: "vertical"
    }

    val showReaderChapterProgress: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_READER_CHAPTER_PROGRESS] ?: true
    }

    val showReaderPageNumber: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_READER_PAGE_NUMBER] ?: true
    }

    val showReaderBattery: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_READER_BATTERY] ?: true
    }

    val volumeKeyPageTurnEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[VOLUME_KEY_PAGE_TURN] ?: false
    }

    val readerEdgeTapMode: Flow<ReaderEdgeTapMode> = context.dataStore.data.map { preferences ->
        ReaderEdgeTapMode.fromKey(preferences[READER_EDGE_TAP_MODE])
    }

    fun readerCornerContent(corner: ReaderPageCorner): Flow<ReaderCornerContent> =
        context.dataStore.data.map { preferences ->
            val stored = preferences[readerCornerKey(corner)]
            if (stored == null) defaultReaderCornerContent(corner)
            else ReaderCornerContent.fromKey(stored)
        }

    val ttsSpeechRate: Flow<Float> = context.dataStore.data.map { preferences ->
        (preferences[TTS_SPEECH_RATE] ?: 1f).coerceIn(0.5f, 2f)
    }

    val ttsPitch: Flow<Float> = context.dataStore.data.map { preferences ->
        (preferences[TTS_PITCH] ?: 1f).coerceIn(0.5f, 2f)
    }

    // 统计设置
    val dailyGoal: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DAILY_GOAL] ?: 30
    }

    // 应用设置
    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "lumi"
    }

    val liquidGlassTransparency: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[LIQUID_GLASS_TRANSPARENCY] ?: 0.55f
    }

    val liquidGlassHdrHighlightEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LIQUID_GLASS_HDR_HIGHLIGHT_ENABLED] ?: deviceSupportsHdr
    }

    val darkMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE] ?: "system"
    }

    val entranceAnimationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENTRANCE_ANIMATIONS_ENABLED] ?: true
    }

    val predictiveBackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PREDICTIVE_BACK_ENABLED] ?: true
    }

    val splashEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SPLASH_ENABLED] ?: true
    }

    val lastReadBook: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_READ_BOOK]
    }

    val hasSeenWelcome: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_SEEN_WELCOME] ?: false
    }

    val completedWelcomeInstallTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[COMPLETED_WELCOME_INSTALL_TIME] ?: 0L
    }

    val mineruMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MINERU_MODE] ?: "disabled"
    }

    val mineruConsentVersion: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MINERU_CONSENT_VERSION] ?: 0
    }

    val mineruConsentAcceptedAt: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[MINERU_CONSENT_ACCEPTED_AT] ?: 0L
    }

    // 应用语言
    val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_LANGUAGE] ?: "system"
    }

    // 个人信息
    val avatarUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AVATAR_URI]
    }

    val nickname: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[NICKNAME] ?: "读者"
    }

    // 保存方法
    suspend fun saveFontSize(fontSize: Float) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE] = fontSize
        }
    }

    suspend fun saveLineHeight(lineHeight: Float) {
        context.dataStore.edit { preferences ->
            preferences[LINE_HEIGHT] = lineHeight
        }
    }

    suspend fun saveLetterSpacing(letterSpacing: Float) {
        context.dataStore.edit { preferences ->
            preferences[LETTER_SPACING] = letterSpacing
        }
    }

    suspend fun saveFontType(fontType: String) {
        context.dataStore.edit { preferences ->
            preferences[FONT_TYPE] = fontType
        }
    }

    suspend fun saveMarginHoriz(marginHoriz: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_HORIZ] = marginHoriz
            preferences[MARGIN_LEFT] = marginHoriz
            preferences[MARGIN_RIGHT] = marginHoriz
        }
    }

    suspend fun saveMarginVert(marginVert: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_VERT] = marginVert
            preferences[MARGIN_TOP] = marginVert
            preferences[MARGIN_BOTTOM] = marginVert
        }
    }

    suspend fun saveMarginLeft(marginLeft: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_LEFT] = marginLeft
            val right = preferences[MARGIN_RIGHT] ?: preferences[MARGIN_HORIZ] ?: 38f
            preferences[MARGIN_HORIZ] = (marginLeft + right) / 2f
        }
    }

    suspend fun saveMarginRight(marginRight: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_RIGHT] = marginRight
            val left = preferences[MARGIN_LEFT] ?: preferences[MARGIN_HORIZ] ?: 38f
            preferences[MARGIN_HORIZ] = (left + marginRight) / 2f
        }
    }

    suspend fun saveMarginTop(marginTop: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_TOP] = marginTop
            val bottom = preferences[MARGIN_BOTTOM] ?: preferences[MARGIN_VERT] ?: 64f
            preferences[MARGIN_VERT] = (marginTop + bottom) / 2f
        }
    }

    suspend fun saveMarginBottom(marginBottom: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_BOTTOM] = marginBottom
            val top = preferences[MARGIN_TOP] ?: preferences[MARGIN_VERT] ?: 64f
            preferences[MARGIN_VERT] = (top + marginBottom) / 2f
        }
    }

    suspend fun saveReaderTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[READER_THEME] = theme
        }
    }

    suspend fun saveBrightness(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[BRIGHTNESS] = value
        }
    }

    suspend fun saveCustomFontPath(path: String?) {
        context.dataStore.edit { preferences ->
            if (path != null) preferences[CUSTOM_FONT_PATH] = path
            else preferences.remove(CUSTOM_FONT_PATH)
        }
    }

    suspend fun saveReaderBackgroundSelection(selection: String) {
        context.dataStore.edit { preferences ->
            preferences[READER_BACKGROUND_SELECTION] = selection
        }
    }

    suspend fun saveCustomReaderBackgrounds(presets: List<ReaderBackgroundPreset>) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_READER_BACKGROUNDS] = ReaderBackgroundPresetCodec.encode(presets)
        }
    }

    suspend fun saveReaderTextColor(color: Int?) {
        context.dataStore.edit { preferences ->
            if (color == null) preferences.remove(READER_TEXT_COLOR)
            else preferences[READER_TEXT_COLOR] = color
        }
    }

    suspend fun savePdfPageMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PDF_PAGE_MODE] = if (mode == "horizontal") "horizontal" else "vertical"
        }
    }

    suspend fun saveShowReaderChapterProgress(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_READER_CHAPTER_PROGRESS] = show
        }
    }

    suspend fun saveShowReaderPageNumber(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_READER_PAGE_NUMBER] = show
        }
    }

    suspend fun saveShowReaderBattery(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_READER_BATTERY] = show
        }
    }

    suspend fun saveVolumeKeyPageTurnEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VOLUME_KEY_PAGE_TURN] = enabled
        }
    }

    suspend fun saveReaderEdgeTapMode(mode: ReaderEdgeTapMode) {
        context.dataStore.edit { preferences ->
            preferences[READER_EDGE_TAP_MODE] = mode.key
        }
    }

    suspend fun saveReaderCornerContent(
        corner: ReaderPageCorner,
        content: ReaderCornerContent
    ) {
        context.dataStore.edit { preferences ->
            if (content != ReaderCornerContent.NONE) {
                ReaderPageCorner.entries
                    .filter { it != corner }
                    .forEach { otherCorner ->
                        val key = readerCornerKey(otherCorner)
                        val current = preferences[key]
                            ?.let(ReaderCornerContent::fromKey)
                            ?: defaultReaderCornerContent(otherCorner)
                        if (current == content) preferences[key] = ReaderCornerContent.NONE.key
                    }
            }
            preferences[readerCornerKey(corner)] = content.key
        }
    }

    suspend fun saveTtsSpeechRate(rate: Float) {
        context.dataStore.edit { preferences ->
            preferences[TTS_SPEECH_RATE] = rate.coerceIn(0.5f, 2f)
        }
    }

    suspend fun saveTtsPitch(pitch: Float) {
        context.dataStore.edit { preferences ->
            preferences[TTS_PITCH] = pitch.coerceIn(0.5f, 2f)
        }
    }

    suspend fun resetAdvancedReaderSettings() {
        context.dataStore.edit { preferences ->
            preferences[LINE_HEIGHT] = 1.5f
            preferences[LETTER_SPACING] = 0f
            preferences[FONT_TYPE] = "system"
            preferences[MARGIN_HORIZ] = 38f
            preferences[MARGIN_VERT] = 64f
            preferences[MARGIN_LEFT] = 38f
            preferences[MARGIN_RIGHT] = 38f
            preferences[MARGIN_TOP] = 64f
            preferences[MARGIN_BOTTOM] = 64f
            preferences[PARAGRAPH_SPACING] = 2f
            preferences[FIRST_LINE_INDENT] = 2f
            preferences[SHOW_READER_CHAPTER_PROGRESS] = true
            preferences[SHOW_READER_PAGE_NUMBER] = true
            preferences[SHOW_READER_BATTERY] = true
            preferences[VOLUME_KEY_PAGE_TURN] = false
            preferences[READER_EDGE_TAP_MODE] = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT.key
            ReaderPageCorner.entries.forEach { corner ->
                preferences[readerCornerKey(corner)] = defaultReaderCornerContent(corner).key
            }
            preferences.remove(READER_TEXT_COLOR)
        }
    }

    private fun readerCornerKey(corner: ReaderPageCorner): Preferences.Key<String> = when (corner) {
        ReaderPageCorner.TOP_LEFT -> READER_TOP_LEFT_CONTENT
        ReaderPageCorner.TOP_RIGHT -> READER_TOP_RIGHT_CONTENT
        ReaderPageCorner.BOTTOM_LEFT -> READER_BOTTOM_LEFT_CONTENT
        ReaderPageCorner.BOTTOM_RIGHT -> READER_BOTTOM_RIGHT_CONTENT
    }

    suspend fun migrateAdvancedReaderDefaults() {
        context.dataStore.edit { preferences ->
            val currentVersion = preferences[ADVANCED_DEFAULTS_VERSION] ?: 0
            if (currentVersion >= 2) return@edit

            if (currentVersion < 1) {
                if (preferences[PARAGRAPH_SPACING] == 8f) {
                    preferences[PARAGRAPH_SPACING] = 2f
                }
                if (preferences[MARGIN_HORIZ] == 44f || preferences[MARGIN_HORIZ] == 40f) {
                    preferences[MARGIN_HORIZ] = 38f
                }
                if (preferences[MARGIN_VERT] == 72f || preferences[MARGIN_VERT] == 68f) {
                    preferences[MARGIN_VERT] = 64f
                }
            }

            val horizontal = preferences[MARGIN_HORIZ] ?: 38f
            val vertical = preferences[MARGIN_VERT] ?: 64f
            if (preferences[MARGIN_LEFT] == null) preferences[MARGIN_LEFT] = horizontal
            if (preferences[MARGIN_RIGHT] == null) preferences[MARGIN_RIGHT] = horizontal
            if (preferences[MARGIN_TOP] == null) preferences[MARGIN_TOP] = vertical
            if (preferences[MARGIN_BOTTOM] == null) preferences[MARGIN_BOTTOM] = vertical
            preferences[ADVANCED_DEFAULTS_VERSION] = 2
        }
    }

    suspend fun saveReaderBackgroundState(
        theme: String,
        selection: String,
        presets: List<ReaderBackgroundPreset>? = null
    ) {
        context.dataStore.edit { preferences ->
            preferences[READER_THEME] = theme
            preferences[READER_BACKGROUND_SELECTION] = selection
            if (presets != null) {
                preferences[CUSTOM_READER_BACKGROUNDS] = ReaderBackgroundPresetCodec.encode(presets)
            }
        }
    }

    /** 每本书的"优化排版"开关（per-book），默认 true */
    fun optimizeLayout(bookId: String): Flow<Boolean> {
        val key = booleanPreferencesKey("optimize_layout_$bookId")
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: true
        }
    }

    suspend fun saveOptimizeLayout(bookId: String, enabled: Boolean) {
        val key = booleanPreferencesKey("optimize_layout_$bookId")
        context.dataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }

    /** 简繁转换模式："original" | "simplified" | "traditional" */
    fun chineseMode(): Flow<String> {
        val key = stringPreferencesKey("chinese_mode")
        return context.dataStore.data.map { it[key] ?: "original" }
    }

    suspend fun saveChineseMode(mode: String) {
        val key = stringPreferencesKey("chinese_mode")
        context.dataStore.edit { it[key] = mode }
    }

    /** 翻页效果："slide" | "scroll" | "fade" */
    fun pageTransition(): Flow<String> {
        val key = stringPreferencesKey("page_transition")
        return context.dataStore.data.map { it[key] ?: "slide" }
    }

    suspend fun savePageTransition(mode: String) {
        val key = stringPreferencesKey("page_transition")
        context.dataStore.edit { it[key] = mode }
    }

    /** 段间距（dp），默认 2dp */
    fun paragraphSpacing(): Flow<Float> {
        return context.dataStore.data.map { it[PARAGRAPH_SPACING] ?: 2f }
    }

    suspend fun saveParagraphSpacing(value: Float) {
        context.dataStore.edit { it[PARAGRAPH_SPACING] = value }
    }

    /** 首行缩进字符数，默认 2 */
    fun firstLineIndent(): Flow<Float> {
        return context.dataStore.data.map { it[FIRST_LINE_INDENT] ?: 2f }
    }

    suspend fun saveFirstLineIndent(value: Float) {
        context.dataStore.edit { it[FIRST_LINE_INDENT] = value }
    }

    suspend fun saveDailyGoal(goal: Int) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_GOAL] = goal
        }
    }

    suspend fun saveAppTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = theme
        }
    }

    suspend fun enableLiquidGlassTheme(transparency: Float = 0.65f) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = "liquid_glass"
            preferences[LIQUID_GLASS_TRANSPARENCY] = transparency.coerceIn(0f, 1f)
        }
    }

    suspend fun saveLiquidGlassTransparency(transparency: Float) {
        context.dataStore.edit { preferences ->
            preferences[LIQUID_GLASS_TRANSPARENCY] = transparency.coerceIn(0f, 1f)
        }
    }

    suspend fun saveLiquidGlassHdrHighlightEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LIQUID_GLASS_HDR_HIGHLIGHT_ENABLED] = enabled
        }
    }

    suspend fun saveDarkMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE] = mode
        }
    }

    suspend fun saveEntranceAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENTRANCE_ANIMATIONS_ENABLED] = enabled
        }
    }

    suspend fun savePredictiveBackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PREDICTIVE_BACK_ENABLED] = enabled
        }
    }

    suspend fun saveSplashEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SPLASH_ENABLED] = enabled
        }
        LaunchThemeController.deferSplashEnabled(context, enabled)
    }

    suspend fun saveLastReadBook(bookId: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_READ_BOOK] = bookId
        }
    }

    suspend fun saveHasSeenWelcome(seen: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_SEEN_WELCOME] = seen
        }
    }

    suspend fun completeWelcomeFlow(installTime: Long) {
        context.dataStore.edit { preferences ->
            preferences[HAS_SEEN_WELCOME] = true
            preferences[COMPLETED_WELCOME_INSTALL_TIME] = installTime
        }
    }

    suspend fun saveMineruMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[MINERU_MODE] = when (mode) {
                "agent", "precise" -> mode
                else -> "disabled"
            }
        }
    }

    suspend fun acceptMineruConsent(version: Int, acceptedAt: Long = System.currentTimeMillis()) {
        context.dataStore.edit { preferences ->
            preferences[MINERU_CONSENT_VERSION] = version.coerceAtLeast(0)
            preferences[MINERU_CONSENT_ACCEPTED_AT] = acceptedAt.coerceAtLeast(0L)
        }
    }

    suspend fun disableMineru() {
        context.dataStore.edit { preferences ->
            preferences[MINERU_MODE] = "disabled"
            preferences[MINERU_CONSENT_VERSION] = 0
            preferences[MINERU_CONSENT_ACCEPTED_AT] = 0L
        }
    }

    suspend fun saveAppLanguage(language: String) {
        // 同步写入 SharedPreferences（供 attachBaseContext 同步读取）
        com.huangder.lumibooks.util.LocaleHelper.saveLanguage(context, language)
        context.dataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = language
        }
    }

    suspend fun saveAvatarUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[AVATAR_URI] = uri
        }
    }

    suspend fun saveNickname(name: String) {
        context.dataStore.edit { preferences ->
            preferences[NICKNAME] = name
        }
    }

    // 已接受的条款/政策版本
    val acceptedTermsVersion: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ACCEPTED_TERMS_VERSION] ?: 0
    }

    val acceptedPrivacyVersion: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ACCEPTED_PRIVACY_VERSION] ?: 0
    }

    /** 首次启动后标记已触发过启动检查（避免重复弹窗） */
    val hasCheckedUpdateOnStart: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_CHECKED_UPDATE_ON_START] ?: false
    }

    suspend fun saveAcceptedTermsVersion(version: Int) {
        context.dataStore.edit { preferences ->
            preferences[ACCEPTED_TERMS_VERSION] = version
        }
    }

    suspend fun saveAcceptedPrivacyVersion(version: Int) {
        context.dataStore.edit { preferences ->
            preferences[ACCEPTED_PRIVACY_VERSION] = version
        }
    }

    suspend fun saveHasCheckedUpdateOnStart(checked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_CHECKED_UPDATE_ON_START] = checked
        }
    }

    /** 清除所有偏好设置 */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        LaunchThemeController.deferSplashEnabled(context, true)
    }
}
