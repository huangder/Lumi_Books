package com.huangder.lumibooks.ui.settings

import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.domain.model.AppIconStyle

/** 单本书的文件大小明细 */
data class BookSizeItem(
    val bookId: String,
    val title: String,
    val format: String,     // "EPUB" / "PDF" / "TXT"
    val sizeBytes: Long
)

/** 存储空间分解数据 */
data class StorageInfo(
    val isCalculating: Boolean = true,
    val appSizeBytes: Long = 0,
    val cacheSizeBytes: Long = 0,
    val booksSizeBytes: Long = 0,
    val coversSizeBytes: Long = 0,
    val externalTtsCacheSizeBytes: Long = 0,
    val externalTtsCacheLimitMb: Int = com.huangder.lumibooks.tts.ExternalTtsConfig.DEFAULT_AUDIO_CACHE_LIMIT_MB,
    val bookDetails: List<BookSizeItem> = emptyList()
)

/**
 * 更新检查结果（用于 UI 展示）
 */
data class UpdateCheckDisplay(
    val hasAppUpdate: Boolean = false,
    val isForceUpdate: Boolean = false,
    val appVersion: String = "",
    val latestVersionCode: Long = 0L,
    val releaseUrl: String = "",
    val updateTitle: String = "",
    val updateMessage: String = "",
    val changelog: String = "",
    val hasTermsUpdate: Boolean = false,
    val termsVersion: Int = 0,
    val hasPrivacyUpdate: Boolean = false,
    val privacyVersion: Int = 0,
    val isChecking: Boolean = false,
    val isNetworkError: Boolean = false,
    // 控制 Dialog 显示
    val showAppUpdateDialog: Boolean = false,
    val showPolicyUpdateDialog: Boolean = false,
    // 已接受的版本（用于 UI 回写）
    val acceptedTermsVersion: Int = 0,
    val acceptedPrivacyVersion: Int = 0
)

data class SettingsUiState(
    // 个人信息
    val avatarUri: String? = null,
    val nickname: String = "",

    // 阅读设置
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val letterSpacing: Float = 0f,
    val fontType: String = "system",
    val marginHoriz: Float = 38f,
    val marginVert: Float = 64f,

    // 显示与外观
    val appIconStyle: String = AppIconStyle.LUMI_2.storedValue,
    val appTheme: String = "lumi",         // "lumi" / "material3" / "liquid_glass"
    val startupScreen: String = DataStoreManager.DEFAULT_STARTUP_SCREEN,
    val appAccentColor: String = DEFAULT_APP_ACCENT_HEX,
    val globalFontMode: String = "system", // "default" / "system"
    val liquidGlassTransparency: Float = 0.55f,
    val liquidGlassHdrHighlightEnabled: Boolean = false,
    val cardOutlinesEnabled: Boolean = false,
    val darkMode: String = "system",       // "system" / "light" / "dark"
    val motionPreference: String = "standard", // "standard" / "reduced"
    val entranceAnimationsEnabled: Boolean = true,
    val eInkModeEnabled: Boolean = false,
    val twoPageSpreadEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = true,
    val splashEnabled: Boolean = true,
    val readerTheme: String = "day",       // "day" / "night" / "sepia" / "green"

    // MinerU 第三方云解析
    val mineruMode: String = "disabled",   // "disabled" / "agent" / "precise"
    val mineruConsentVersion: Int = 0,
    val mineruHasToken: Boolean = false,

    // 外部 TTS 听书
    val externalTtsSettings: com.huangder.lumibooks.tts.ExternalTtsSettings = com.huangder.lumibooks.tts.ExternalTtsSettings(),
    val externalTtsHasToken: Boolean = false,
    val externalTtsSettingsLoaded: Boolean = false,
    val ttsProviderSelection: com.huangder.lumibooks.tts.TtsProviderSelection =
        com.huangder.lumibooks.tts.TtsProviderSelection.SystemDefault,
    val installedTtsEngines: List<com.huangder.lumibooks.tts.InstalledTtsEngine> = emptyList(),
    val mineruManualImporting: Boolean = false,

    // 语言
    val appLanguage: String = "system",    // "system" / "zh-CN" / "zh-TW" / "zh-HK" / "zh-MO" / "ko" / "ja" / "en"

    // 阅读目标
    val dailyGoal: Int = 30,               // 分钟

    // 存储
    val storageInfo: StorageInfo = StorageInfo(),

    // 备份恢复
    val backupStatus: String = "",     // 操作状态提示
    val backupFailed: Boolean = false,
    val isProcessing: Boolean = false, // 是否正在执行备份/恢复

    // WebDAV 同步
    val webdavConfig: com.huangder.lumibooks.domain.model.WebdavConfig = com.huangder.lumibooks.domain.model.WebdavConfig(),
    val webdavHasToken: Boolean = false,
    val isWebdavSyncing: Boolean = false,
    val webdavSyncResult: String = "",
    val webdavSyncSucceeded: Boolean = true,

    // 检查更新
    val updateCheck: UpdateCheckDisplay = UpdateCheckDisplay(),

    // 阅读设置扩展
    val customHighlightColors: List<String> = emptyList(),
    val customHighlightPalettes: List<com.huangder.lumibooks.domain.model.HighlightPalette> = emptyList(),
    val activeHighlightPaletteId: String? = null,
    val floatingSubtitleSettings: com.huangder.lumibooks.tts.FloatingSubtitleSettings =
        com.huangder.lumibooks.tts.FloatingSubtitleSettings(),
    val bodyFontWeight: Int = 400,
    val applyToBodyOnly: Boolean = false
)
