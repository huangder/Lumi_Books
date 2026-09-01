package com.huangder.lumibooks.data.local

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.CustomFontPresetCodec
import com.huangder.lumibooks.domain.model.HighlightPalette
import com.huangder.lumibooks.domain.model.HighlightPaletteCodec
import com.huangder.lumibooks.domain.model.AppIconStyle
import com.huangder.lumibooks.domain.model.WebdavConfig
import com.huangder.lumibooks.domain.model.WebdavSyncContent
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundPresetCodec
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.normalizeAppAccentHex
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuiteCodec
import com.huangder.lumibooks.domain.model.ReaderThemeSuiteState
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.domain.model.PdfPageMode
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.domain.model.ReaderPageTransition
import com.huangder.lumibooks.domain.model.ReaderFirstOpenHintPolicy
import com.huangder.lumibooks.domain.model.defaultReaderCornerContent
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.LaunchThemeSnapshot
import com.huangder.lumibooks.util.epub.EpubRenderMode
import com.huangder.lumibooks.util.parser.TxtTocRule
import com.huangder.lumibooks.util.parser.TxtTocRuleBuiltIns
import com.huangder.lumibooks.util.parser.TxtTocRuleCodec
import com.huangder.lumibooks.tts.ExternalTtsProtocol
import com.huangder.lumibooks.tts.ExternalTtsResumePosition
import com.huangder.lumibooks.tts.ExternalTtsSettings
import com.huangder.lumibooks.tts.ExternalTtsConfig
import com.huangder.lumibooks.tts.FloatingSubtitleSettings
import com.huangder.lumibooks.tts.TtsSettingsStore
import com.huangder.lumibooks.tts.TtsProviderSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import com.huangder.lumibooks.data.backup.PortablePreference
import java.security.MessageDigest
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class ReaderPreferencesSnapshot(
    val fontSize: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    val textAlignment: ReaderTextAlignment,
    val fontType: String,
    val marginLeft: Float,
    val marginRight: Float,
    val marginTop: Float,
    val marginBottom: Float,
    val readerTheme: String,
    val brightness: Float,
    val customFontPath: String?,
    val customFonts: List<CustomFontPreset>,
    val readerBackgroundSelection: String,
    val readerBackgroundColorSelection: String,
    val readerBackgroundImageOpacity: Float,
    val readerBackgroundImageBlurDp: Float,
    val customReaderBackgrounds: List<ReaderBackgroundPreset>,
    val preserveEpubBackground: Boolean,
    val readerTextColor: Int?,
    val pageAnimationSettings: ReaderPageAnimationSettings,
    val pageTransition: String,
    val readerThemeSuiteState: ReaderThemeSuiteState,
    val readerThemeSuiteBookScoped: Boolean,
    val readerThemeSuiteBookActiveId: String?,
    val pdfPageMode: String,
    val showReaderChapterProgress: Boolean,
    val showReaderPageNumber: Boolean,
    val showReaderBattery: Boolean,
    val volumeKeyPageTurnEnabled: Boolean,
    val bionicReadingEnabled: Boolean,
    val comicModeEnabled: Boolean,
    val bodyFontWeight: Int,
    val eInkModeEnabled: Boolean,
    val twoPageSpreadEnabled: Boolean,
    val screenSleepTimeoutSeconds: Int,
    val readerEdgeTapMode: ReaderEdgeTapMode,
    val readerTopLeftContent: ReaderCornerContent,
    val readerTopRightContent: ReaderCornerContent,
    val readerBottomLeftContent: ReaderCornerContent,
    val readerBottomRightContent: ReaderCornerContent,
    val readerDisplayMode: String,
    val paragraphSpacing: Float,
    val firstLineIndent: Float,
    val chineseMode: String,
    val selectionMenuItems: Map<String, Boolean>,
    val customHighlightPalettes: List<HighlightPalette>,
    val activeHighlightPaletteId: String?,
    val renderMode: EpubRenderMode,
    val txtEncoding: String,
    val txtTocRuleId: String,
    val txtEncodingHintShown: Boolean,
    val epubLayoutHintShown: Boolean,
    val mobiLayoutHintShown: Boolean,
    val readerFirstOpenHintAcknowledgementCount: Int,
    val readerFirstOpenHintsDisabled: Boolean,
    val optimizeLayout: Boolean,
    val useEpubCss: Boolean,
    val readerWritingMode: ReaderWritingMode
)

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsSettingsStore {
    private val readerMigrationMutex = Mutex()
    @Volatile private var readerMigrationsComplete = false
    private val deviceSupportsHdr: Boolean by lazy {
        context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.isHdr == true
    }

    companion object {
        const val SCREEN_SLEEP_TIMEOUT_FOLLOW_SYSTEM = 0
        val SCREEN_SLEEP_TIMEOUT_SECONDS_OPTIONS = listOf(SCREEN_SLEEP_TIMEOUT_FOLLOW_SYSTEM, 30, 60, 300, 600, 1200, 1800, 2400, 3000, 3600, 5400, 7200)
        const val DEFAULT_SCREEN_SLEEP_TIMEOUT_SECONDS = SCREEN_SLEEP_TIMEOUT_FOLLOW_SYSTEM

        const val STARTUP_SCREEN_HOME = "home"
        const val STARTUP_SCREEN_BOOKSHELF = "bookshelf"
        const val STARTUP_SCREEN_STATISTICS = "statistics"
        const val DEFAULT_STARTUP_SCREEN = STARTUP_SCREEN_HOME

        fun normalizeStartupScreen(value: String?): String = when (value) {
            STARTUP_SCREEN_BOOKSHELF -> STARTUP_SCREEN_BOOKSHELF
            STARTUP_SCREEN_STATISTICS -> STARTUP_SCREEN_STATISTICS
            else -> STARTUP_SCREEN_HOME
        }

        // 阅读设置
        private val FONT_SIZE = floatPreferencesKey("font_size")
        private val LINE_HEIGHT = floatPreferencesKey("line_height")
        private val LETTER_SPACING = floatPreferencesKey("letter_spacing")
        private val TEXT_ALIGNMENT = stringPreferencesKey("text_alignment")
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
        private val CUSTOM_FONTS = stringPreferencesKey("custom_fonts")
        private val REMOTE_FONT_VERSIONS = stringPreferencesKey("remote_font_versions")
        private val READER_BACKGROUND_SELECTION = stringPreferencesKey("reader_background_selection")
        private val READER_BACKGROUND_COLOR_SELECTION = stringPreferencesKey("reader_background_color_selection")
        private val READER_BACKGROUND_IMAGE_OPACITY = floatPreferencesKey("reader_background_image_opacity")
        private val READER_BACKGROUND_IMAGE_BLUR_DP = floatPreferencesKey("reader_background_image_blur_dp")
        private val CUSTOM_READER_BACKGROUNDS = stringPreferencesKey("custom_reader_backgrounds")
        private val PRESERVE_EPUB_BACKGROUND = booleanPreferencesKey("preserve_epub_background")
        private val READER_TEXT_COLOR = intPreferencesKey("reader_text_color")
        private val READER_THEME_SUITES = stringPreferencesKey("reader_theme_suites")
        private val ACTIVE_READER_THEME_SUITE_ID = stringPreferencesKey("active_reader_theme_suite_id")
        private val TXT_TOC_CUSTOM_RULES = stringPreferencesKey("txt_toc_custom_rules_v1")
        private val READER_THEME_SUITES_VERSION = intPreferencesKey("reader_theme_suites_version")
        private val PAGE_TRANSITION = stringPreferencesKey("page_transition")
        private val PAGE_TRANSITION_SLIDE_DURATION_MS = intPreferencesKey("page_transition_slide_duration_ms")
        private val PAGE_TRANSITION_SCROLL_DURATION_MS = intPreferencesKey("page_transition_scroll_duration_ms")
        private val PAGE_TRANSITION_FADE_DURATION_MS = intPreferencesKey("page_transition_fade_duration_ms")
        private val PAGE_TRANSITION_CURL_DURATION_MS = intPreferencesKey("page_transition_curl_duration_ms")
        private val CUSTOM_HIGHLIGHT_COLORS = stringPreferencesKey("custom_highlight_colors")
        private val CUSTOM_HIGHLIGHT_PALETTES = stringPreferencesKey("custom_highlight_palettes")
        private val ACTIVE_HIGHLIGHT_PALETTE_ID = stringPreferencesKey("active_highlight_palette_id")
        private val SELECTION_MENU_ITEMS = stringPreferencesKey("selection_menu_items")
        private val TTS_FLOATING_WINDOW = booleanPreferencesKey("tts_floating_window")
        private val TTS_FLOATING_X_FRACTION = floatPreferencesKey("tts_floating_x_fraction")
        private val TTS_FLOATING_Y_FRACTION = floatPreferencesKey("tts_floating_y_fraction")
        private val TTS_FLOATING_BACKGROUND_COLOR = stringPreferencesKey("tts_floating_background_color")
        private val TTS_FLOATING_BACKGROUND_OPACITY = floatPreferencesKey("tts_floating_background_opacity")
        private val TTS_FLOATING_CORNER_RADIUS_DP = floatPreferencesKey("tts_floating_corner_radius_dp")
        private val TTS_FLOATING_WIDTH_DP = floatPreferencesKey("tts_floating_width_dp")
        private val TTS_FLOATING_HEIGHT_DP = floatPreferencesKey("tts_floating_height_dp")
        private val PREFERRED_TTS_ENGINE = stringPreferencesKey("preferred_tts_engine")
        private val TTS_PROVIDER_SELECTION = stringPreferencesKey("tts_provider_selection")
        private val BODY_FONT_WEIGHT = intPreferencesKey("body_font_weight")
        private val APPLY_TO_BODY_ONLY = booleanPreferencesKey("apply_to_body_only")
        private val PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing")
        private val FIRST_LINE_INDENT = floatPreferencesKey("first_line_indent")
        private val ADVANCED_DEFAULTS_VERSION = intPreferencesKey("advanced_defaults_version")
        private val PDF_PAGE_MODE = stringPreferencesKey("pdf_page_mode")
        private val SHOW_READER_CHAPTER_PROGRESS = booleanPreferencesKey("show_reader_chapter_progress")
        private val SHOW_READER_PAGE_NUMBER = booleanPreferencesKey("show_reader_page_number")
        private val SHOW_READER_BATTERY = booleanPreferencesKey("show_reader_battery")
        private val VOLUME_KEY_PAGE_TURN = booleanPreferencesKey("volume_key_page_turn")
        private val BIONIC_READING_ENABLED = booleanPreferencesKey("bionic_reading_enabled")
        private val COMIC_MODE = booleanPreferencesKey("comic_mode")
        private val SCREEN_SLEEP_TIMEOUT_SECONDS = intPreferencesKey("screen_sleep_timeout_seconds")
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
        private val APP_ICON_STYLE = stringPreferencesKey("app_icon_style")
        private val APP_THEME = stringPreferencesKey("app_theme")
        private val STARTUP_SCREEN = stringPreferencesKey("startup_screen")
        private val APP_ACCENT_COLOR = stringPreferencesKey("app_accent_color")
        private val GLOBAL_FONT_MODE = stringPreferencesKey("global_font_mode")
        private val LIQUID_GLASS_TRANSPARENCY = floatPreferencesKey("liquid_glass_transparency")
        private val LIQUID_GLASS_HDR_HIGHLIGHT_ENABLED = booleanPreferencesKey("liquid_glass_hdr_highlight_enabled")
        private val CARD_OUTLINES_ENABLED = booleanPreferencesKey("card_outlines_enabled")
        private val DARK_MODE = stringPreferencesKey("dark_mode")
        private val ENTRANCE_ANIMATIONS_ENABLED = booleanPreferencesKey("entrance_animations_enabled")
        private val MOTION_PREFERENCE = stringPreferencesKey("motion_preference")
        private val E_INK_MODE_ENABLED = booleanPreferencesKey("e_ink_mode_enabled")
        private val TWO_PAGE_SPREAD_ENABLED = booleanPreferencesKey("two_page_spread_enabled")
        private val PREDICTIVE_BACK_ENABLED = booleanPreferencesKey("predictive_back_enabled")
        private val SPLASH_ENABLED = booleanPreferencesKey("splash_enabled")
        private val LAST_READ_BOOK = stringPreferencesKey("last_read_book")
        private val BOOKSHELF_LAYOUT_MODE = intPreferencesKey("bookshelf_layout_mode")
        private val BOOKSHELF_SORT_MODE = stringPreferencesKey("bookshelf_sort_mode")
        private val IMPORT_BOOKS_LAYOUT_MODE = intPreferencesKey("import_books_layout_mode")
        private val HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")
        private val COMPLETED_WELCOME_INSTALL_TIME = longPreferencesKey("completed_welcome_install_time")
        private val HAS_COMPLETED_WELCOME_LANGUAGE_SETUP = booleanPreferencesKey("has_completed_welcome_language_setup")
        private val BUILTIN_GUIDES_SEEDED_VERSION = intPreferencesKey("builtin_guides_seeded_version")
        private val BUILTIN_GUIDES_FOLDER_COVER_VERSION =
            intPreferencesKey("builtin_guides_folder_cover_version")
        private val READER_FIRST_OPEN_HINT_ACKNOWLEDGEMENT_COUNT =
            intPreferencesKey("reader_first_open_hint_acknowledgement_count")
        private val READER_FIRST_OPEN_HINTS_DISABLED =
            booleanPreferencesKey("reader_first_open_hints_disabled")

        // MinerU 第三方云解析设置
        private val MINERU_MODE = stringPreferencesKey("mineru_mode")
        private val MINERU_CONSENT_VERSION = intPreferencesKey("mineru_consent_version")
        private val MINERU_CONSENT_ACCEPTED_AT = longPreferencesKey("mineru_consent_accepted_at")

        // 外部 TTS 听书设置
        private val EXTERNAL_TTS_ENABLED = booleanPreferencesKey("external_tts_enabled")
        private val EXTERNAL_TTS_PROTOCOL = stringPreferencesKey("external_tts_protocol")
        private val EXTERNAL_TTS_BASE_URL = stringPreferencesKey("external_tts_base_url")
        private val EXTERNAL_TTS_MODEL = stringPreferencesKey("external_tts_model")
        private val EXTERNAL_TTS_VOICE = stringPreferencesKey("external_tts_voice")
        private val EXTERNAL_TTS_STYLE = stringPreferencesKey("external_tts_style")
        private val EXTERNAL_TTS_ALLOW_HTTP = booleanPreferencesKey("external_tts_allow_http")
        private val EXTERNAL_TTS_CONSENT_VERSION = intPreferencesKey("external_tts_consent_version")
        private val EXTERNAL_TTS_CONSENT_ACCEPTED_AT = longPreferencesKey("external_tts_consent_accepted_at")
        private val EXTERNAL_TTS_CACHE_LIMIT_MB = intPreferencesKey("external_tts_cache_limit_mb")
        // WebDAV 同步设置
        private val WEBDAV_ENABLED = booleanPreferencesKey("webdav_enabled")
        private val WEBDAV_SERVER_URL = stringPreferencesKey("webdav_server_url")
        private val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        private val WEBDAV_SYNC_PATH = stringPreferencesKey("webdav_sync_path")
        private val WEBDAV_LAST_SYNC_TIME = longPreferencesKey("webdav_last_sync_time")
        private val WEBDAV_SYNC_MODE = stringPreferencesKey("webdav_sync_mode")
        private val WEBDAV_SYNCED_BOOK_IDS = stringPreferencesKey("webdav_synced_book_ids")
        private val WEBDAV_SYNC_BOOK_FILES = booleanPreferencesKey("webdav_sync_book_files")
        private val WEBDAV_SYNC_READING_RECORDS = booleanPreferencesKey("webdav_sync_reading_records")
        private val WEBDAV_SYNC_BOOKMARKS = booleanPreferencesKey("webdav_sync_bookmarks")
        private val WEBDAV_SYNC_NOTES = booleanPreferencesKey("webdav_sync_notes")
        private val WEBDAV_SYNC_PROFILE_SETTINGS = booleanPreferencesKey("webdav_sync_profile_settings")
        private val WEBDAV_SYNC_LIBRARY_ORGANIZATION = booleanPreferencesKey("webdav_sync_library_organization")
        private val WEBDAV_SYNC_READING_DATA = booleanPreferencesKey("webdav_sync_reading_data")
        // 应用语言
        private val APP_LANGUAGE = stringPreferencesKey("app_language")

        // 个人信息
        private val AVATAR_URI = stringPreferencesKey("avatar_uri")
        private val AUTHORIZED_BOOK_DIRECTORIES = stringPreferencesKey("authorized_book_directories")
        private val NICKNAME = stringPreferencesKey("nickname")

        // 已接受的条款/政策版本（用于检查更新）
        private val ACCEPTED_TERMS_VERSION = intPreferencesKey("accepted_terms_version")
        private val ACCEPTED_PRIVACY_VERSION = intPreferencesKey("accepted_privacy_version")

        // 是否已完成首次启动的更新检查
        private val HAS_CHECKED_UPDATE_ON_START = booleanPreferencesKey("has_checked_update_on_start")
        private val ACKNOWLEDGED_NOTICE_IDS = stringSetPreferencesKey("acknowledged_notice_ids")
        private val IGNORED_APP_UPDATE_VERSION_CODE = longPreferencesKey("ignored_app_update_version_code")
        private val PORTABLE_PREFERENCE_METADATA = stringPreferencesKey("portable_preference_metadata_v1")
    }

    // 阅读设置
    fun readerPreferences(bookId: String): Flow<ReaderPreferencesSnapshot> =
        context.dataStore.data.map { preferences ->
            val horizontal = preferences[MARGIN_HORIZ] ?: 38f
            val vertical = preferences[MARGIN_VERT] ?: 64f
            val suites = readThemeSuites(preferences)
            val requestedActiveSuite = preferences[ACTIVE_READER_THEME_SUITE_ID]
            val suiteState = ReaderThemeSuiteState(
                suites = suites,
                activeSuiteId = requestedActiveSuite?.takeIf { id -> suites.any { it.id == id } }
                    ?: ReaderThemeSuites.DAY_ID
            )
            val selectionItems = preferences[SELECTION_MENU_ITEMS]
                ?.takeIf(String::isNotBlank)
                ?.let { raw ->
                    runCatching {
                        JSONObject(raw).let { json ->
                            json.keys().asSequence().associateWith(json::getBoolean)
                        }
                    }.getOrDefault(emptyMap())
                }
                .orEmpty()
            val modeKey = stringPreferencesKey("epub_render_mode_$bookId")
            val themeSuiteBookScopedKey = booleanPreferencesKey("reader_theme_suite_book_scoped_$bookId")
            val themeSuiteBookActiveKey = stringPreferencesKey("reader_theme_suite_book_active_$bookId")
            val optimizeKey = booleanPreferencesKey("optimize_layout_$bookId")
            val cssKey = booleanPreferencesKey("use_epub_css_$bookId")
            val resolvedRenderMode = EpubRenderMode.fromStorage(preferences[modeKey]) ?: when {
                preferences[cssKey] == true || preferences[optimizeKey] == false -> EpubRenderMode.BOOK_LAYOUT
                else -> EpubRenderMode.READER_LAYOUT
            }
            fun cornerContent(corner: ReaderPageCorner): ReaderCornerContent {
                val stored = preferences[readerCornerKey(corner)]
                return if (stored == null) defaultReaderCornerContent(corner)
                else ReaderCornerContent.fromKey(stored)
            }
            ReaderPreferencesSnapshot(
                fontSize = preferences[FONT_SIZE] ?: 16f,
                lineHeight = preferences[LINE_HEIGHT] ?: 1.5f,
                letterSpacing = preferences[LETTER_SPACING] ?: 0f,
                textAlignment = ReaderTextAlignment.fromKey(preferences[TEXT_ALIGNMENT]),
                fontType = preferences[FONT_TYPE] ?: "system",
                marginLeft = preferences[MARGIN_LEFT] ?: horizontal,
                marginRight = preferences[MARGIN_RIGHT] ?: horizontal,
                marginTop = preferences[MARGIN_TOP] ?: vertical,
                marginBottom = preferences[MARGIN_BOTTOM] ?: vertical,
                readerTheme = preferences[READER_THEME] ?: "day",
                brightness = preferences[BRIGHTNESS] ?: -1f,
                customFontPath = preferences[CUSTOM_FONT_PATH],
                customFonts = CustomFontPresetCodec.decode(preferences[CUSTOM_FONTS]),
                readerBackgroundSelection = preferences[READER_BACKGROUND_SELECTION]
                    ?: preferences[READER_THEME]
                    ?: "day",
                readerBackgroundColorSelection = preferences[READER_BACKGROUND_COLOR_SELECTION]
                    ?: preferences.resolveLegacyBackgroundColorSelection(
                        preferences[READER_BACKGROUND_SELECTION] ?: ReaderThemeSuites.DAY_ID
                    ),
                readerBackgroundImageOpacity = (preferences[READER_BACKGROUND_IMAGE_OPACITY] ?: 1f)
                    .coerceIn(0f, 1f),
                readerBackgroundImageBlurDp = (preferences[READER_BACKGROUND_IMAGE_BLUR_DP] ?: 0f)
                    .coerceIn(0f, 40f),
                customReaderBackgrounds = ReaderBackgroundPresetCodec.decode(
                    preferences[CUSTOM_READER_BACKGROUNDS]
                ),
                preserveEpubBackground = preferences[PRESERVE_EPUB_BACKGROUND] ?: true,
                readerTextColor = preferences[READER_TEXT_COLOR],
                pageAnimationSettings = ReaderPageAnimationSettings(
                    slideDurationMs = ReaderPageAnimationSettings.sanitizeDuration(
                        ReaderPageAnimationSettings.MODE_SLIDE,
                        preferences[PAGE_TRANSITION_SLIDE_DURATION_MS]
                            ?: ReaderPageAnimationSettings.SLIDE_DEFAULT_MS
                    ),
                    scrollDurationMs = ReaderPageAnimationSettings.sanitizeDuration(
                        ReaderPageAnimationSettings.MODE_SCROLL,
                        preferences[PAGE_TRANSITION_SCROLL_DURATION_MS]
                            ?: ReaderPageAnimationSettings.SCROLL_DEFAULT_MS
                    ),
                    fadeDurationMs = ReaderPageAnimationSettings.sanitizeDuration(
                        ReaderPageAnimationSettings.MODE_FADE,
                        preferences[PAGE_TRANSITION_FADE_DURATION_MS]
                            ?: ReaderPageAnimationSettings.FADE_DEFAULT_MS
                    ),
                    curlDurationMs = ReaderPageAnimationSettings.sanitizeDuration(
                        ReaderPageAnimationSettings.MODE_CURL,
                        preferences[PAGE_TRANSITION_CURL_DURATION_MS]
                            ?: ReaderPageAnimationSettings.CURL_DEFAULT_MS
                    )
                ),
                pageTransition = ReaderPageTransition.normalizeKey(preferences[PAGE_TRANSITION]),
                readerThemeSuiteState = suiteState,
                readerThemeSuiteBookScoped = preferences[themeSuiteBookScopedKey] ?: false,
                readerThemeSuiteBookActiveId = preferences[themeSuiteBookActiveKey],
                pdfPageMode = PdfPageMode.normalizeKey(preferences[PDF_PAGE_MODE]),
                showReaderChapterProgress = preferences[SHOW_READER_CHAPTER_PROGRESS] ?: true,
                showReaderPageNumber = preferences[SHOW_READER_PAGE_NUMBER] ?: true,
                showReaderBattery = preferences[SHOW_READER_BATTERY] ?: true,
                volumeKeyPageTurnEnabled = preferences[VOLUME_KEY_PAGE_TURN] ?: false,
                bionicReadingEnabled = preferences[BIONIC_READING_ENABLED] ?: false,
                comicModeEnabled = preferences[COMIC_MODE] ?: false,
                bodyFontWeight = preferences[BODY_FONT_WEIGHT] ?: 400,
                eInkModeEnabled = preferences[E_INK_MODE_ENABLED] ?: false,
                twoPageSpreadEnabled = preferences[TWO_PAGE_SPREAD_ENABLED] ?: true,
                screenSleepTimeoutSeconds = preferences[SCREEN_SLEEP_TIMEOUT_SECONDS]
                    ?.takeIf { it in SCREEN_SLEEP_TIMEOUT_SECONDS_OPTIONS }
                    ?: DEFAULT_SCREEN_SLEEP_TIMEOUT_SECONDS,
                readerEdgeTapMode = ReaderEdgeTapMode.fromKey(preferences[READER_EDGE_TAP_MODE]),
                readerTopLeftContent = cornerContent(ReaderPageCorner.TOP_LEFT),
                readerTopRightContent = cornerContent(ReaderPageCorner.TOP_RIGHT),
                readerBottomLeftContent = cornerContent(ReaderPageCorner.BOTTOM_LEFT),
                readerBottomRightContent = cornerContent(ReaderPageCorner.BOTTOM_RIGHT),
                readerDisplayMode = preferences[stringPreferencesKey("reader_display_mode")] ?: "auto",
                paragraphSpacing = preferences[PARAGRAPH_SPACING] ?: 2f,
                firstLineIndent = preferences[FIRST_LINE_INDENT] ?: 2f,
                chineseMode = preferences[stringPreferencesKey("chinese_mode")] ?: "original",
                selectionMenuItems = selectionItems,
                customHighlightPalettes = decodeHighlightPalettes(preferences),
                activeHighlightPaletteId = preferences[ACTIVE_HIGHLIGHT_PALETTE_ID],
                renderMode = resolvedRenderMode,
                txtEncoding = preferences[stringPreferencesKey("txt_encoding_$bookId")] ?: "auto",
                txtTocRuleId = preferences[stringPreferencesKey("txt_toc_rule_$bookId")] ?: "auto",
                txtEncodingHintShown = preferences[booleanPreferencesKey("txt_encoding_hint_shown_$bookId")]
                    ?: false,
                epubLayoutHintShown = preferences[booleanPreferencesKey("epub_layout_hint_shown_$bookId")]
                    ?: false,
                mobiLayoutHintShown = preferences[booleanPreferencesKey("mobi_layout_hint_shown_$bookId")]
                    ?: false,
                readerFirstOpenHintAcknowledgementCount =
                    preferences.readerFirstOpenHintAcknowledgementCount(),
                readerFirstOpenHintsDisabled = preferences[READER_FIRST_OPEN_HINTS_DISABLED] ?: false,
                optimizeLayout = preferences[optimizeKey] ?: true,
                useEpubCss = preferences[cssKey] ?: false,
                readerWritingMode = ReaderWritingMode.fromKey(
                    preferences[stringPreferencesKey("reader_writing_mode_$bookId")]
                )
            )
        }.distinctUntilChanged()

    val fontSize: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[FONT_SIZE] ?: 16f
    }

    val lineHeight: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[LINE_HEIGHT] ?: 1.5f
    }

    val letterSpacing: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[LETTER_SPACING] ?: 0f
    }

    val textAlignment: Flow<ReaderTextAlignment> = context.dataStore.data.map { preferences ->
        ReaderTextAlignment.fromKey(preferences[TEXT_ALIGNMENT])
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

    /** 用户导入的所有自定义字体列表 */
    val customFonts: Flow<List<CustomFontPreset>> = context.dataStore.data.map { preferences ->
        CustomFontPresetCodec.decode(preferences[CUSTOM_FONTS])
    }

    /** 自定义导入字体文件路径（旧版单字体，保留向后兼容） */
    val customFontPath: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_FONT_PATH]
    }

    val readerBackgroundSelection: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[READER_BACKGROUND_SELECTION] ?: preferences[READER_THEME] ?: "day"
    }

    val readerBackgroundColorSelection: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[READER_BACKGROUND_COLOR_SELECTION]
            ?: preferences.resolveLegacyBackgroundColorSelection(
                preferences[READER_BACKGROUND_SELECTION] ?: ReaderThemeSuites.DAY_ID
            )
    }

    val customReaderBackgrounds: Flow<List<ReaderBackgroundPreset>> =
        context.dataStore.data.map { preferences ->
            ReaderBackgroundPresetCodec.decode(preferences[CUSTOM_READER_BACKGROUNDS])
        }

    /** Named custom palettes. The old flat color list is exposed as one legacy palette. */
    val customHighlightPalettes: Flow<List<HighlightPalette>> = context.dataStore.data.map { preferences ->
        decodeHighlightPalettes(preferences)
    }

    /** Null means the built-in default palette is active. */
    val activeHighlightPaletteId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_HIGHLIGHT_PALETTE_ID]
    }

    val selectedHighlightPalette: Flow<HighlightPalette?> = context.dataStore.data.map { preferences ->
        val palettes = decodeHighlightPalettes(preferences)
        if (palettes.isEmpty()) {
            null
        } else {
            val activeId = preferences[ACTIVE_HIGHLIGHT_PALETTE_ID]
            palettes.firstOrNull { it.id == activeId }
                ?: if (activeId == null && palettes.firstOrNull()?.id == "legacy") {
                    palettes.firstOrNull()
                } else if (activeId == "legacy") {
                    palettes.firstOrNull()
                } else {
                    null
                }
        }
    }

    /** Backward-compatible flattened view for callers that only need set colors. */
    val customHighlightColors: Flow<List<String>> = selectedHighlightPalette.map { palette ->
        palette?.normalizedColors?.filterNotNull() ?: emptyList()
    }

    private fun decodeHighlightPalettes(preferences: Preferences): List<HighlightPalette> {
        val decoded = HighlightPaletteCodec.decode(preferences[CUSTOM_HIGHLIGHT_PALETTES])
        if (decoded.isNotEmpty()) return decoded
        val oldRaw = preferences[CUSTOM_HIGHLIGHT_COLORS] ?: return emptyList()
        return runCatching {
            val arr = JSONArray(oldRaw)
            val colors = (0 until arr.length()).map { arr.optString(it).takeIf(String::isNotBlank) }.take(6)
            if (colors.isEmpty()) emptyList()
            else listOf(HighlightPalette(id = "legacy", name = context.getString(R.string.background_custom), colors = colors))
        }.getOrDefault(emptyList())
    }

    /** 选择文本菜单项开关（JSON 对象，如 "{\"copy\":true,\"search\":true}"），为空时默认全部开启 */
    val selectionMenuItems: Flow<Map<String, Boolean>> = context.dataStore.data.map { preferences ->
        val raw = preferences[SELECTION_MENU_ITEMS] ?: ""
        if (raw.isBlank()) emptyMap()
        else try {
            val obj = org.json.JSONObject(raw)
            val map = mutableMapOf<String, Boolean>()
            obj.keys().forEach { key -> map[key] = obj.getBoolean(key) }
            map
        } catch (_: Exception) { emptyMap() }
    }

    val floatingSubtitleSettings: Flow<FloatingSubtitleSettings> = context.dataStore.data.map { preferences ->
        FloatingSubtitleSettings(
            enabled = preferences[TTS_FLOATING_WINDOW] ?: true,
            xFraction = preferences[TTS_FLOATING_X_FRACTION]
                ?: FloatingSubtitleSettings.DEFAULT_X_FRACTION,
            yFraction = preferences[TTS_FLOATING_Y_FRACTION]
                ?: FloatingSubtitleSettings.DEFAULT_Y_FRACTION,
            backgroundColorHex = preferences[TTS_FLOATING_BACKGROUND_COLOR]
                ?: FloatingSubtitleSettings.DEFAULT_BACKGROUND_COLOR,
            backgroundOpacity = preferences[TTS_FLOATING_BACKGROUND_OPACITY]
                ?: FloatingSubtitleSettings.DEFAULT_BACKGROUND_OPACITY,
            cornerRadiusDp = preferences[TTS_FLOATING_CORNER_RADIUS_DP]
                ?: FloatingSubtitleSettings.DEFAULT_CORNER_RADIUS_DP,
            widthDp = preferences[TTS_FLOATING_WIDTH_DP]
                ?: FloatingSubtitleSettings.DEFAULT_WIDTH_DP,
            heightDp = preferences[TTS_FLOATING_HEIGHT_DP]
                ?: FloatingSubtitleSettings.DEFAULT_HEIGHT_DP
        ).normalized()
    }

    /** Backward-compatible view used by older call sites. */
    val ttsFloatingWindow: Flow<Boolean> = floatingSubtitleSettings.map { it.enabled }

    val preferredTtsEngine: Flow<String?> = context.dataStore.data.map { it[PREFERRED_TTS_ENGINE] }
    override val ttsProviderSelection: Flow<TtsProviderSelection> = context.dataStore.data.map { preferences ->
        TtsProviderSelection.resolve(
            storedValue = preferences[TTS_PROVIDER_SELECTION],
            legacyEnginePackage = preferences[PREFERRED_TTS_ENGINE],
            externalTtsConfigured = (preferences[EXTERNAL_TTS_ENABLED] ?: false) &&
                (preferences[EXTERNAL_TTS_CONSENT_VERSION] ?: 0) >= ExternalTtsConfig.CONSENT_VERSION
        )
    }
    val bodyFontWeight: Flow<Int> = context.dataStore.data.map { it[BODY_FONT_WEIGHT] ?: 400 }
    val applyToBodyOnly: Flow<Boolean> = context.dataStore.data.map { it[APPLY_TO_BODY_ONLY] ?: false }

    val preserveEpubBackground: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PRESERVE_EPUB_BACKGROUND] ?: true
    }

    val readerTextColor: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[READER_TEXT_COLOR]
    }

    val readerBackgroundImageOpacity: Flow<Float> = context.dataStore.data.map { preferences ->
        (preferences[READER_BACKGROUND_IMAGE_OPACITY] ?: 1f).coerceIn(0f, 1f)
    }

    val readerBackgroundImageBlurDp: Flow<Float> = context.dataStore.data.map { preferences ->
        (preferences[READER_BACKGROUND_IMAGE_BLUR_DP] ?: 0f).coerceIn(0f, 40f)
    }

    val readerPageAnimationSettings: Flow<ReaderPageAnimationSettings> =
        context.dataStore.data.map { preferences ->
            ReaderPageAnimationSettings(
                slideDurationMs = ReaderPageAnimationSettings.sanitizeDuration(
                    ReaderPageAnimationSettings.MODE_SLIDE,
                    preferences[PAGE_TRANSITION_SLIDE_DURATION_MS]
                        ?: ReaderPageAnimationSettings.SLIDE_DEFAULT_MS
                ),
                scrollDurationMs = ReaderPageAnimationSettings.sanitizeDuration(
                    ReaderPageAnimationSettings.MODE_SCROLL,
                    preferences[PAGE_TRANSITION_SCROLL_DURATION_MS]
                        ?: ReaderPageAnimationSettings.SCROLL_DEFAULT_MS
                ),
                fadeDurationMs = ReaderPageAnimationSettings.sanitizeDuration(
                    ReaderPageAnimationSettings.MODE_FADE,
                    preferences[PAGE_TRANSITION_FADE_DURATION_MS]
                        ?: ReaderPageAnimationSettings.FADE_DEFAULT_MS
                ),
                curlDurationMs = ReaderPageAnimationSettings.sanitizeDuration(
                    ReaderPageAnimationSettings.MODE_CURL,
                    preferences[PAGE_TRANSITION_CURL_DURATION_MS]
                        ?: ReaderPageAnimationSettings.CURL_DEFAULT_MS
                )
            )
        }

    val readerThemeSuiteState: Flow<ReaderThemeSuiteState> = context.dataStore.data.map { preferences ->
        val suites = readThemeSuites(preferences)
        val requestedActiveId = preferences[ACTIVE_READER_THEME_SUITE_ID]
        ReaderThemeSuiteState(
            suites = suites,
            activeSuiteId = requestedActiveId?.takeIf { id -> suites.any { it.id == id } }
                ?: ReaderThemeSuites.DAY_ID
        )
    }

    /** PDF 阅读模式："vertical" | "vertical_paging" | "horizontal"，所有 PDF 共用。 */
    val pdfPageMode: Flow<String> = context.dataStore.data.map { preferences ->
        PdfPageMode.normalizeKey(preferences[PDF_PAGE_MODE])
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

    val bionicReadingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIONIC_READING_ENABLED] ?: false
    }

    /** 漫画模式：图片按屏宽等比缩放、整页无缝上下拼接滚动 */
    val comicMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[COMIC_MODE] ?: false
    }

    val screenSleepTimeoutSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SCREEN_SLEEP_TIMEOUT_SECONDS]
            ?.takeIf { it in SCREEN_SLEEP_TIMEOUT_SECONDS_OPTIONS }
            ?: DEFAULT_SCREEN_SLEEP_TIMEOUT_SECONDS
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

    override val ttsSpeechRate: Flow<Float> = context.dataStore.data.map { preferences ->
        (preferences[TTS_SPEECH_RATE] ?: 1f).coerceIn(0.5f, 2f)
    }

    override val ttsPitch: Flow<Float> = context.dataStore.data.map { preferences ->
        (preferences[TTS_PITCH] ?: 1f).coerceIn(0.5f, 2f)
    }

    // 统计设置
    val dailyGoal: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DAILY_GOAL] ?: 30
    }

    // 应用设置
    val appIconStyle: Flow<String> = context.dataStore.data.map { preferences ->
        AppIconStyle.normalize(preferences[APP_ICON_STYLE])
    }

    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "lumi"
    }

    val startupScreen: Flow<String> = context.dataStore.data.map { preferences ->
        normalizeStartupScreen(preferences[STARTUP_SCREEN])
    }

    val appAccentColor: Flow<String> = context.dataStore.data.map { preferences ->
        normalizeAppAccentHex(preferences[APP_ACCENT_COLOR])
    }

    val globalFontMode: Flow<String> = context.dataStore.data.map { preferences ->
        when (preferences[GLOBAL_FONT_MODE]) {
            "default" -> "default"
            else -> "system"
        }
    }

    val liquidGlassTransparency: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[LIQUID_GLASS_TRANSPARENCY] ?: 0.55f
    }

    val liquidGlassHdrHighlightEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LIQUID_GLASS_HDR_HIGHLIGHT_ENABLED] ?: deviceSupportsHdr
    }

    val cardOutlinesEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CARD_OUTLINES_ENABLED] ?: false
    }

    val darkMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE] ?: "system"
    }

    val entranceAnimationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENTRANCE_ANIMATIONS_ENABLED] ?: true
    }

    /** Full motion policy. The legacy entrance flag remains the migration fallback. */
    val motionPreference: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MOTION_PREFERENCE]
            ?.takeIf { it == "standard" || it == "reduced" }
            ?: if (preferences[ENTRANCE_ANIMATIONS_ENABLED] == false) "reduced" else "standard"
    }

    val eInkModeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[E_INK_MODE_ENABLED] ?: false
    }

    val twoPageSpreadEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TWO_PAGE_SPREAD_ENABLED] ?: true
    }

    val bookshelfLayoutMode: Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[BOOKSHELF_LAYOUT_MODE] ?: 2).coerceIn(1, 3)
    }

    /**
     * Layout mode for the import-book picker. Falls back to the bookshelf mode on
     * first use so the two stay consistent, then remembers the picker's own choice.
     */
    val importBooksLayoutMode: Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[IMPORT_BOOKS_LAYOUT_MODE]
            ?: preferences[BOOKSHELF_LAYOUT_MODE]
            ?: 2).coerceIn(1, 3)
    }

    val predictiveBackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PREDICTIVE_BACK_ENABLED] ?: true
    }

    val bookshelfSortMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[BOOKSHELF_SORT_MODE] ?: "LAST_READ"
    }

    /** Single DataStore read used to refresh the non-blocking Activity launch snapshot. */
    val launchThemeSnapshot: Flow<LaunchThemeSnapshot> = context.dataStore.data.map { preferences ->
        LaunchThemeSnapshot(
            iconStyle = AppIconStyle.normalize(preferences[APP_ICON_STYLE]),
            appTheme = preferences[APP_THEME] ?: "lumi",
            appAccentColor = normalizeAppAccentHex(preferences[APP_ACCENT_COLOR]),
            globalFontMode = if (preferences[GLOBAL_FONT_MODE] == "default") "default" else "system",
            liquidGlassTransparency = preferences[LIQUID_GLASS_TRANSPARENCY] ?: 0.55f,
            liquidGlassHdrHighlightEnabled =
                preferences[LIQUID_GLASS_HDR_HIGHLIGHT_ENABLED] ?: deviceSupportsHdr,
            cardOutlinesEnabled = preferences[CARD_OUTLINES_ENABLED] ?: false,
            darkMode = preferences[DARK_MODE] ?: "system",
            motionPreference = preferences[MOTION_PREFERENCE]
                ?.takeIf { it == "standard" || it == "reduced" }
                ?: if (preferences[ENTRANCE_ANIMATIONS_ENABLED] == false) "reduced" else "standard",
            eInkModeEnabled = preferences[E_INK_MODE_ENABLED] ?: false,
            predictiveBackEnabled = preferences[PREDICTIVE_BACK_ENABLED] ?: true
        )
    }.distinctUntilChanged()

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

    /**
     * The language and e-ink setup was introduced after the original welcome flow.
     * Keep its completion state independent from the per-version welcome marker so
     * existing users see it once after updating, while future updates skip it.
     */
    val hasCompletedWelcomeLanguageSetup: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_COMPLETED_WELCOME_LANGUAGE_SETUP] ?: false
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

    // 外部 TTS 听书设置
    override val externalTtsSettings: Flow<ExternalTtsSettings> = context.dataStore.data.map { preferences ->
        ExternalTtsSettings(
            enabled = preferences[EXTERNAL_TTS_ENABLED] ?: false,
            protocol = ExternalTtsProtocol.fromKey(preferences[EXTERNAL_TTS_PROTOCOL]),
            baseUrl = preferences[EXTERNAL_TTS_BASE_URL] ?: "",
            model = preferences[EXTERNAL_TTS_MODEL] ?: "",
            voice = preferences[EXTERNAL_TTS_VOICE] ?: "",
            styleInstructions = preferences[EXTERNAL_TTS_STYLE] ?: "",
            allowHttp = preferences[EXTERNAL_TTS_ALLOW_HTTP] ?: false,
            consentVersion = preferences[EXTERNAL_TTS_CONSENT_VERSION] ?: 0,
            consentAcceptedAt = preferences[EXTERNAL_TTS_CONSENT_ACCEPTED_AT] ?: 0L
        )
    }

    val externalTtsCacheLimitMb: Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[EXTERNAL_TTS_CACHE_LIMIT_MB] ?: ExternalTtsConfig.DEFAULT_AUDIO_CACHE_LIMIT_MB)
            .coerceIn(
                ExternalTtsConfig.MIN_AUDIO_CACHE_LIMIT_MB,
                ExternalTtsConfig.MAX_AUDIO_CACHE_LIMIT_MB
            )
    }

    // WebDAV 同步设置
    val webdavConfig: Flow<WebdavConfig> = context.dataStore.data.map { preferences ->
        WebdavConfig(
            enabled = preferences[WEBDAV_ENABLED] ?: false,
            serverUrl = preferences[WEBDAV_SERVER_URL] ?: "",
            username = preferences[WEBDAV_USERNAME] ?: "",
            syncPath = preferences[WEBDAV_SYNC_PATH] ?: "LumiBooks",
            lastSyncTime = preferences[WEBDAV_LAST_SYNC_TIME] ?: 0L,
            syncMode = preferences[WEBDAV_SYNC_MODE] ?: "auto",
            syncBookFiles = preferences[WEBDAV_SYNC_BOOK_FILES] ?: true,
            syncProfileAndSettings = preferences[WEBDAV_SYNC_PROFILE_SETTINGS] ?: true,
            syncLibraryOrganization = preferences[WEBDAV_SYNC_LIBRARY_ORGANIZATION] ?: true,
            syncReadingData = preferences[WEBDAV_SYNC_READING_DATA]
                ?: ((preferences[WEBDAV_SYNC_READING_RECORDS] ?: true) ||
                    (preferences[WEBDAV_SYNC_BOOKMARKS] ?: true) ||
                    (preferences[WEBDAV_SYNC_NOTES] ?: true))
        )
    }

    val webdavSyncedBookIds: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val raw = preferences[WEBDAV_SYNCED_BOOK_IDS] ?: ""
        if (raw.isBlank()) emptySet()
        else raw.split(",").toSet()
    }

    override fun externalTtsResumePosition(bookId: String): Flow<ExternalTtsResumePosition?> =
        context.dataStore.data.map { preferences ->
            val raw = preferences[externalTtsResumeKey(bookId)] ?: return@map null
            parseResumePosition(raw, bookId)
        }

    // 应用语言
    val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_LANGUAGE] ?: "system"
    }

    // 个人信息
    val avatarUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AVATAR_URI]
    }

    val authorizedBookDirectories: Flow<List<String>> = context.dataStore.data.map { preferences ->
        preferences[AUTHORIZED_BOOK_DIRECTORIES]
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
    }

    val nickname: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[NICKNAME] ?: context.getString(R.string.default_nickname)
    }

    // 保存方法
    suspend fun saveBookshelfLayoutMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[BOOKSHELF_LAYOUT_MODE] = mode.coerceIn(1, 3)
        }
    }

    suspend fun saveImportBooksLayoutMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[IMPORT_BOOKS_LAYOUT_MODE] = mode.coerceIn(1, 3)
        }
    }

    suspend fun saveFontSize(fontSize: Float) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE] = fontSize
            preferences.updateActiveReaderThemeSuite { copy(fontSize = fontSize) }
        }
    }

    suspend fun saveLineHeight(lineHeight: Float) {
        context.dataStore.edit { preferences ->
            preferences[LINE_HEIGHT] = lineHeight
            preferences.updateActiveReaderThemeSuite { copy(lineHeight = lineHeight) }
        }
    }

    suspend fun saveLetterSpacing(letterSpacing: Float) {
        context.dataStore.edit { preferences ->
            preferences[LETTER_SPACING] = letterSpacing
            preferences.updateActiveReaderThemeSuite { copy(letterSpacing = letterSpacing) }
        }
    }

    suspend fun saveBookshelfSortMode(mode: String) {
        context.dataStore.edit { preferences -> preferences[BOOKSHELF_SORT_MODE] = mode }
    }

    val portablePreferenceChanges: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences.asMap()
            .filterKeys { isPortablePreferenceKey(it.name) }
            .entries
            .sortedBy { it.key.name }
            .fold(1) { hash, (key, value) -> 31 * hash + key.name.hashCode() + value.hashCode() }
    }.distinctUntilChanged()

    suspend fun saveTextAlignment(alignment: ReaderTextAlignment) {
        context.dataStore.edit { preferences ->
            preferences[TEXT_ALIGNMENT] = alignment.key
            preferences.updateActiveReaderThemeSuite { copy(textAlignment = alignment) }
        }
    }

    suspend fun saveFontType(fontType: String) {
        context.dataStore.edit { preferences ->
            preferences[FONT_TYPE] = fontType
            preferences.updateActiveReaderThemeSuite { copy(fontType = fontType) }
        }
    }

    /** 已下载远程字体的版本号（key -> version，JSON 存储，沿用 CustomFontPresetCodec 先例）。 */
    val remoteFontVersions: Flow<Map<String, Int>> = context.dataStore.data.map { preferences ->
        preferences[REMOTE_FONT_VERSIONS]?.let { raw ->
            runCatching {
                val obj = JSONObject(raw)
                obj.keys().asSequence().associateWith { obj.optInt(it, 0) }
            }.getOrDefault(emptyMap())
        } ?: emptyMap()
    }

    suspend fun saveRemoteFontVersion(key: String, version: Int) {
        context.dataStore.edit { preferences ->
            val obj = runCatching { JSONObject(preferences[REMOTE_FONT_VERSIONS] ?: "{}") }
                .getOrDefault(JSONObject())
            obj.put(key, version)
            preferences[REMOTE_FONT_VERSIONS] = obj.toString()
        }
    }

    suspend fun saveMarginHoriz(marginHoriz: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_HORIZ] = marginHoriz
            preferences[MARGIN_LEFT] = marginHoriz
            preferences[MARGIN_RIGHT] = marginHoriz
            preferences.updateActiveReaderThemeSuite {
                copy(marginLeft = marginHoriz, marginRight = marginHoriz)
            }
        }
    }

    suspend fun saveMarginVert(marginVert: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_VERT] = marginVert
            preferences[MARGIN_TOP] = marginVert
            preferences[MARGIN_BOTTOM] = marginVert
            preferences.updateActiveReaderThemeSuite {
                copy(marginTop = marginVert, marginBottom = marginVert)
            }
        }
    }

    suspend fun saveMarginLeft(marginLeft: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_LEFT] = marginLeft
            val right = preferences[MARGIN_RIGHT] ?: preferences[MARGIN_HORIZ] ?: 38f
            preferences[MARGIN_HORIZ] = (marginLeft + right) / 2f
            preferences.updateActiveReaderThemeSuite { copy(marginLeft = marginLeft) }
        }
    }

    suspend fun saveMarginRight(marginRight: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_RIGHT] = marginRight
            val left = preferences[MARGIN_LEFT] ?: preferences[MARGIN_HORIZ] ?: 38f
            preferences[MARGIN_HORIZ] = (left + marginRight) / 2f
            preferences.updateActiveReaderThemeSuite { copy(marginRight = marginRight) }
        }
    }

    suspend fun saveMarginTop(marginTop: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_TOP] = marginTop
            val bottom = preferences[MARGIN_BOTTOM] ?: preferences[MARGIN_VERT] ?: 64f
            preferences[MARGIN_VERT] = (marginTop + bottom) / 2f
            preferences.updateActiveReaderThemeSuite { copy(marginTop = marginTop) }
        }
    }

    suspend fun saveMarginBottom(marginBottom: Float) {
        context.dataStore.edit { preferences ->
            preferences[MARGIN_BOTTOM] = marginBottom
            val top = preferences[MARGIN_TOP] ?: preferences[MARGIN_VERT] ?: 64f
            preferences[MARGIN_VERT] = (top + marginBottom) / 2f
            preferences.updateActiveReaderThemeSuite { copy(marginBottom = marginBottom) }
        }
    }

    suspend fun saveReaderTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[READER_THEME] = theme
            preferences[READER_BACKGROUND_SELECTION] = theme
            preferences.updateActiveReaderThemeSuite {
                copy(backgroundSelection = theme, backgroundColorSelection = theme)
            }
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

    suspend fun saveCustomFonts(presets: List<CustomFontPreset>) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_FONTS] = CustomFontPresetCodec.encode(presets)
        }
    }

    suspend fun saveCustomHighlightColors(colors: List<String>) {
        context.dataStore.edit { preferences ->
            if (colors.isEmpty()) {
                preferences.remove(CUSTOM_HIGHLIGHT_COLORS)
            } else {
                preferences[CUSTOM_HIGHLIGHT_COLORS] = JSONArray(colors).toString()
            }
        }
    }

    suspend fun saveCustomHighlightPalettes(palettes: List<HighlightPalette>) {
        context.dataStore.edit { preferences ->
            if (palettes.isEmpty()) {
                preferences.remove(CUSTOM_HIGHLIGHT_PALETTES)
                preferences.remove(ACTIVE_HIGHLIGHT_PALETTE_ID)
            } else {
                preferences[CUSTOM_HIGHLIGHT_PALETTES] = HighlightPaletteCodec.encode(palettes)
            }
            preferences.remove(CUSTOM_HIGHLIGHT_COLORS)
        }
    }

    suspend fun saveActiveHighlightPaletteId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id.isNullOrBlank()) preferences.remove(ACTIVE_HIGHLIGHT_PALETTE_ID)
            else preferences[ACTIVE_HIGHLIGHT_PALETTE_ID] = id
        }
    }

    suspend fun saveSelectionMenuItems(items: Map<String, Boolean>) {
        context.dataStore.edit { preferences ->
            if (items.isEmpty()) {
                preferences.remove(SELECTION_MENU_ITEMS)
            } else {
                val obj = JSONObject()
                items.forEach { (k, v) -> obj.put(k, v) }
                preferences[SELECTION_MENU_ITEMS] = obj.toString()
            }
        }
    }

    suspend fun saveTtsFloatingWindow(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TTS_FLOATING_WINDOW] = enabled
        }
    }

    suspend fun saveFloatingSubtitleSettings(settings: FloatingSubtitleSettings) {
        val value = settings.normalized()
        context.dataStore.edit { preferences ->
            preferences[TTS_FLOATING_WINDOW] = value.enabled
            preferences[TTS_FLOATING_X_FRACTION] = value.xFraction
            preferences[TTS_FLOATING_Y_FRACTION] = value.yFraction
            preferences[TTS_FLOATING_BACKGROUND_COLOR] = value.backgroundColorHex
            preferences[TTS_FLOATING_BACKGROUND_OPACITY] = value.backgroundOpacity
            preferences[TTS_FLOATING_CORNER_RADIUS_DP] = value.cornerRadiusDp
            preferences[TTS_FLOATING_WIDTH_DP] = value.widthDp
            preferences[TTS_FLOATING_HEIGHT_DP] = value.heightDp
        }
    }

    suspend fun saveFloatingSubtitlePosition(xFraction: Float, yFraction: Float) {
        context.dataStore.edit { preferences ->
            preferences[TTS_FLOATING_X_FRACTION] = xFraction.coerceIn(0f, 1f)
            preferences[TTS_FLOATING_Y_FRACTION] = yFraction.coerceIn(0f, 1f)
        }
    }

    suspend fun saveFloatingSubtitleBackgroundColor(colorHex: String) {
        context.dataStore.edit { preferences ->
            preferences[TTS_FLOATING_BACKGROUND_COLOR] = FloatingSubtitleSettings.normalizeColor(colorHex)
        }
    }

    suspend fun saveFloatingSubtitleBackgroundOpacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[TTS_FLOATING_BACKGROUND_OPACITY] = opacity.coerceIn(0f, 1f)
        }
    }

    suspend fun saveFloatingSubtitleCornerRadius(radiusDp: Float) {
        context.dataStore.edit { preferences ->
            preferences[TTS_FLOATING_CORNER_RADIUS_DP] = radiusDp.coerceIn(
                FloatingSubtitleSettings.MIN_CORNER_RADIUS_DP,
                FloatingSubtitleSettings.MAX_CORNER_RADIUS_DP
            )
        }
    }

    suspend fun saveFloatingSubtitleSize(widthDp: Float, heightDp: Float) {
        context.dataStore.edit { preferences ->
            preferences[TTS_FLOATING_WIDTH_DP] = widthDp.coerceIn(
                FloatingSubtitleSettings.MIN_WIDTH_DP,
                FloatingSubtitleSettings.MAX_WIDTH_DP
            )
            preferences[TTS_FLOATING_HEIGHT_DP] = heightDp.coerceIn(
                FloatingSubtitleSettings.MIN_HEIGHT_DP,
                FloatingSubtitleSettings.MAX_HEIGHT_DP
            )
        }
    }

    suspend fun saveReaderBackgroundSelection(selection: String) {
        context.dataStore.edit { preferences ->
            preferences[READER_BACKGROUND_SELECTION] = selection
            preferences.updateActiveReaderThemeSuite {
                copy(
                    backgroundSelection = selection,
                    backgroundColorSelection = if (selection.startsWith("custom:")) {
                        backgroundColorSelection
                    } else {
                        selection
                    }
                )
            }
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
            preferences.updateActiveReaderThemeSuite { copy(textColor = color) }
        }
    }

    suspend fun savePdfPageMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PDF_PAGE_MODE] = PdfPageMode.normalizeKey(mode)
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

    suspend fun saveBionicReadingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIONIC_READING_ENABLED] = enabled
        }
    }

    suspend fun saveComicMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[COMIC_MODE] = enabled
        }
    }

    suspend fun saveScreenSleepTimeoutSeconds(seconds: Int) {
        if (seconds !in SCREEN_SLEEP_TIMEOUT_SECONDS_OPTIONS) return
        context.dataStore.edit { preferences ->
            preferences[SCREEN_SLEEP_TIMEOUT_SECONDS] = seconds
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

    override suspend fun saveTtsSpeechRate(rate: Float) {
        context.dataStore.edit { preferences ->
            preferences[TTS_SPEECH_RATE] = rate.coerceIn(0.5f, 2f)
        }
    }

    suspend fun savePreferredTtsEngine(packageName: String?) {
        context.dataStore.edit { preferences ->
            if (packageName == null) preferences.remove(PREFERRED_TTS_ENGINE)
            else preferences[PREFERRED_TTS_ENGINE] = packageName
        }
    }

    suspend fun saveBodyFontWeight(weight: Int) {
        context.dataStore.edit { preferences ->
            val sanitized = weight.coerceIn(100, 900)
            preferences[BODY_FONT_WEIGHT] = sanitized
            preferences.updateActiveReaderThemeSuite { copy(bodyFontWeight = sanitized) }
        }
    }

    suspend fun saveApplyToBodyOnly(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[APPLY_TO_BODY_ONLY] = enabled }
    }

    override suspend fun saveTtsPitch(pitch: Float) {
        context.dataStore.edit { preferences ->
            preferences[TTS_PITCH] = pitch.coerceIn(0.5f, 2f)
        }
    }

    suspend fun resetAdvancedReaderSettings(targetSuiteId: String? = null) {
        context.dataStore.edit { preferences ->
            val shouldApplyLegacyThemeFields = targetSuiteId == null ||
                targetSuiteId == preferences[ACTIVE_READER_THEME_SUITE_ID]
            if (shouldApplyLegacyThemeFields) {
                preferences[LINE_HEIGHT] = 1.5f
                preferences[LETTER_SPACING] = 0f
                preferences[TEXT_ALIGNMENT] = ReaderTextAlignment.NATURAL.key
                preferences[FONT_TYPE] = "system"
                preferences[MARGIN_HORIZ] = 38f
                preferences[MARGIN_VERT] = 64f
                preferences[MARGIN_LEFT] = 38f
                preferences[MARGIN_RIGHT] = 38f
                preferences[MARGIN_TOP] = 64f
                preferences[MARGIN_BOTTOM] = 64f
                preferences[PARAGRAPH_SPACING] = 2f
                preferences[FIRST_LINE_INDENT] = 2f
                preferences.remove(READER_TEXT_COLOR)
            }
            preferences[SHOW_READER_CHAPTER_PROGRESS] = true
            preferences[SHOW_READER_PAGE_NUMBER] = true
            preferences[SHOW_READER_BATTERY] = true
            preferences[VOLUME_KEY_PAGE_TURN] = false
            preferences[BIONIC_READING_ENABLED] = false
            preferences[SCREEN_SLEEP_TIMEOUT_SECONDS] = DEFAULT_SCREEN_SLEEP_TIMEOUT_SECONDS
            preferences[READER_EDGE_TAP_MODE] = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT.key
            ReaderPageCorner.entries.forEach { corner ->
                preferences[readerCornerKey(corner)] = defaultReaderCornerContent(corner).key
            }
            val reset = ReaderThemeSettings(
                textColor = null,
                fontType = "system",
                lineHeight = 1.5f,
                letterSpacing = 0f,
                textAlignment = ReaderTextAlignment.NATURAL,
                paragraphSpacing = 2f,
                firstLineIndent = 2f,
                marginLeft = 38f,
                marginRight = 38f,
                marginTop = 64f,
                marginBottom = 64f
            )
            if (targetSuiteId == null) {
                preferences.updateActiveReaderThemeSuite {
                    copy(
                        textColor = reset.textColor,
                        fontType = reset.fontType,
                        lineHeight = reset.lineHeight,
                        letterSpacing = reset.letterSpacing,
                        textAlignment = reset.textAlignment,
                        paragraphSpacing = reset.paragraphSpacing,
                        firstLineIndent = reset.firstLineIndent,
                        marginLeft = reset.marginLeft,
                        marginRight = reset.marginRight,
                        marginTop = reset.marginTop,
                        marginBottom = reset.marginBottom
                    )
                }
            } else {
                val suites = readThemeSuites(preferences)
                preferences[READER_THEME_SUITES] = ReaderThemeSuiteCodec.encode(
                    suites.map { suite ->
                        if (suite.id == targetSuiteId) suite.copy(settings = suite.settings.copy(
                            textColor = reset.textColor,
                            fontType = reset.fontType,
                            lineHeight = reset.lineHeight,
                            letterSpacing = reset.letterSpacing,
                            textAlignment = reset.textAlignment,
                            paragraphSpacing = reset.paragraphSpacing,
                            firstLineIndent = reset.firstLineIndent,
                            marginLeft = reset.marginLeft,
                            marginRight = reset.marginRight,
                            marginTop = reset.marginTop,
                            marginBottom = reset.marginBottom
                        )) else suite
                    }
                )
            }
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

    suspend fun migrateReaderThemeSuites() {
        context.dataStore.edit { preferences ->
            val currentVersion = preferences[READER_THEME_SUITES_VERSION] ?: 0
            if (currentVersion >= 1 && preferences[READER_THEME_SUITES] != null) {
                var normalized = readThemeSuites(preferences)
                val activeId = preferences[ACTIVE_READER_THEME_SUITE_ID]
                    ?.takeIf { id -> normalized.any { it.id == id } }
                    ?: ReaderThemeSuites.DAY_ID
                if (currentVersion < 2) {
                    val legacy = preferences.toReaderThemeSettings(
                        normalized.firstOrNull { it.id == activeId }
                            ?.settings
                            ?.backgroundSelection
                            ?: ReaderThemeSuites.DAY_ID
                    )
                    normalized = normalized.map { suite ->
                        if (suite.id == activeId) {
                            suite.copy(
                                settings = suite.settings.copy(
                                    backgroundColorSelection = legacy.backgroundColorSelection,
                                    backgroundImageOpacity = legacy.backgroundImageOpacity,
                                    backgroundImageBlurDp = legacy.backgroundImageBlurDp,
                                    bodyFontWeight = legacy.bodyFontWeight
                                )
                            )
                        } else {
                            suite
                        }
                    }
                }
                preferences[READER_THEME_SUITES] = ReaderThemeSuiteCodec.encode(normalized)
                preferences[ACTIVE_READER_THEME_SUITE_ID] = activeId
                preferences[READER_THEME_SUITES_VERSION] = 2
                return@edit
            }

            val backgroundSelection = preferences[READER_BACKGROUND_SELECTION]
                ?: preferences[READER_THEME]
                ?: ReaderThemeSuites.DAY_ID
            val currentSettings = preferences.toReaderThemeSettings(backgroundSelection)
            val migrated = ReaderThemeSuites.fromLegacy(currentSettings)
            preferences[READER_THEME_SUITES] = ReaderThemeSuiteCodec.encode(migrated.suites)
            preferences[ACTIVE_READER_THEME_SUITE_ID] = migrated.activeSuiteId
            preferences[READER_THEME_SUITES_VERSION] = 2
        }
    }

    suspend fun saveReaderThemeSuiteState(
        suites: List<ReaderThemeSuite>,
        activeSuiteId: String,
        applyActiveSuite: Boolean
    ) {
        context.dataStore.edit { preferences ->
            val normalized = ReaderThemeSuites.normalized(suites)
            val resolvedActiveId = activeSuiteId.takeIf { id -> normalized.any { it.id == id } }
                ?: ReaderThemeSuites.DAY_ID
            preferences[READER_THEME_SUITES] = ReaderThemeSuiteCodec.encode(normalized)
            preferences[ACTIVE_READER_THEME_SUITE_ID] = resolvedActiveId
            preferences[READER_THEME_SUITES_VERSION] = 2
            if (applyActiveSuite) {
                normalized.firstOrNull { it.id == resolvedActiveId }
                    ?.settings
                    ?.let { settings -> preferences.applyReaderThemeSettings(settings) }
            }
        }
    }

    suspend fun ensureReaderMigrations() {
        if (readerMigrationsComplete) return
        readerMigrationMutex.withLock {
            if (readerMigrationsComplete) return
            migrateAdvancedReaderDefaults()
            migrateReaderThemeSuites()
            readerMigrationsComplete = true
        }
    }

    /** Saves one suite without changing which suite is active. */
    suspend fun updateReaderThemeSuite(suiteId: String, settings: ReaderThemeSettings) {
        context.dataStore.edit { preferences ->
            val suites = readThemeSuites(preferences)
            if (suites.none { it.id == suiteId }) return@edit
            val updated = ReaderThemeSuites.normalized(suites.map { suite ->
                if (suite.id == suiteId) suite.copy(settings = settings) else suite
            })
            preferences[READER_THEME_SUITES] = ReaderThemeSuiteCodec.encode(updated)
            preferences[READER_THEME_SUITES_VERSION] = 2
            if (preferences[ACTIVE_READER_THEME_SUITE_ID] == suiteId) {
                preferences.applyReaderThemeSettings(updated.first { it.id == suiteId }.settings)
            }
        }
    }

    suspend fun renameReaderThemeSuite(suiteId: String, name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return
        context.dataStore.edit { preferences ->
            val updated = readThemeSuites(preferences).map { suite ->
                if (suite.id == suiteId && !suite.isBuiltIn) suite.copy(customName = normalizedName)
                else suite
            }
            preferences[READER_THEME_SUITES] = ReaderThemeSuiteCodec.encode(updated)
        }
    }

    /** Activates and applies an already persisted suite. */
    suspend fun setActiveReaderThemeSuite(suiteId: String) {
        context.dataStore.edit { preferences ->
            val suite = readThemeSuites(preferences).firstOrNull { it.id == suiteId } ?: return@edit
            preferences[ACTIVE_READER_THEME_SUITE_ID] = suite.id
            preferences.applyReaderThemeSettings(suite.settings)
        }
    }

    fun readerThemeSuiteBookScoped(bookId: String): Flow<Boolean> =
        context.dataStore.data.map { it[booleanPreferencesKey("reader_theme_suite_book_scoped_$bookId")] ?: false }

    suspend fun setReaderThemeSuiteBookScoped(
        bookId: String,
        enabled: Boolean,
        activeSuiteId: String? = null
    ) {
        val scopedKey = booleanPreferencesKey("reader_theme_suite_book_scoped_$bookId")
        val activeKey = stringPreferencesKey("reader_theme_suite_book_active_$bookId")
        context.dataStore.edit { preferences ->
            if (enabled) {
                preferences[scopedKey] = true
                activeSuiteId?.let { preferences[activeKey] = it }
            } else {
                preferences.remove(scopedKey)
                preferences.remove(activeKey)
            }
        }
    }

    suspend fun saveReaderThemeSuiteBookActiveId(bookId: String, suiteId: String) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("reader_theme_suite_book_active_$bookId")] = suiteId
        }
    }

    suspend fun savePreserveEpubBackground(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PRESERVE_EPUB_BACKGROUND] = enabled
        }
    }


    suspend fun saveReaderBackgroundState(
        theme: String,
        selection: String,
        presets: List<ReaderBackgroundPreset>? = null
    ) {
        context.dataStore.edit { preferences ->
            val availablePresets = presets ?: ReaderBackgroundPresetCodec.decode(
                preferences[CUSTOM_READER_BACKGROUNDS]
            )
            val selectedType = availablePresets.firstOrNull { it.selectionKey == selection }?.type
            preferences[READER_THEME] = theme
            preferences[READER_BACKGROUND_SELECTION] = selection
            preferences.updateActiveReaderThemeSuite {
                copy(
                    backgroundSelection = selection,
                    backgroundColorSelection = if (selectedType == ReaderBackgroundType.IMAGE) {
                        backgroundColorSelection
                    } else {
                        selection
                    }
                )
            }
            if (presets != null) {
                preferences[CUSTOM_READER_BACKGROUNDS] = ReaderBackgroundPresetCodec.encode(presets)
            }
        }
    }

    /** 每本书的"优化排版"开关（per-book），默认 true */
    fun renderMode(bookId: String): Flow<EpubRenderMode> {
        val modeKey = stringPreferencesKey("epub_render_mode_$bookId")
        val optimizeKey = booleanPreferencesKey("optimize_layout_$bookId")
        val cssKey = booleanPreferencesKey("use_epub_css_$bookId")
        return context.dataStore.data.map { preferences ->
            EpubRenderMode.fromStorage(preferences[modeKey]) ?: when {
                preferences[cssKey] == true || preferences[optimizeKey] == false -> EpubRenderMode.BOOK_LAYOUT
                preferences.contains(optimizeKey) || preferences.contains(cssKey) -> EpubRenderMode.READER_LAYOUT
                else -> EpubRenderMode.READER_LAYOUT
            }
        }
    }

    suspend fun migrateRenderMode(bookId: String): EpubRenderMode {
        val modeKey = stringPreferencesKey("epub_render_mode_$bookId")
        val optimizeKey = booleanPreferencesKey("optimize_layout_$bookId")
        val cssKey = booleanPreferencesKey("use_epub_css_$bookId")
        var resolved = EpubRenderMode.READER_LAYOUT
        context.dataStore.edit { preferences ->
            resolved = EpubRenderMode.fromStorage(preferences[modeKey]) ?: when {
                preferences[cssKey] == true || preferences[optimizeKey] == false -> EpubRenderMode.BOOK_LAYOUT
                preferences.contains(optimizeKey) || preferences.contains(cssKey) -> EpubRenderMode.READER_LAYOUT
                else -> EpubRenderMode.READER_LAYOUT
            }
            preferences[modeKey] = resolved.storageValue
        }
        return resolved
    }

    suspend fun saveRenderMode(bookId: String, mode: EpubRenderMode) {
        val key = stringPreferencesKey("epub_render_mode_$bookId")
        context.dataStore.edit { preferences -> preferences[key] = mode.storageValue }
    }

    fun txtEncoding(bookId: String): Flow<String> {
        val key = stringPreferencesKey("txt_encoding_$bookId")
        return context.dataStore.data.map { preferences -> preferences[key] ?: "auto" }
    }

    suspend fun saveTxtEncoding(bookId: String, encoding: String) {
        val key = stringPreferencesKey("txt_encoding_$bookId")
        context.dataStore.edit { preferences -> preferences[key] = encoding }
    }

    fun txtEncodingHintShown(bookId: String): Flow<Boolean> {
        val key = booleanPreferencesKey("txt_encoding_hint_shown_$bookId")
        return context.dataStore.data.map { preferences -> preferences[key] ?: false }
    }

    suspend fun markTxtEncodingHintShown(bookId: String, doNotShowAgain: Boolean = false) {
        val key = booleanPreferencesKey("txt_encoding_hint_shown_$bookId")
        recordReaderFirstOpenHintAcknowledgement(key, doNotShowAgain)
    }

    fun epubLayoutHintShown(bookId: String): Flow<Boolean> {
        val key = booleanPreferencesKey("epub_layout_hint_shown_$bookId")
        return context.dataStore.data.map { preferences -> preferences[key] ?: false }
    }

    suspend fun markEpubLayoutHintShown(bookId: String, doNotShowAgain: Boolean = false) {
        val key = booleanPreferencesKey("epub_layout_hint_shown_$bookId")
        recordReaderFirstOpenHintAcknowledgement(key, doNotShowAgain)
    }

    fun mobiLayoutHintShown(bookId: String): Flow<Boolean> {
        val key = booleanPreferencesKey("mobi_layout_hint_shown_$bookId")
        return context.dataStore.data.map { preferences -> preferences[key] ?: false }
    }

    suspend fun markMobiLayoutHintShown(bookId: String, doNotShowAgain: Boolean = false) {
        val key = booleanPreferencesKey("mobi_layout_hint_shown_$bookId")
        recordReaderFirstOpenHintAcknowledgement(key, doNotShowAgain)
    }

    private suspend fun recordReaderFirstOpenHintAcknowledgement(
        bookHintKey: Preferences.Key<Boolean>,
        doNotShowAgain: Boolean
    ) {
        context.dataStore.edit { preferences ->
            val currentCount = preferences.readerFirstOpenHintAcknowledgementCount()
            preferences[bookHintKey] = true
            preferences[READER_FIRST_OPEN_HINT_ACKNOWLEDGEMENT_COUNT] = if (doNotShowAgain) {
                currentCount
            } else {
                (currentCount + 1).coerceAtMost(ReaderFirstOpenHintPolicy.MAX_ACKNOWLEDGEMENTS)
            }
            if (doNotShowAgain) {
                preferences[READER_FIRST_OPEN_HINTS_DISABLED] = true
            }
        }
    }

    private fun Preferences.readerFirstOpenHintAcknowledgementCount(): Int {
        this[READER_FIRST_OPEN_HINT_ACKNOWLEDGEMENT_COUNT]?.let {
            return it.coerceIn(0, ReaderFirstOpenHintPolicy.MAX_ACKNOWLEDGEMENTS)
        }

        // Preserve acknowledgements recorded by releases that only stored per-book flags.
        return asMap().count { (key, value) ->
            value == true && (
                key.name.startsWith("txt_encoding_hint_shown_") ||
                    key.name.startsWith("epub_layout_hint_shown_") ||
                    key.name.startsWith("mobi_layout_hint_shown_")
                )
        }.coerceAtMost(ReaderFirstOpenHintPolicy.MAX_ACKNOWLEDGEMENTS)
    }

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

    /** 是否加载 EPUB 自带 CSS 样式（per-book，默认 false） */
    fun useEpubCss(bookId: String): Flow<Boolean> {
        val key = booleanPreferencesKey("use_epub_css_$bookId")
        return context.dataStore.data.map { it[key] ?: false }
    }

    suspend fun saveUseEpubCss(bookId: String, enabled: Boolean) {
        val key = booleanPreferencesKey("use_epub_css_$bookId")
        context.dataStore.edit { it[key] = enabled }
    }

    fun readerWritingMode(bookId: String): Flow<ReaderWritingMode> {
        val key = stringPreferencesKey("reader_writing_mode_$bookId")
        return context.dataStore.data.map { preferences ->
            ReaderWritingMode.fromKey(preferences[key])
        }
    }

    suspend fun saveReaderWritingMode(bookId: String, mode: ReaderWritingMode) {
        val key = stringPreferencesKey("reader_writing_mode_$bookId")
        context.dataStore.edit { preferences -> preferences[key] = mode.key }
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

    /** 翻页效果："slide" | "continuous" | "scroll" | "fade" | "curl" */
    fun pageTransition(): Flow<String> = context.dataStore.data.map {
        ReaderPageTransition.normalizeKey(it[PAGE_TRANSITION])
    }

    fun txtTocRuleSelection(bookId: String): Flow<String> {
        val key = stringPreferencesKey("txt_toc_rule_$bookId")
        return context.dataStore.data.map { preferences -> preferences[key] ?: "auto" }
    }

    suspend fun saveTxtTocRuleSelection(bookId: String, ruleId: String?) {
        val key = stringPreferencesKey("txt_toc_rule_$bookId")
        context.dataStore.edit { preferences ->
            preferences[key] = ruleId?.takeIf { it.isNotBlank() } ?: "auto"
        }
    }

    fun txtTocCustomRules(): Flow<List<TxtTocRule>> = context.dataStore.data.map { preferences ->
        runCatching { TxtTocRuleCodec.decode(preferences[TXT_TOC_CUSTOM_RULES] ?: "") }
            .getOrDefault(emptyList())
    }.distinctUntilChanged()

    suspend fun saveTxtTocCustomRules(rules: List<TxtTocRule>) {
        require(rules.map { it.id }.distinct().size == rules.size) { "TXT TOC rule IDs must be unique" }
        require(rules.none { TxtTocRuleBuiltIns.byId(it.id) != null }) { "Built-in rule IDs are reserved" }
        rules.forEach { com.huangder.lumibooks.util.parser.TxtTocRuleCompiler.compile(it).getOrThrow() }
        context.dataStore.edit { preferences -> preferences[TXT_TOC_CUSTOM_RULES] = TxtTocRuleCodec.encode(rules) }
    }

    suspend fun exportTxtTocRules(): String = TxtTocRuleCodec.encode(txtTocCustomRules().first())

    /** Validates and merges an imported Lumi JSON rule file. Built-in IDs are never overwritten. */
    suspend fun importTxtTocRules(payload: String): List<TxtTocRule> {
        val imported = TxtTocRuleCodec.decode(payload)
        require(imported.none { TxtTocRuleBuiltIns.byId(it.id) != null }) {
            "Imported rules cannot replace built-in rules"
        }
        val current = txtTocCustomRules().first().associateBy { it.id }.toMutableMap()
        imported.forEach { current[it.id] = it }
        val merged = current.values.sortedBy { it.order }.mapIndexed { index, rule -> rule.copy(order = index) }
        saveTxtTocCustomRules(merged)
        return merged
    }

    suspend fun upsertTxtTocRule(rule: TxtTocRule) {
        val current = txtTocCustomRules().first()
        val updated = (current.filterNot { it.id == rule.id } + rule.copy(origin = com.huangder.lumibooks.util.parser.TxtTocRuleOrigin.CUSTOM))
            .mapIndexed { index, item -> item.copy(order = index) }
        saveTxtTocCustomRules(updated)
    }

    suspend fun deleteTxtTocRule(ruleId: String) {
        val updated = txtTocCustomRules().first().filterNot { it.id == ruleId }
            .mapIndexed { index, item -> item.copy(order = index) }
        saveTxtTocCustomRules(updated)
    }

    suspend fun resolveTxtTocRule(bookId: String): TxtTocRule? {
        val preferences = context.dataStore.data.first()
        val selectedId = preferences[stringPreferencesKey("txt_toc_rule_$bookId")] ?: "auto"
        if (selectedId == "auto") return null
        val builtIn = TxtTocRuleBuiltIns.byId(selectedId)?.takeIf { it.enabled }
        if (builtIn != null) return builtIn
        val custom = runCatching { TxtTocRuleCodec.decode(preferences[TXT_TOC_CUSTOM_RULES] ?: "") }
            .getOrDefault(emptyList())
        return custom.firstOrNull { it.id == selectedId && it.enabled }
    }

    suspend fun savePageTransition(mode: String) {
        context.dataStore.edit { it[PAGE_TRANSITION] = ReaderPageTransition.normalizeKey(mode) }
    }

    suspend fun savePageTransitionDuration(mode: String, durationMs: Int) {
        val normalizedMode = when (mode) {
            ReaderPageAnimationSettings.MODE_SCROLL -> ReaderPageAnimationSettings.MODE_SCROLL
            ReaderPageAnimationSettings.MODE_FADE -> ReaderPageAnimationSettings.MODE_FADE
            ReaderPageAnimationSettings.MODE_CURL -> ReaderPageAnimationSettings.MODE_CURL
            else -> ReaderPageAnimationSettings.MODE_SLIDE
        }
        val key = when (normalizedMode) {
            ReaderPageAnimationSettings.MODE_SCROLL -> PAGE_TRANSITION_SCROLL_DURATION_MS
            ReaderPageAnimationSettings.MODE_FADE -> PAGE_TRANSITION_FADE_DURATION_MS
            ReaderPageAnimationSettings.MODE_CURL -> PAGE_TRANSITION_CURL_DURATION_MS
            else -> PAGE_TRANSITION_SLIDE_DURATION_MS
        }
        context.dataStore.edit { preferences ->
            preferences[key] = ReaderPageAnimationSettings.sanitizeDuration(normalizedMode, durationMs)
        }
    }

    /** 阅读页显示效果："auto" | "day" | "night" */
    fun displayMode(): Flow<String> {
        val key = stringPreferencesKey("reader_display_mode")
        return context.dataStore.data.map { it[key] ?: "auto" }
    }

    suspend fun saveDisplayMode(mode: String) {
        val key = stringPreferencesKey("reader_display_mode")
        context.dataStore.edit { it[key] = mode }
    }

    /** 段间距（dp），默认 2dp */
    fun paragraphSpacing(): Flow<Float> {
        return context.dataStore.data.map { it[PARAGRAPH_SPACING] ?: 2f }
    }

    suspend fun saveParagraphSpacing(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[PARAGRAPH_SPACING] = value
            preferences.updateActiveReaderThemeSuite { copy(paragraphSpacing = value) }
        }
    }

    /** 首行缩进字符数，默认 2 */
    fun firstLineIndent(): Flow<Float> {
        return context.dataStore.data.map { it[FIRST_LINE_INDENT] ?: 2f }
    }

    suspend fun saveFirstLineIndent(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[FIRST_LINE_INDENT] = value
            preferences.updateActiveReaderThemeSuite { copy(firstLineIndent = value) }
        }
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

    suspend fun saveStartupScreen(screen: String) {
        context.dataStore.edit { preferences ->
            preferences[STARTUP_SCREEN] = normalizeStartupScreen(screen)
        }
    }

    suspend fun saveAppIconStyle(style: String): Boolean {
        val normalized = AppIconStyle.normalize(style)
        context.dataStore.edit { preferences ->
            preferences[APP_ICON_STYLE] = normalized
        }
        LaunchThemeController.updateIconStyleSnapshot(context, normalized)
        return LaunchThemeController.applyIconStyle(context, normalized)
    }

    override suspend fun saveTtsProviderSelection(selection: TtsProviderSelection) {
        context.dataStore.edit { preferences ->
            preferences[TTS_PROVIDER_SELECTION] = selection.storedValue
        }
    }

    suspend fun saveAppAccentColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_ACCENT_COLOR] = normalizeAppAccentHex(color)
        }
    }

    suspend fun saveGlobalFontMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[GLOBAL_FONT_MODE] = if (mode == "system") "system" else "default"
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
            preferences[MOTION_PREFERENCE] = if (enabled) "standard" else "reduced"
        }
    }

    suspend fun saveMotionPreference(preference: String) {
        val normalized = if (preference == "reduced") "reduced" else "standard"
        context.dataStore.edit { preferences ->
            preferences[MOTION_PREFERENCE] = normalized
            // Keep older builds and exported settings in sync.
            preferences[ENTRANCE_ANIMATIONS_ENABLED] = normalized == "standard"
        }
    }

    suspend fun saveEInkModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[E_INK_MODE_ENABLED] = enabled
        }
    }

    suspend fun saveTwoPageSpreadEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TWO_PAGE_SPREAD_ENABLED] = enabled
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

    suspend fun saveWelcomeLanguageSetupCompleted(completed: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_WELCOME_LANGUAGE_SETUP] = completed
        }
        LaunchThemeController.updateWelcomeLanguageSetup(context, completed)
    }

    suspend fun completeWelcomeFlow(installTime: Long) {
        context.dataStore.edit { preferences ->
            preferences[HAS_SEEN_WELCOME] = true
            preferences[COMPLETED_WELCOME_INSTALL_TIME] = installTime
        }
        LaunchThemeController.updateWelcomeCompletedInstallTime(context, installTime)
    }

    /** Version of the bundled multi-language Lumi guide already installed in the library. */
    val builtinGuidesSeededVersion: Flow<Int> =
        context.dataStore.data.map { preferences -> preferences[BUILTIN_GUIDES_SEEDED_VERSION] ?: 0 }

    suspend fun markBuiltinGuidesSeeded(version: Int) {
        context.dataStore.edit { preferences -> preferences[BUILTIN_GUIDES_SEEDED_VERSION] = version }
    }

    val builtinGuidesFolderCoverVersion: Flow<Int> =
        context.dataStore.data.map { preferences -> preferences[BUILTIN_GUIDES_FOLDER_COVER_VERSION] ?: 0 }

    suspend fun markBuiltinGuidesFolderCoverSeeded(version: Int) {
        context.dataStore.edit { preferences ->
            preferences[BUILTIN_GUIDES_FOLDER_COVER_VERSION] = version
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

    // 外部 TTS
    suspend fun saveExternalTtsSettings(settings: ExternalTtsSettings) {
        val normalized = settings.normalized()
        context.dataStore.edit { preferences ->
            preferences[EXTERNAL_TTS_ENABLED] = normalized.enabled
            preferences[EXTERNAL_TTS_PROTOCOL] = normalized.protocol.key
            preferences[EXTERNAL_TTS_BASE_URL] = normalized.baseUrl
            preferences[EXTERNAL_TTS_MODEL] = normalized.model
            preferences[EXTERNAL_TTS_VOICE] = normalized.voice
            preferences[EXTERNAL_TTS_STYLE] = normalized.styleInstructions
            preferences[EXTERNAL_TTS_ALLOW_HTTP] = normalized.allowHttp
            preferences[EXTERNAL_TTS_CONSENT_VERSION] = normalized.consentVersion
            preferences[EXTERNAL_TTS_CONSENT_ACCEPTED_AT] = normalized.consentAcceptedAt
            if (normalized.enabled &&
                normalized.consentVersion >= ExternalTtsConfig.CONSENT_VERSION
            ) {
                preferences[TTS_PROVIDER_SELECTION] = TtsProviderSelection.AiModel.storedValue
            }
        }
    }

    suspend fun saveCardOutlinesEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CARD_OUTLINES_ENABLED] = enabled
        }
    }

    suspend fun disableExternalTts() {
        context.dataStore.edit { preferences ->
            preferences[EXTERNAL_TTS_ENABLED] = false
            val selected = TtsProviderSelection.fromStoredValue(preferences[TTS_PROVIDER_SELECTION])
            if (selected == TtsProviderSelection.AiModel) {
                preferences[TTS_PROVIDER_SELECTION] = TtsProviderSelection.SystemDefault.storedValue
            }
        }
    }

    // WebDAV
    suspend fun saveWebdavConfig(config: WebdavConfig) {
        val normalized = config.normalized()
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_ENABLED] = normalized.enabled
            preferences[WEBDAV_SERVER_URL] = normalized.serverUrl
            preferences[WEBDAV_USERNAME] = normalized.username
            preferences[WEBDAV_SYNC_PATH] = normalized.syncPath
            preferences[WEBDAV_LAST_SYNC_TIME] = normalized.lastSyncTime
            preferences[WEBDAV_SYNC_MODE] = normalized.syncMode
            preferences[WEBDAV_SYNC_BOOK_FILES] = normalized.syncBookFiles
            preferences[WEBDAV_SYNC_PROFILE_SETTINGS] = normalized.syncProfileAndSettings
            preferences[WEBDAV_SYNC_LIBRARY_ORGANIZATION] = normalized.syncLibraryOrganization
            preferences[WEBDAV_SYNC_READING_DATA] = normalized.syncReadingData
        }
    }

    suspend fun setWebdavSyncContent(content: WebdavSyncContent, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val key = when (content) {
                WebdavSyncContent.BOOK_FILES -> WEBDAV_SYNC_BOOK_FILES
                WebdavSyncContent.PROFILE_AND_SETTINGS -> WEBDAV_SYNC_PROFILE_SETTINGS
                WebdavSyncContent.LIBRARY_ORGANIZATION -> WEBDAV_SYNC_LIBRARY_ORGANIZATION
                WebdavSyncContent.READING_DATA -> WEBDAV_SYNC_READING_DATA
            }
            preferences[key] = enabled
        }
    }

    suspend fun saveWebdavSyncMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_SYNC_MODE] = mode
        }
    }

    suspend fun disableWebdav() {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_ENABLED] = false
        }
    }

    suspend fun clearWebdavConfig() {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_ENABLED] = false
            preferences.remove(WEBDAV_SERVER_URL)
            preferences.remove(WEBDAV_USERNAME)
            preferences.remove(WEBDAV_SYNC_PATH)
            preferences.remove(WEBDAV_LAST_SYNC_TIME)
            preferences.remove(WEBDAV_SYNCED_BOOK_IDS)
        }
    }

    suspend fun updateWebdavLastSyncTime(timeMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_LAST_SYNC_TIME] = timeMillis
        }
    }

    suspend fun saveWebdavSyncedBookIds(ids: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_SYNCED_BOOK_IDS] = ids.joinToString(",")
        }
    }

    suspend fun saveExternalTtsCacheLimitMb(limitMb: Int) {
        context.dataStore.edit { preferences ->
            preferences[EXTERNAL_TTS_CACHE_LIMIT_MB] = limitMb.coerceIn(
                ExternalTtsConfig.MIN_AUDIO_CACHE_LIMIT_MB,
                ExternalTtsConfig.MAX_AUDIO_CACHE_LIMIT_MB
            )
        }
    }

    override suspend fun saveExternalTtsResumePosition(position: ExternalTtsResumePosition) {
        val key = externalTtsResumeKey(position.bookId)
        val encoded = buildString {
            append("ch=${position.chapterIndex}|pg=${position.pageIndex}|off=${position.characterOffset}")
            if (position.clauseIndex > 0) append("|clause=${position.clauseIndex}")
            position.pageFingerprint?.let { append("|page=$it") }
            position.cacheKey?.let { append("|cache=$it|frame=${position.pcmFrameOffset.coerceAtLeast(0L)}") }
        }
        context.dataStore.edit { preferences ->
            preferences[key] = encoded
        }
    }

    override suspend fun clearExternalTtsResumePosition(bookId: String) {
        val key = externalTtsResumeKey(bookId)
        context.dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    private fun externalTtsResumeKey(bookId: String) =
        stringPreferencesKey("external_tts_resume_$bookId")

    private fun parseResumePosition(raw: String, bookId: String): ExternalTtsResumePosition? {
        return try {
            val parts = raw.split("|").associate { part ->
                val (key, value) = part.split("=", limit = 2)
                key to value
            }
            val pageFingerprint = parts["page"]?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            val cacheKey = parts["cache"]?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            if (parts.containsKey("page") && pageFingerprint == null) return null
            if (parts.containsKey("cache") && cacheKey == null) return null
            ExternalTtsResumePosition(
                bookId = bookId,
                chapterIndex = parts["ch"]?.toIntOrNull() ?: return null,
                pageIndex = parts["pg"]?.toIntOrNull() ?: return null,
                characterOffset = parts["off"]?.toIntOrNull() ?: return null,
                clauseIndex = parts["clause"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                pageFingerprint = pageFingerprint,
                cacheKey = cacheKey,
                pcmFrameOffset = parts["frame"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            )
        } catch (_: Exception) { null }
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

    suspend fun addAuthorizedBookDirectory(uri: String) {
        context.dataStore.edit { preferences ->
            val directories = preferences[AUTHORIZED_BOOK_DIRECTORIES]
                .orEmpty()
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toMutableSet()
            directories += uri
            preferences[AUTHORIZED_BOOK_DIRECTORIES] = directories.sorted().joinToString("\n")
        }
    }

    suspend fun removeAuthorizedBookDirectory(uri: String) {
        context.dataStore.edit { preferences ->
            val directories = preferences[AUTHORIZED_BOOK_DIRECTORIES]
                .orEmpty()
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .filterNot { it == uri }
                .distinct()
                .sorted()
                .toList()
            preferences[AUTHORIZED_BOOK_DIRECTORIES] = directories.joinToString("\n")
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

    /** Acknowledged remote notice IDs; each notice ID is shown only once. */
    val acknowledgedNoticeIds: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[ACKNOWLEDGED_NOTICE_IDS] ?: emptySet()
    }

    /** App update versionCode ignored by the user. Same version will not auto-pop again. */
    val ignoredAppUpdateVersionCode: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[IGNORED_APP_UPDATE_VERSION_CODE] ?: 0L
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

    suspend fun acknowledgeNotice(id: String) {
        val normalized = id.trim()
        if (normalized.isBlank()) return
        context.dataStore.edit { preferences ->
            val existing = preferences[ACKNOWLEDGED_NOTICE_IDS] ?: emptySet()
            preferences[ACKNOWLEDGED_NOTICE_IDS] = existing + normalized
        }
    }

    suspend fun ignoreAppUpdate(versionCode: Long) {
        if (versionCode <= 0L) return
        context.dataStore.edit { preferences ->
            preferences[IGNORED_APP_UPDATE_VERSION_CODE] = versionCode
        }
    }

    private fun readThemeSuites(preferences: Preferences): List<ReaderThemeSuite> {
        val decoded = ReaderThemeSuiteCodec.decode(preferences[READER_THEME_SUITES])
        return ReaderThemeSuites.normalized(
            decoded.ifEmpty { ReaderThemeSuites.defaults() }
        )
    }

    private fun Preferences.toReaderThemeSettings(
        backgroundSelection: String = this[READER_BACKGROUND_SELECTION]
            ?: this[READER_THEME]
            ?: ReaderThemeSuites.DAY_ID
    ) = ReaderThemeSettings(
        backgroundSelection = backgroundSelection,
        backgroundColorSelection = this[READER_BACKGROUND_COLOR_SELECTION]
            ?: resolveLegacyBackgroundColorSelection(backgroundSelection),
        backgroundImageOpacity = this[READER_BACKGROUND_IMAGE_OPACITY] ?: 1f,
        backgroundImageBlurDp = this[READER_BACKGROUND_IMAGE_BLUR_DP] ?: 0f,
        textColor = this[READER_TEXT_COLOR],
        fontSize = this[FONT_SIZE] ?: 16f,
        fontType = this[FONT_TYPE] ?: "system",
        bodyFontWeight = this[BODY_FONT_WEIGHT] ?: 400,
        lineHeight = this[LINE_HEIGHT] ?: 1.5f,
        letterSpacing = this[LETTER_SPACING] ?: 0f,
        textAlignment = ReaderTextAlignment.fromKey(this[TEXT_ALIGNMENT]),
        paragraphSpacing = this[PARAGRAPH_SPACING] ?: 2f,
        firstLineIndent = this[FIRST_LINE_INDENT] ?: 2f,
        marginLeft = this[MARGIN_LEFT] ?: this[MARGIN_HORIZ] ?: 38f,
        marginRight = this[MARGIN_RIGHT] ?: this[MARGIN_HORIZ] ?: 38f,
        marginTop = this[MARGIN_TOP] ?: this[MARGIN_VERT] ?: 64f,
        marginBottom = this[MARGIN_BOTTOM] ?: this[MARGIN_VERT] ?: 64f
    )

    private fun MutablePreferences.updateActiveReaderThemeSuite(
        transform: ReaderThemeSettings.() -> ReaderThemeSettings
    ) {
        if (this[READER_THEME_SUITES] == null) return
        val suites = readThemeSuites(this)
        val activeId = this[ACTIVE_READER_THEME_SUITE_ID]
            ?.takeIf { id -> suites.any { it.id == id } }
            ?: ReaderThemeSuites.DAY_ID
        val updated = suites.map { suite ->
            if (suite.id == activeId) suite.copy(settings = suite.settings.transform()) else suite
        }
        this[READER_THEME_SUITES] = ReaderThemeSuiteCodec.encode(updated)
    }

    suspend fun exportPortablePreferences(deviceId: String): List<PortablePreference> {
        val preferences = context.dataStore.data.first()
        val previous = parsePortablePreferenceMetadata(preferences[PORTABLE_PREFERENCE_METADATA])
        val now = System.currentTimeMillis()
        val current = preferences.asMap()
            .asSequence()
            .filter { (key, _) -> isPortablePreferenceKey(key.name) }
            .mapNotNull { (key, value) -> encodePortablePreference(key.name, value) }
            .associateBy { it.key }

        val result = mutableListOf<PortablePreference>()
        val metadata = JSONObject()
        for ((key, encoded) in current) {
            val fingerprint = portablePreferenceFingerprint(encoded.type, encoded.value)
            val old = previous[key]
            val updatedAt = old?.takeIf { it.fingerprint == fingerprint && !it.deleted }?.updatedAt ?: now
            val owner = old?.takeIf { it.fingerprint == fingerprint && !it.deleted }?.deviceId ?: deviceId
            result += encoded.copy(updatedAt = updatedAt, deviceId = owner)
            metadata.put(key, portablePreferenceMetadataJson(fingerprint, updatedAt, owner, false))
        }
        for ((key, old) in previous) {
            if (key in current || !isPortablePreferenceKey(key)) continue
            val updatedAt = if (old.deleted) old.updatedAt else now
            val owner = if (old.deleted) old.deviceId else deviceId
            result += PortablePreference(key, PortablePreference.TYPE_DELETED, "", updatedAt, owner)
            metadata.put(key, portablePreferenceMetadataJson("", updatedAt, owner, true))
        }
        context.dataStore.edit { it[PORTABLE_PREFERENCE_METADATA] = metadata.toString() }
        return result
    }

    suspend fun applyPortablePreferences(entries: List<PortablePreference>) {
        if (entries.isEmpty()) return
        context.dataStore.edit { preferences ->
            val metadata = JSONObject(preferences[PORTABLE_PREFERENCE_METADATA] ?: "{}")
            for (entry in entries) {
                if (!isPortablePreferenceKey(entry.key)) continue
                applyPortablePreference(preferences, entry)
                val fingerprint = if (entry.deleted) "" else portablePreferenceFingerprint(entry.type, entry.value)
                metadata.put(
                    entry.key,
                    portablePreferenceMetadataJson(
                        fingerprint,
                        entry.updatedAt,
                        entry.deviceId,
                        entry.deleted
                    )
                )
            }
            preferences[PORTABLE_PREFERENCE_METADATA] = metadata.toString()
        }
    }

    suspend fun replacePortablePreferences(entries: List<PortablePreference>, deviceId: String) {
        val incomingKeys = entries.mapTo(mutableSetOf()) { it.key }
        val removals = exportPortablePreferences(deviceId)
            .filterNot { it.key in incomingKeys }
            .map { PortablePreference(it.key, PortablePreference.TYPE_DELETED, "", System.currentTimeMillis(), deviceId) }
        applyPortablePreferences(entries + removals)
    }

    suspend fun readLegacyPortablePreferences(file: File, deviceId: String): List<PortablePreference> {
        if (!file.isFile) return emptyList()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            val legacyStore = PreferenceDataStoreFactory.create(scope = scope) { file }
            val preferences = legacyStore.data.first()
            val timestamp = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
            preferences.asMap().mapNotNull { (key, value) ->
                if (!isPortablePreferenceKey(key.name)) null
                else encodePortablePreference(key.name, value)?.copy(updatedAt = timestamp, deviceId = deviceId)
            }
        } finally {
            scope.cancel()
        }
    }

    private fun encodePortablePreference(key: String, value: Any): PortablePreference? {
        val (type, encoded) = when (value) {
            is Boolean -> PortablePreference.TYPE_BOOLEAN to value.toString()
            is Int -> PortablePreference.TYPE_INT to value.toString()
            is Long -> PortablePreference.TYPE_LONG to value.toString()
            is Float -> PortablePreference.TYPE_FLOAT to value.toString()
            is Double -> PortablePreference.TYPE_DOUBLE to value.toString()
            is String -> PortablePreference.TYPE_STRING to value
            is Set<*> -> PortablePreference.TYPE_STRING_SET to JSONArray(
                value.filterIsInstance<String>().sorted()
            ).toString()
            else -> return null
        }
        return PortablePreference(key, type, encoded, 0L, "")
    }

    private fun applyPortablePreference(preferences: MutablePreferences, entry: PortablePreference) {
        if (entry.deleted) {
            val existing = preferences.asMap().keys.firstOrNull { it.name == entry.key }
            if (existing != null) {
                @Suppress("UNCHECKED_CAST")
                preferences.remove(existing as Preferences.Key<Any>)
            }
            return
        }
        when (entry.type) {
            PortablePreference.TYPE_BOOLEAN -> preferences[booleanPreferencesKey(entry.key)] =
                entry.value.toBooleanStrictOrNull() ?: return
            PortablePreference.TYPE_INT -> preferences[intPreferencesKey(entry.key)] =
                entry.value.toIntOrNull() ?: return
            PortablePreference.TYPE_LONG -> preferences[longPreferencesKey(entry.key)] =
                entry.value.toLongOrNull() ?: return
            PortablePreference.TYPE_FLOAT -> preferences[floatPreferencesKey(entry.key)] =
                entry.value.toFloatOrNull() ?: return
            PortablePreference.TYPE_DOUBLE -> preferences[doublePreferencesKey(entry.key)] =
                entry.value.toDoubleOrNull() ?: return
            PortablePreference.TYPE_STRING -> preferences[stringPreferencesKey(entry.key)] = entry.value
            PortablePreference.TYPE_STRING_SET -> {
                val array = runCatching { JSONArray(entry.value) }.getOrNull() ?: return
                preferences[stringSetPreferencesKey(entry.key)] = buildSet {
                    for (index in 0 until array.length()) add(array.optString(index))
                }
            }
        }
    }

    private fun isPortablePreferenceKey(key: String): Boolean {
        if (key == PORTABLE_PREFERENCE_METADATA.name) return false
        if (key.startsWith("webdav_")) return false
        if (key == "authorized_book_directories") return false
        if (key in nonPortablePreferenceKeys) return false
        return true
    }

    private fun portablePreferenceFingerprint(type: String, value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$type\u0000$value".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private data class PortablePreferenceMetadata(
        val fingerprint: String,
        val updatedAt: Long,
        val deviceId: String,
        val deleted: Boolean
    )

    private fun parsePortablePreferenceMetadata(raw: String?): Map<String, PortablePreferenceMetadata> {
        val root = runCatching { JSONObject(raw ?: "{}") }.getOrElse { JSONObject() }
        return buildMap {
            for (key in root.keys()) {
                val item = root.optJSONObject(key) ?: continue
                put(
                    key,
                    PortablePreferenceMetadata(
                        fingerprint = item.optString("fingerprint"),
                        updatedAt = item.optLong("updatedAt"),
                        deviceId = item.optString("deviceId"),
                        deleted = item.optBoolean("deleted")
                    )
                )
            }
        }
    }

    private fun portablePreferenceMetadataJson(
        fingerprint: String,
        updatedAt: Long,
        deviceId: String,
        deleted: Boolean
    ) = JSONObject().apply {
        put("fingerprint", fingerprint)
        put("updatedAt", updatedAt)
        put("deviceId", deviceId)
        put("deleted", deleted)
    }

    private val nonPortablePreferenceKeys = setOf(
            "accepted_terms_version",
            "accepted_privacy_version",
            "has_checked_update_on_start",
            "acknowledged_notice_ids",
            "ignored_app_update_version_code",
            "has_seen_welcome",
            "completed_welcome_install_time",
            "has_completed_welcome_language_setup",
            "builtin_guides_seeded_version",
            "mineru_consent_version",
            "mineru_consent_accepted_at",
            "external_tts_consent_version",
            "external_tts_consent_accepted_at",
            "preferred_tts_engine",
            "tts_floating_x_fraction",
            "tts_floating_y_fraction",
            "tts_floating_width_dp",
            "tts_floating_height_dp",
            "remote_font_versions"
        )

    private fun Preferences.resolveLegacyBackgroundColorSelection(selection: String): String {
        val preset = ReaderBackgroundPresetCodec.decode(this[CUSTOM_READER_BACKGROUNDS])
            .firstOrNull { it.selectionKey == selection }
        return when (preset?.type) {
            ReaderBackgroundType.COLOR -> selection
            ReaderBackgroundType.IMAGE -> ReaderThemeSuites.DAY_ID
            null -> selection.takeUnless { it.startsWith("custom:") } ?: ReaderThemeSuites.DAY_ID
        }
    }

    private fun MutablePreferences.applyReaderThemeSettings(settings: ReaderThemeSettings) {
        this[FONT_SIZE] = settings.fontSize
        this[LINE_HEIGHT] = settings.lineHeight
        this[LETTER_SPACING] = settings.letterSpacing
        this[TEXT_ALIGNMENT] = settings.textAlignment.key
        this[FONT_TYPE] = settings.fontType
        this[BODY_FONT_WEIGHT] = settings.bodyFontWeight
        this[MARGIN_LEFT] = settings.marginLeft
        this[MARGIN_RIGHT] = settings.marginRight
        this[MARGIN_TOP] = settings.marginTop
        this[MARGIN_BOTTOM] = settings.marginBottom
        this[MARGIN_HORIZ] = (settings.marginLeft + settings.marginRight) / 2f
        this[MARGIN_VERT] = (settings.marginTop + settings.marginBottom) / 2f
        this[PARAGRAPH_SPACING] = settings.paragraphSpacing
        this[FIRST_LINE_INDENT] = settings.firstLineIndent
        this[READER_BACKGROUND_SELECTION] = settings.backgroundSelection
        this[READER_BACKGROUND_COLOR_SELECTION] = settings.backgroundColorSelection
        this[READER_BACKGROUND_IMAGE_OPACITY] = settings.backgroundImageOpacity
        this[READER_BACKGROUND_IMAGE_BLUR_DP] = settings.backgroundImageBlurDp
        this[READER_THEME] = settings.backgroundSelection
            .takeIf { it in ReaderThemeSuites.BUILT_IN_IDS }
            ?: ReaderThemeSuites.DAY_ID
        settings.textColor?.let { this[READER_TEXT_COLOR] = it }
            ?: remove(READER_TEXT_COLOR)

        if (settings.fontType.startsWith("custom:")) {
            val fontId = settings.fontType.removePrefix("custom:")
            CustomFontPresetCodec.decode(this[CUSTOM_FONTS])
                .firstOrNull { it.id == fontId }
                ?.path
                ?.let { this[CUSTOM_FONT_PATH] = it }
        }
    }

    /** 清除所有偏好设置 */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        LaunchThemeController.updateWelcomeSnapshot(
            context = context,
            completedInstallTime = 0L,
            splashEnabled = true,
            hasCompletedLanguageSetup = false
        )
        LaunchThemeController.deferSplashEnabled(context, true)
    }
}
