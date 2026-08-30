package com.huangder.lumibooks.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.model.normalizeAppAccentHex
import com.huangder.lumibooks.domain.model.AppIconStyle
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.huangder.lumibooks.mineru.MineruConfig
import com.huangder.lumibooks.mineru.MineruApiException
import com.huangder.lumibooks.mineru.MineruManualImportManager
import com.huangder.lumibooks.mineru.MineruMode
import com.huangder.lumibooks.tts.ExternalTtsConfig
import com.huangder.lumibooks.tts.ExternalTtsAudioCache
import com.huangder.lumibooks.tts.ExternalTtsEndpointValidator
import com.huangder.lumibooks.tts.ExternalTtsException
import com.huangder.lumibooks.tts.ExternalTtsProtocol
import com.huangder.lumibooks.tts.ExternalTtsSettings
import com.huangder.lumibooks.tts.ExternalTtsEngine
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsEngine
import com.huangder.lumibooks.tts.TtsProviderSelection
import com.huangder.lumibooks.tts.FloatingSubtitleSettings
import com.huangder.lumibooks.service.FloatingSubtitleOverlayController
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.data.backup.BackupArchiveManager

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val bookRepository: BookRepository,
    private val mineruTokenStore: MineruTokenStore,
    private val externalTtsTokenStore: ExternalTtsTokenStore,
    private val externalTtsEngine: ExternalTtsEngine,
    private val ttsEngine: TtsEngine,
    private val ttsController: TtsController,
    private val mineruManualImportManager: MineruManualImportManager,
    private val externalTtsAudioCache: ExternalTtsAudioCache,
    private val webdavSyncManager: com.huangder.lumibooks.data.sync.WebdavSyncManager,
    private val webdavTokenStore: com.huangder.lumibooks.data.local.WebdavTokenStore,
    private val floatingSubtitleOverlayController: FloatingSubtitleOverlayController,
    private val backupArchiveManager: BackupArchiveManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var predictiveBackVisualOverride: Boolean? = null
    private var predictiveBackTransitionJob: Job? = null
    private var externalTtsCacheLimitJob: Job? = null
    private val storageRefreshMutex = Mutex()

    init {
        collectAllPreferences()
        calculateStorageBreakdown()
        refreshInstalledTtsEngines()
    }

    private fun collectAllPreferences() {
        viewModelScope.launch {
            dataStoreManager.avatarUri.collectLatest { uri ->
                _uiState.value = _uiState.value.copy(avatarUri = uri)
            }
        }
        viewModelScope.launch {
            webdavSyncManager.isSyncing.collectLatest { syncing ->
                _uiState.value = _uiState.value.copy(isWebdavSyncing = syncing)
            }
        }
        viewModelScope.launch {
            dataStoreManager.nickname.collectLatest { name ->
                _uiState.value = _uiState.value.copy(nickname = name)
            }
        }
        viewModelScope.launch {
            dataStoreManager.fontSize.collectLatest { value ->
                _uiState.value = _uiState.value.copy(fontSize = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.lineHeight.collectLatest { value ->
                _uiState.value = _uiState.value.copy(lineHeight = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.letterSpacing.collectLatest { value ->
                _uiState.value = _uiState.value.copy(letterSpacing = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.fontType.collectLatest { value ->
                _uiState.value = _uiState.value.copy(fontType = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.marginHoriz.collectLatest { value ->
                _uiState.value = _uiState.value.copy(marginHoriz = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.marginVert.collectLatest { value ->
                _uiState.value = _uiState.value.copy(marginVert = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.appIconStyle.collectLatest { style ->
                _uiState.value = _uiState.value.copy(appIconStyle = style)
            }
        }
        viewModelScope.launch {
            dataStoreManager.appTheme.collectLatest { theme ->
                _uiState.value = _uiState.value.copy(appTheme = theme)
            }
        }
        viewModelScope.launch {
            dataStoreManager.appAccentColor.collectLatest { color ->
                _uiState.value = _uiState.value.copy(appAccentColor = color)
            }
        }
        viewModelScope.launch {
            dataStoreManager.globalFontMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(globalFontMode = mode)
            }
        }
        viewModelScope.launch {
            dataStoreManager.liquidGlassTransparency.collectLatest { transparency ->
                _uiState.value = _uiState.value.copy(liquidGlassTransparency = transparency)
            }
        }
        viewModelScope.launch {
            dataStoreManager.liquidGlassHdrHighlightEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(liquidGlassHdrHighlightEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.cardOutlinesEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(cardOutlinesEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.darkMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(darkMode = mode)
            }
        }
        viewModelScope.launch {
            dataStoreManager.motionPreference.collectLatest { preference ->
                _uiState.value = _uiState.value.copy(motionPreference = preference)
            }
        }
        viewModelScope.launch {
            dataStoreManager.entranceAnimationsEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(entranceAnimationsEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.eInkModeEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(eInkModeEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.twoPageSpreadEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(twoPageSpreadEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.predictiveBackEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(
                    predictiveBackEnabled = predictiveBackVisualOverride ?: enabled
                )
            }
        }
        viewModelScope.launch {
            dataStoreManager.splashEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(splashEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.mineruMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(
                    mineruMode = MineruMode.fromKey(mode).key,
                    mineruHasToken = mineruTokenStore.hasToken()
                )
            }
        }
        viewModelScope.launch {
            dataStoreManager.mineruConsentVersion.collectLatest { version ->
                _uiState.value = _uiState.value.copy(mineruConsentVersion = version)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerTheme.collectLatest { theme ->
                _uiState.value = _uiState.value.copy(readerTheme = theme)
            }
        }
        viewModelScope.launch {
            dataStoreManager.dailyGoal.collectLatest { goal ->
                _uiState.value = _uiState.value.copy(dailyGoal = goal)
            }
        }
        viewModelScope.launch {
            dataStoreManager.acceptedTermsVersion.collectLatest { version ->
                _uiState.value = _uiState.value.copy(
                    updateCheck = _uiState.value.updateCheck.copy(acceptedTermsVersion = version)
                )
            }
        }
        viewModelScope.launch {
            dataStoreManager.acceptedPrivacyVersion.collectLatest { version ->
                _uiState.value = _uiState.value.copy(
                    updateCheck = _uiState.value.updateCheck.copy(acceptedPrivacyVersion = version)
                )
            }
        }
        viewModelScope.launch {
            dataStoreManager.appLanguage.collectLatest { language ->
                _uiState.value = _uiState.value.copy(appLanguage = language)
            }
        }
        viewModelScope.launch {
            dataStoreManager.externalTtsSettings.collectLatest { settings ->
                _uiState.value = _uiState.value.copy(
                    externalTtsSettings = settings,
                    externalTtsHasToken = externalTtsTokenStore.hasToken(),
                    externalTtsSettingsLoaded = true
                )
            }
        }
        viewModelScope.launch {
            dataStoreManager.externalTtsCacheLimitMb.collectLatest { limitMb ->
                _uiState.update { state ->
                    state.copy(
                        storageInfo = state.storageInfo.copy(externalTtsCacheLimitMb = limitMb)
                    )
                }
            }
        }
        viewModelScope.launch {
            dataStoreManager.webdavConfig.collectLatest { config ->
                _uiState.value = _uiState.value.copy(
                    webdavConfig = config,
                    webdavHasToken = webdavTokenStore.hasToken()
                )
            }
        }
        viewModelScope.launch {
            combine(
                dataStoreManager.customHighlightPalettes,
                dataStoreManager.activeHighlightPaletteId
            ) { palettes, activeId -> palettes to activeId }
                .collectLatest { (palettes, activeId) ->
                    val selected = palettes.firstOrNull { it.id == activeId }
                        ?: palettes.firstOrNull()?.takeIf {
                            it.id == "legacy" && (activeId == null || activeId == "legacy")
                    }
                    _uiState.value = _uiState.value.copy(
                        customHighlightColors = selected?.normalizedColors?.filterNotNull().orEmpty(),
                        customHighlightPalettes = palettes,
                        activeHighlightPaletteId = activeId
                    )
                    com.huangder.lumibooks.ui.reader.updateHighlightPalettes(palettes, activeId)
                }
        }
        viewModelScope.launch {
            dataStoreManager.floatingSubtitleSettings.collectLatest { settings ->
                _uiState.value = _uiState.value.copy(floatingSubtitleSettings = settings)
            }
        }
        viewModelScope.launch {
            dataStoreManager.ttsProviderSelection.collectLatest { selection ->
                _uiState.value = _uiState.value.copy(ttsProviderSelection = selection)
            }
        }
        viewModelScope.launch {
            dataStoreManager.bodyFontWeight.collectLatest { bodyFontWeight ->
                _uiState.value = _uiState.value.copy(bodyFontWeight = bodyFontWeight)
            }
        }
        viewModelScope.launch {
            dataStoreManager.applyToBodyOnly.collectLatest { applyToBodyOnly ->
                _uiState.value = _uiState.value.copy(applyToBodyOnly = applyToBodyOnly)
            }
        }
    }

    // ─── 个人信息 ───

    fun saveAvatar(avatarPath: String) {
        viewModelScope.launch {
            dataStoreManager.saveAvatarUri(avatarPath)
            _uiState.value = _uiState.value.copy(avatarUri = avatarPath)
        }
    }

    fun saveNickname(name: String) {
        viewModelScope.launch {
            dataStoreManager.saveNickname(name)
            _uiState.value = _uiState.value.copy(nickname = name)
        }
    }

    fun saveCustomHighlightColors(colors: List<String>) {
        viewModelScope.launch {
            dataStoreManager.saveCustomHighlightColors(colors)
            _uiState.value = _uiState.value.copy(customHighlightColors = colors)
        }
    }

    fun saveCustomHighlightPalettes(
        palettes: List<com.huangder.lumibooks.domain.model.HighlightPalette>
    ) {
        viewModelScope.launch {
            dataStoreManager.saveCustomHighlightPalettes(palettes)
            _uiState.value = _uiState.value.copy(customHighlightPalettes = palettes)
        }
    }

    fun saveActiveHighlightPalette(id: String?) {
        viewModelScope.launch {
            dataStoreManager.saveActiveHighlightPaletteId(id)
            _uiState.value = _uiState.value.copy(activeHighlightPaletteId = id)
        }
    }

    fun saveFloatingSubtitleSettings(settings: FloatingSubtitleSettings) {
        val normalized = settings.normalized()
        _uiState.value = _uiState.value.copy(floatingSubtitleSettings = normalized)
        floatingSubtitleOverlayController.preview(normalized)
        viewModelScope.launch {
            dataStoreManager.saveFloatingSubtitleSettings(normalized)
        }
    }

    fun previewFloatingSubtitleSettings(settings: FloatingSubtitleSettings) {
        floatingSubtitleOverlayController.preview(settings.normalized())
    }

    fun setFloatingSubtitlePreviewActive(active: Boolean) {
        floatingSubtitleOverlayController.setPreviewActive(active)
    }

    fun refreshFloatingSubtitlePermission() {
        floatingSubtitleOverlayController.refreshPermission()
    }

    // ─── 阅读设置 ───

    fun saveFontSize(value: Float) {
        viewModelScope.launch {
            dataStoreManager.saveFontSize(value)
            _uiState.value = _uiState.value.copy(fontSize = value)
        }
    }

    fun saveLineHeight(value: Float) {
        viewModelScope.launch {
            dataStoreManager.saveLineHeight(value)
            _uiState.value = _uiState.value.copy(lineHeight = value)
        }
    }

    fun saveLetterSpacing(value: Float) {
        viewModelScope.launch {
            dataStoreManager.saveLetterSpacing(value)
            _uiState.value = _uiState.value.copy(letterSpacing = value)
        }
    }

    fun saveFontType(value: String) {
        viewModelScope.launch {
            dataStoreManager.saveFontType(value)
            _uiState.value = _uiState.value.copy(fontType = value)
        }
    }

    fun saveMarginHoriz(value: Float) {
        viewModelScope.launch {
            dataStoreManager.saveMarginHoriz(value)
            _uiState.value = _uiState.value.copy(marginHoriz = value)
        }
    }

    fun saveMarginVert(value: Float) {
        viewModelScope.launch {
            dataStoreManager.saveMarginVert(value)
            _uiState.value = _uiState.value.copy(marginVert = value)
        }
    }

    // ─── 显示与外观 ───

    fun saveAppIconStyle(style: String) {
        val normalized = AppIconStyle.normalize(style)
        if (_uiState.value.appIconStyle == normalized) return
        _uiState.value = _uiState.value.copy(appIconStyle = normalized)
        viewModelScope.launch {
            if (!dataStoreManager.saveAppIconStyle(normalized)) {
                Toast.makeText(context, R.string.icon_style_change_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveAppTheme(theme: String) {
        if (_uiState.value.appTheme == theme) return
        _uiState.value = _uiState.value.copy(appTheme = theme)
        viewModelScope.launch {
            dataStoreManager.saveAppTheme(theme)
        }
    }

    fun saveTtsProviderSelection(selection: TtsProviderSelection) {
        viewModelScope.launch {
            if (_uiState.value.ttsProviderSelection != selection) {
                ttsController.stop()
                dataStoreManager.saveTtsProviderSelection(selection)
            }
        }
    }

    fun refreshInstalledTtsEngines() {
        _uiState.value = _uiState.value.copy(
            installedTtsEngines = ttsEngine.getInstalledEngines()
        )
    }

    fun saveAppAccentColor(color: String) {
        val normalized = normalizeAppAccentHex(color)
        if (_uiState.value.appAccentColor == normalized) return
        _uiState.value = _uiState.value.copy(appAccentColor = normalized)
        viewModelScope.launch {
            dataStoreManager.saveAppAccentColor(normalized)
        }
    }

    fun saveGlobalFontMode(mode: String) {
        val normalized = if (mode == "system") "system" else "default"
        if (_uiState.value.globalFontMode == normalized) return
        _uiState.value = _uiState.value.copy(globalFontMode = normalized)
        viewModelScope.launch {
            dataStoreManager.saveGlobalFontMode(normalized)
        }
    }

    fun saveLiquidGlassTransparency(transparency: Float) {
        val clamped = transparency.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(liquidGlassTransparency = clamped)
        viewModelScope.launch {
            dataStoreManager.saveLiquidGlassTransparency(clamped)
        }
    }

    fun previewLiquidGlassTransparency(transparency: Float) {
        _uiState.value = _uiState.value.copy(
            liquidGlassTransparency = transparency.coerceIn(0f, 1f)
        )
    }

    fun saveLiquidGlassHdrHighlightEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(liquidGlassHdrHighlightEnabled = enabled)
        viewModelScope.launch {
            dataStoreManager.saveLiquidGlassHdrHighlightEnabled(enabled)
        }
    }

    fun saveCardOutlinesEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(cardOutlinesEnabled = enabled)
        viewModelScope.launch {
            dataStoreManager.saveCardOutlinesEnabled(enabled)
        }
    }

    fun saveDarkMode(mode: String) {
        if (_uiState.value.darkMode == mode) return
        _uiState.value = _uiState.value.copy(darkMode = mode)
        viewModelScope.launch {
            dataStoreManager.saveDarkMode(mode)
        }
    }

    fun saveBodyFontWeight(weight: Int) {
        viewModelScope.launch {
            dataStoreManager.saveBodyFontWeight(weight)
            _uiState.value = _uiState.value.copy(bodyFontWeight = weight)
        }
    }

    fun saveApplyToBodyOnly(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.saveApplyToBodyOnly(enabled)
            _uiState.value = _uiState.value.copy(applyToBodyOnly = enabled)
        }
    }

    fun saveMotionPreference(preference: String) {
        val normalized = if (preference == "reduced") "reduced" else "standard"
        if (_uiState.value.motionPreference == normalized) return
        _uiState.value = _uiState.value.copy(
            motionPreference = normalized,
            entranceAnimationsEnabled = normalized == "standard"
        )
        viewModelScope.launch {
            dataStoreManager.saveMotionPreference(normalized)
        }
    }

    fun saveEntranceAnimationsEnabled(enabled: Boolean) {
        if (_uiState.value.entranceAnimationsEnabled == enabled) return
        _uiState.value = _uiState.value.copy(entranceAnimationsEnabled = enabled)
        viewModelScope.launch {
            dataStoreManager.saveEntranceAnimationsEnabled(enabled)
        }
    }

    fun saveEInkModeEnabled(enabled: Boolean) {
        if (_uiState.value.eInkModeEnabled == enabled) return
        _uiState.value = _uiState.value.copy(eInkModeEnabled = enabled)
        viewModelScope.launch {
            dataStoreManager.saveEInkModeEnabled(enabled)
        }
    }

    fun saveTwoPageSpreadEnabled(enabled: Boolean) {
        if (_uiState.value.twoPageSpreadEnabled == enabled) return
        _uiState.value = _uiState.value.copy(twoPageSpreadEnabled = enabled)
        viewModelScope.launch {
            dataStoreManager.saveTwoPageSpreadEnabled(enabled)
        }
    }

    fun savePredictiveBackEnabled(enabled: Boolean) {
        if (_uiState.value.predictiveBackEnabled == enabled) return
        predictiveBackTransitionJob?.cancel()
        predictiveBackTransitionJob = viewModelScope.launch {
            if (enabled) {
                // Let the thumb finish moving before predictive callbacks recompose the screen.
                predictiveBackVisualOverride = true
                _uiState.value = _uiState.value.copy(predictiveBackEnabled = true)
                delay(PREDICTIVE_BACK_SWITCH_ANIMATION_MILLIS)
                dataStoreManager.savePredictiveBackEnabled(true)
                predictiveBackVisualOverride = null
            } else {
                // Remove predictive callbacks first, then animate the visual switch one frame later.
                predictiveBackVisualOverride = true
                dataStoreManager.savePredictiveBackEnabled(false)
                delay(PREDICTIVE_BACK_DISABLE_SETTLE_MILLIS)
                predictiveBackVisualOverride = false
                _uiState.value = _uiState.value.copy(predictiveBackEnabled = false)
                delay(PREDICTIVE_BACK_SWITCH_ANIMATION_MILLIS)
                predictiveBackVisualOverride = null
            }
        }
    }

    private companion object {
        const val PREDICTIVE_BACK_SWITCH_ANIMATION_MILLIS = 250L
        const val PREDICTIVE_BACK_DISABLE_SETTLE_MILLIS = 34L
        const val BYTES_PER_MEBIBYTE = 1_048_576L
        // Material's indeterminate indicator needs roughly one cycle to read as intentional.
        const val MIN_STORAGE_REFRESH_VISIBILITY_MS = 1_200L
    }

    fun saveSplashEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.saveSplashEnabled(enabled)
            _uiState.value = _uiState.value.copy(splashEnabled = enabled)
            Toast.makeText(context, R.string.splash_setting_next_launch, Toast.LENGTH_SHORT).show()
        }
    }

    fun enableMineru(mode: MineruMode, token: String? = null, acceptConsent: Boolean = false) {
        require(mode != MineruMode.DISABLED)
        viewModelScope.launch(Dispatchers.IO) {
            if (mode == MineruMode.PRECISE) {
                val normalizedToken = token?.trim().orEmpty()
                if (normalizedToken.isNotEmpty()) {
                    mineruTokenStore.save(normalizedToken)
                }
                if (!mineruTokenStore.hasToken()) return@launch
            }
            if (acceptConsent) {
                dataStoreManager.acceptMineruConsent(MineruConfig.CONSENT_VERSION)
            }
            dataStoreManager.saveMineruMode(mode.key)
            _uiState.value = _uiState.value.copy(
                mineruMode = mode.key,
                mineruConsentVersion = if (acceptConsent) {
                    MineruConfig.CONSENT_VERSION
                } else {
                    _uiState.value.mineruConsentVersion
                },
                mineruHasToken = mineruTokenStore.hasToken()
            )
        }
    }

    fun disableMineru(clearToken: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (clearToken) mineruTokenStore.clear()
            dataStoreManager.disableMineru()
            _uiState.value = _uiState.value.copy(
                mineruMode = MineruMode.DISABLED.key,
                mineruConsentVersion = 0,
                mineruHasToken = mineruTokenStore.hasToken()
            )
        }
    }

    fun clearMineruToken() {
        viewModelScope.launch(Dispatchers.IO) {
            val wasPrecise = _uiState.value.mineruMode == MineruMode.PRECISE.key
            mineruTokenStore.clear()
            if (wasPrecise) {
                dataStoreManager.disableMineru()
            }
            _uiState.value = _uiState.value.copy(
                mineruMode = if (wasPrecise) {
                    MineruMode.DISABLED.key
                } else {
                    _uiState.value.mineruMode
                },
                mineruConsentVersion = if (wasPrecise) {
                    0
                } else {
                    _uiState.value.mineruConsentVersion
                },
                mineruHasToken = false
            )
        }
    }

    // ─── 外部 TTS 听书 ───

    fun updateExternalTtsDraft(settings: ExternalTtsSettings) {
        _uiState.value = _uiState.value.copy(externalTtsSettings = settings)
    }

    fun enableExternalTts(
        settings: ExternalTtsSettings,
        token: String?,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedToken = token?.trim().orEmpty()
            if (normalizedToken.isEmpty() && !externalTtsTokenStore.hasToken()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.external_tts_key_required, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            if (!settings.hasRequiredFields) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.external_tts_required_fields, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val urlResult = ExternalTtsEndpointValidator.validate(settings.baseUrl, settings.allowHttp)
            if (urlResult.isFailure) {
                val msg = when (val error = urlResult.exceptionOrNull()) {
                    is ExternalTtsException.InsecureEndpoint -> context.getString(R.string.external_tts_insecure_endpoint)
                    is ExternalTtsException.InvalidConfiguration -> error.message ?: context.getString(R.string.external_tts_invalid_url)
                    else -> context.getString(R.string.external_tts_invalid_url)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            if (normalizedToken.isNotEmpty()) {
                externalTtsTokenStore.save(normalizedToken)
            }
            val finalSettings = settings.normalized().copy(
                enabled = true,
                consentVersion = ExternalTtsConfig.CONSENT_VERSION,
                consentAcceptedAt = System.currentTimeMillis()
            )
            ttsController.stop()
            dataStoreManager.saveExternalTtsSettings(finalSettings)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    externalTtsSettings = finalSettings,
                    externalTtsHasToken = externalTtsTokenStore.hasToken()
                )
                onSaved()
            }
        }
    }

    fun disableExternalTts(clearKey: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.ttsProviderSelection == TtsProviderSelection.AiModel) {
                ttsController.stop()
            }
            if (clearKey) externalTtsTokenStore.clear()
            dataStoreManager.disableExternalTts()
            _uiState.value = _uiState.value.copy(
                externalTtsSettings = _uiState.value.externalTtsSettings.copy(enabled = false),
                externalTtsHasToken = externalTtsTokenStore.hasToken()
            )
        }
    }

    fun clearExternalTtsToken() {
        viewModelScope.launch(Dispatchers.IO) {
            val wasEnabled = _uiState.value.externalTtsSettings.enabled
            if (_uiState.value.ttsProviderSelection == TtsProviderSelection.AiModel) {
                ttsController.stop()
            }
            externalTtsTokenStore.clear()
            if (wasEnabled) {
                dataStoreManager.disableExternalTts()
            }
            _uiState.value = _uiState.value.copy(
                externalTtsSettings = _uiState.value.externalTtsSettings.copy(enabled = false),
                externalTtsHasToken = false
            )
        }
    }

    fun testExternalTtsConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = externalTtsEngine.testConnection()
            val message = if (result.isSuccess) {
                R.string.external_tts_test_success
            } else {
                R.string.external_tts_test_failed
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun importManualMineruResult(uri: Uri) {
        if (_uiState.value.mineruManualImporting) return
        _uiState.value = _uiState.value.copy(mineruManualImporting = true)
        viewModelScope.launch {
            try {
                val result = mineruManualImportManager.importStandalone(
                    uri = uri,
                    fallbackTitle = context.getString(R.string.mineru_manual_default_title),
                    author = context.getString(R.string.book_author_unknown)
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.mineru_manual_import_success, result.title),
                    Toast.LENGTH_LONG
                ).show()
            } catch (error: MineruApiException) {
                val message = when (error.kind) {
                    MineruApiException.Kind.FILE_LIMIT -> R.string.mineru_manual_import_too_large
                    else -> R.string.mineru_manual_import_invalid
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {
                Toast.makeText(context, R.string.mineru_manual_import_failed, Toast.LENGTH_LONG).show()
            } finally {
                _uiState.value = _uiState.value.copy(mineruManualImporting = false)
            }
        }
    }

    fun saveReaderTheme(theme: String) {
        viewModelScope.launch {
            dataStoreManager.saveReaderTheme(theme)
            _uiState.value = _uiState.value.copy(readerTheme = theme)
        }
    }

    // ─── 语言 ───

    fun saveAppLanguage(language: String) {
        viewModelScope.launch {
            dataStoreManager.saveAppLanguage(language)
            _uiState.value = _uiState.value.copy(appLanguage = language)
        }
    }

    // ─── 阅读目标 ───

    fun saveDailyGoal(minutes: Int) {
        viewModelScope.launch {
            dataStoreManager.saveDailyGoal(minutes)
            _uiState.value = _uiState.value.copy(dailyGoal = minutes)
        }
    }

    // ─── 存储管理 ───

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.listFiles()?.forEach { entry ->
                    if (entry.name != ExternalTtsAudioCache.DIRECTORY_NAME) {
                        entry.deleteRecursively()
                    }
                }
                // 清除图片缓存目录
                try { Coil.imageLoader(context).diskCache?.clear() } catch (_: Exception) { }
                try { Coil.imageLoader(context).memoryCache?.clear() } catch (_: Exception) { }
                refreshStorageBreakdown()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) { }
        }
    }
    fun saveExternalTtsCacheLimitMb(limitMb: Int) {
        val boundedLimitMb = limitMb.coerceIn(
            ExternalTtsConfig.MIN_AUDIO_CACHE_LIMIT_MB,
            ExternalTtsConfig.MAX_AUDIO_CACHE_LIMIT_MB
        )
        _uiState.update { state ->
            state.copy(
                storageInfo = state.storageInfo.copy(externalTtsCacheLimitMb = boundedLimitMb)
            )
        }
        externalTtsCacheLimitJob?.cancel()
        externalTtsCacheLimitJob = viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.saveExternalTtsCacheLimitMb(boundedLimitMb)
            externalTtsAudioCache.trimToLimit(boundedLimitMb.toLong() * BYTES_PER_MEBIBYTE)
            refreshStorageBreakdown()
        }
    }

    fun clearExternalTtsAudioCache() {
        viewModelScope.launch(Dispatchers.IO) {
            externalTtsAudioCache.clear()
            refreshStorageBreakdown()
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 清除缓存
                context.cacheDir.deleteRecursively()
                // 清除内部存储（保留头像）
                val avatarDir = File(context.filesDir, "avatars")
                val avatarBackup = if (avatarDir.exists()) {
                    val backup = File(context.cacheDir, "avatar_backup")
                    avatarDir.copyRecursively(backup, overwrite = true)
                    backup
                } else null

                context.filesDir.listFiles()?.forEach { file ->
                    if (file.name != "avatars") file.deleteRecursively()
                }

                // 恢复头像
                if (avatarBackup != null && avatarBackup.exists()) {
                    avatarBackup.copyRecursively(avatarDir, overwrite = true)
                    avatarBackup.deleteRecursively()
                }

                // 重置加密凭据与 DataStore；先清凭据，确保设置流回填正确状态。
                externalTtsTokenStore.clear()
                dataStoreManager.clearAll()

                refreshStorageBreakdown()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "所有数据已清除", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) { }
        }
    }

    /** 计算存储空间分解：应用本体 + 缓存 + 外部 TTS 音频缓存 + 电子书 + 封面 + 逐本书明细 */
    private fun calculateStorageBreakdown() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshStorageBreakdown()
        }
    }

    private suspend fun refreshStorageBreakdown() = storageRefreshMutex.withLock {
        val refreshStartedAt = System.currentTimeMillis()
        _uiState.update { state ->
            state.copy(storageInfo = state.storageInfo.copy(isCalculating = true))
        }
        try {
            // APK 本体大小
            val appSize = try {
                val appInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                File(appInfo.applicationInfo?.sourceDir ?: "").length()
            } catch (_: Exception) { 0L }

            val cacheSize = getDirSize(context.cacheDir)
            val filesSize = getDirSize(context.filesDir)
            val externalTtsCacheSize = externalTtsAudioCache.sizeBytes()
            val genericCacheSize = (cacheSize + filesSize - externalTtsCacheSize).coerceAtLeast(0L)
            val coversDirSize = getDirSize(FileUtils.getCoversDirectory(context))

            // SAF 与应用内部文件统一取真实大小，并按大小降序展示。
            val bookDetails = bookRepository.getAllBooks().first().map { book ->
                BookSizeItem(
                    bookId = book.id,
                    title = book.title,
                    format = book.format.name,
                    sizeBytes = BookFileAccess.size(context, book.filePath)
                )
            }.sortedByDescending { it.sizeBytes }

            val remaining = MIN_STORAGE_REFRESH_VISIBILITY_MS -
                (System.currentTimeMillis() - refreshStartedAt)
            if (remaining > 0L) delay(remaining)

            _uiState.update { state ->
                state.copy(
                    storageInfo = StorageInfo(
                        isCalculating = false,
                        appSizeBytes = appSize,
                        cacheSizeBytes = genericCacheSize,
                        booksSizeBytes = bookDetails.sumOf { it.sizeBytes },
                        coversSizeBytes = coversDirSize,
                        externalTtsCacheSizeBytes = externalTtsCacheSize,
                        externalTtsCacheLimitMb = state.storageInfo.externalTtsCacheLimitMb,
                        bookDetails = bookDetails
                    )
                )
            }
        } catch (_: Exception) {
            val remaining = MIN_STORAGE_REFRESH_VISIBILITY_MS -
                (System.currentTimeMillis() - refreshStartedAt)
            if (remaining > 0L) delay(remaining)
            _uiState.update { state ->
                state.copy(storageInfo = state.storageInfo.copy(isCalculating = false))
            }
        }
    }

    private fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    // ─── 备份 ───

    suspend fun backup(outputUri: Uri): String {
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            backupStatus = context.getString(R.string.backup_in_progress)
        )
        try {
            val result = backupArchiveManager.create(outputUri)
            val sizeStr = formatFileSize(result.sizeBytes)

            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                backupStatus = context.getString(R.string.backup_complete, sizeStr)
            )
            return sizeStr
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                backupStatus = context.getString(R.string.backup_failed, e.message ?: context.getString(R.string.error))
            )
            throw e
        }
    }

    suspend fun restore(inputUri: Uri) {
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            backupStatus = context.getString(R.string.restore_in_progress)
        )
        try {
            backupArchiveManager.restore(inputUri)
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                backupStatus = context.getString(R.string.restore_success)
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                backupStatus = context.getString(R.string.restore_failed, e.message ?: context.getString(R.string.error))
            )
            throw e
        }
    }

    fun clearBackupStatus() {
        _uiState.value = _uiState.value.copy(backupStatus = "")
    }

    // ─── 检查更新 ──────────────────────────────────────────

    /**
     * 执行完整的更新检查（App版本 + 用户协议 + 隐私政策）。
     * @param isAutoCheck true = 启动时自动检查（静默模式），false = 手动触发
     */
    fun checkUpdate(isAutoCheck: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                updateCheck = _uiState.value.updateCheck.copy(isChecking = true, isNetworkError = false)
            )

            val config = UpdateChecker.fetchUpdateConfig()
            if (config == null) {
                _uiState.value = _uiState.value.copy(
                    updateCheck = _uiState.value.updateCheck.copy(
                        isChecking = false,
                        isNetworkError = true
                    )
                )
                if (!isAutoCheck) {
                    Toast.makeText(context, "网络连接失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val packageInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (_: Exception) { null }
            val currentVersion = packageInfo?.versionName ?: "1.0"
            val currentVersionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

            val state = _uiState.value.updateCheck
            val result = UpdateChecker.evaluate(
                config = config,
                currentVersion = currentVersion,
                currentVersionCode = currentVersionCode,
                acceptedTerms = state.acceptedTermsVersion,
                acceptedPrivacy = state.acceptedPrivacyVersion
            )

            // 决定弹出哪个 Dialog（条款/政策优先于App更新）
            val hasPolicyUpdate = result.hasTermsUpdate || result.hasPrivacyUpdate
            val showAppDialog = result.hasAppUpdate && !hasPolicyUpdate

            _uiState.value = _uiState.value.copy(
                updateCheck = state.copy(
                    hasAppUpdate = result.hasAppUpdate,
                    isForceUpdate = result.isForceUpdate,
                    appVersion = result.appVersion,
                    latestVersionCode = result.latestVersionCode,
                    releaseUrl = result.releaseUrl,
                    updateTitle = result.updateTitle,
                    updateMessage = result.updateMessage,
                    changelog = result.changelog,
                    hasTermsUpdate = result.hasTermsUpdate,
                    termsVersion = result.termsVersion,
                    hasPrivacyUpdate = result.hasPrivacyUpdate,
                    privacyVersion = result.privacyVersion,
                    isChecking = false,
                    // 自动检查时静默弹窗，手动检查时弹窗
                    showPolicyUpdateDialog = hasPolicyUpdate,
                    showAppUpdateDialog = showAppDialog && !isAutoCheck
                )
            )

            if (!isAutoCheck && !result.hasAppUpdate && !hasPolicyUpdate) {
                Toast.makeText(context, R.string.update_already_latest, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 用户同意更新后的条款 */
    fun acceptTermsUpdate(version: Int) {
        viewModelScope.launch {
            dataStoreManager.saveAcceptedTermsVersion(version)
            dismissPolicyUpdateDialog()
        }
    }

    /** 用户同意更新后的隐私政策 */
    fun acceptPrivacyUpdate(version: Int) {
        viewModelScope.launch {
            dataStoreManager.saveAcceptedPrivacyVersion(version)
            dismissPolicyUpdateDialog()
        }
    }

    /** 关闭条款/政策更新 Dialog */
    fun dismissPolicyUpdateDialog() {
        _uiState.value = _uiState.value.copy(
            updateCheck = _uiState.value.updateCheck.copy(showPolicyUpdateDialog = false)
        )
    }

    /** 关闭 App 更新 Dialog */
    fun dismissAppUpdateDialog() {
        _uiState.value = _uiState.value.copy(
            updateCheck = _uiState.value.updateCheck.copy(showAppUpdateDialog = false)
        )
    }

    // ─── WebDAV 同步 ──────────────────────────────────────────────────

    /** Ignore the current update version; the same versionCode will not auto-pop on startup. */
    fun ignoreCurrentAppUpdate() {
        val versionCode = _uiState.value.updateCheck.latestVersionCode
        viewModelScope.launch {
            dataStoreManager.ignoreAppUpdate(versionCode)
            dismissAppUpdateDialog()
        }
    }

    fun enableWebdav(config: com.huangder.lumibooks.domain.model.WebdavConfig, password: String) {
        viewModelScope.launch {
            val previous = _uiState.value.webdavConfig.normalized()
            val next = config.normalized()
            val libraryChanged = previous.serverUrl.isNotBlank() &&
                (previous.serverUrl != next.serverUrl ||
                    previous.username != next.username ||
                    previous.syncPath != next.syncPath)
            if (libraryChanged) webdavSyncManager.detachLibrary(previous)
            if (password.isNotBlank()) {
                webdavTokenStore.save(password)
            }
            dataStoreManager.saveWebdavConfig(config.copy(enabled = true))
        }
    }

    fun disableWebdav(clearKey: Boolean = false) {
        viewModelScope.launch {
            dataStoreManager.disableWebdav()
            if (clearKey) {
                webdavTokenStore.clear()
            }
        }
    }

    fun clearWebdavConfig() {
        viewModelScope.launch {
            webdavSyncManager.detachLibrary(_uiState.value.webdavConfig)
            webdavTokenStore.clear()
            dataStoreManager.clearWebdavConfig()
        }
    }

    fun testWebdavConnection() {
        viewModelScope.launch {
            val config = _uiState.value.webdavConfig.normalized()
            val password = webdavTokenStore.read() ?: ""
            if (config.serverUrl.isBlank() || password.isBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.webdav_required_fields, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val result = webdavSyncManager.testConnection(config.serverUrl, config.username, password)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (result.success) R.string.webdav_test_success else R.string.webdav_test_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun saveWebdavSyncMode(mode: String) {
        viewModelScope.launch {
            dataStoreManager.saveWebdavSyncMode(mode)
        }
    }

    fun setWebdavSyncContent(
        content: com.huangder.lumibooks.domain.model.WebdavSyncContent,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            dataStoreManager.setWebdavSyncContent(content, enabled)
        }
    }

    fun syncWebdavNow() {
        viewModelScope.launch {
            val config = _uiState.value.webdavConfig
            if (!config.enabled) return@launch
            _uiState.update { it.copy(webdavSyncResult = "", webdavSyncSucceeded = true) }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.webdav_syncing, Toast.LENGTH_SHORT).show()
            }
            val result = webdavSyncManager.fullSync()
            _uiState.update {
                it.copy(webdavSyncResult = result.message, webdavSyncSucceeded = result.success)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

}
