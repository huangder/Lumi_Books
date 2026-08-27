package com.huangder.lumibooks.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderSettingsPreviewUiState(
    val suites: List<ReaderThemeSuite> = ReaderThemeSuites.defaults(),
    val activeSuiteId: String = ReaderThemeSuites.DAY_ID,
    val editingSuiteId: String? = null,
    val backgrounds: List<ReaderBackgroundPreset> = emptyList(),
    val customFonts: List<CustomFontPreset> = emptyList(),
    val animationSettings: ReaderPageAnimationSettings = ReaderPageAnimationSettings(),
    val animationMode: String = ReaderPageAnimationSettings.MODE_SLIDE,
    val eInkMode: Boolean = false
) {
    val editingSuite: ReaderThemeSuite?
        get() = suites.firstOrNull { it.id == editingSuiteId }
}

@HiltViewModel
class ReaderSettingsPreviewViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderSettingsPreviewUiState())
    val uiState: StateFlow<ReaderSettingsPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { dataStoreManager.migrateReaderThemeSuites() }
        viewModelScope.launch {
            dataStoreManager.readerThemeSuiteState.collectLatest { state ->
                _uiState.update { it.copy(suites = state.suites, activeSuiteId = state.activeSuiteId) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.customReaderBackgrounds.collectLatest { backgrounds ->
                _uiState.update { it.copy(backgrounds = backgrounds) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.customFonts.collectLatest { fonts ->
                _uiState.update { it.copy(customFonts = fonts) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerPageAnimationSettings.collectLatest { settings ->
                _uiState.update { it.copy(animationSettings = settings) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.pageTransition().collectLatest { mode ->
                if (mode in setOf("slide", "fade", "curl")) {
                    _uiState.update { it.copy(animationMode = mode) }
                }
            }
        }
        viewModelScope.launch {
            dataStoreManager.eInkModeEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(eInkMode = enabled) }
            }
        }
    }

    fun beginEditing(suiteId: String) {
        if (_uiState.value.suites.any { it.id == suiteId }) {
            _uiState.update { it.copy(editingSuiteId = suiteId) }
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editingSuiteId = null) }
    }

    fun createTheme(name: String) {
        val normalized = name.trim()
        if (normalized.isBlank()) return
        val suite = ReaderThemeSuites.newCustom(UUID.randomUUID().toString(), normalized)
        viewModelScope.launch {
            val state = _uiState.value
            dataStoreManager.saveReaderThemeSuiteState(
                suites = state.suites + suite,
                activeSuiteId = state.activeSuiteId,
                applyActiveSuite = false
            )
            _uiState.update { it.copy(editingSuiteId = suite.id) }
        }
    }

    fun updateTheme(settings: ReaderThemeSettings) {
        val suiteId = _uiState.value.editingSuiteId ?: return
        _uiState.update { state ->
            state.copy(
                suites = state.suites.map { suite ->
                    if (suite.id == suiteId) suite.copy(settings = settings) else suite
                }
            )
        }
        viewModelScope.launch { dataStoreManager.updateReaderThemeSuite(suiteId, settings) }
    }

    fun renameTheme(suiteId: String, name: String) {
        viewModelScope.launch { dataStoreManager.renameReaderThemeSuite(suiteId, name) }
    }

    fun deleteTheme(suiteId: String) {
        val state = _uiState.value
        val suite = state.suites.firstOrNull { it.id == suiteId } ?: return
        if (suite.isBuiltIn) return
        val updated = state.suites.filterNot { it.id == suiteId }
        val activeId = if (state.activeSuiteId == suiteId) ReaderThemeSuites.DAY_ID else state.activeSuiteId
        viewModelScope.launch {
            dataStoreManager.saveReaderThemeSuiteState(
                suites = updated,
                activeSuiteId = activeId,
                applyActiveSuite = state.activeSuiteId == suiteId
            )
            _uiState.update { current ->
                current.copy(editingSuiteId = current.editingSuiteId.takeUnless { it == suiteId })
            }
        }
    }

    fun moveTheme(suiteId: String, delta: Int) {
        val state = _uiState.value
        val from = state.suites.indexOfFirst { it.id == suiteId }
        if (from < 0) return
        val to = (from + delta).coerceIn(state.suites.indices)
        if (from == to) return
        val reordered = state.suites.toMutableList().apply { add(to, removeAt(from)) }
        _uiState.update { it.copy(suites = reordered) }
        viewModelScope.launch {
            dataStoreManager.saveReaderThemeSuiteState(
                reordered,
                state.activeSuiteId,
                applyActiveSuite = false
            )
        }
    }

    fun setActiveTheme(suiteId: String) {
        viewModelScope.launch { dataStoreManager.setActiveReaderThemeSuite(suiteId) }
    }

    fun setAnimationMode(mode: String) {
        if (mode !in setOf("slide", "fade", "curl") || _uiState.value.eInkMode) return
        _uiState.update { it.copy(animationMode = mode) }
        viewModelScope.launch { dataStoreManager.savePageTransition(mode) }
    }

    fun setAnimationDuration(mode: String, durationMs: Int) {
        val updated = _uiState.value.animationSettings.withDuration(mode, durationMs)
        _uiState.update { it.copy(animationSettings = updated) }
        viewModelScope.launch { dataStoreManager.savePageTransitionDuration(mode, durationMs) }
    }

    fun addBackgroundColor(colorHex: String) {
        val normalized = colorHex.trim().let { if (it.startsWith('#')) it else "#$it" }
        val color = runCatching { android.graphics.Color.parseColor(normalized) }.getOrNull() ?: return
        val preset = ReaderBackgroundPreset(
            id = UUID.randomUUID().toString(),
            type = ReaderBackgroundType.COLOR,
            value = String.format("#%08X", color)
        )
        val backgrounds = _uiState.value.backgrounds + preset
        viewModelScope.launch {
            dataStoreManager.saveCustomReaderBackgrounds(backgrounds)
            selectBackgroundColor(preset.selectionKey)
        }
    }

    fun addBackgroundPhoto(uri: Uri) {
        val suiteId = _uiState.value.editingSuiteId ?: return
        viewModelScope.launch {
            val preset = withContext(Dispatchers.IO) {
                val directory = File(context.filesDir, "reader_backgrounds").apply { mkdirs() }
                val id = UUID.randomUUID().toString()
                val target = File(directory, "$id.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                ReaderBackgroundPreset(id, ReaderBackgroundType.IMAGE, target.absolutePath)
            } ?: return@launch
            val state = _uiState.value
            dataStoreManager.saveCustomReaderBackgrounds(state.backgrounds + preset)
            val suite = state.suites.firstOrNull { it.id == suiteId } ?: return@launch
            dataStoreManager.updateReaderThemeSuite(
                suiteId,
                suite.settings.copy(backgroundSelection = preset.selectionKey)
            )
        }
    }

    fun removeBackgroundPhoto() {
        val suite = _uiState.value.editingSuite ?: return
        val preset = _uiState.value.backgrounds.firstOrNull {
            it.selectionKey == suite.settings.backgroundSelection
        }
        val updatedSettings = suite.settings.copy(
            backgroundSelection = suite.settings.backgroundColorSelection,
            backgroundImageOpacity = 1f,
            backgroundImageBlurDp = 0f
        )
        viewModelScope.launch {
            dataStoreManager.updateReaderThemeSuite(suite.id, updatedSettings)
            val usedByAnotherSuite = preset != null && _uiState.value.suites.any {
                it.id != suite.id && it.settings.backgroundSelection == preset.selectionKey
            }
            if (preset?.type == ReaderBackgroundType.IMAGE && !usedByAnotherSuite) {
                dataStoreManager.saveCustomReaderBackgrounds(
                    _uiState.value.backgrounds.filterNot { it.id == preset.id }
                )
                withContext(Dispatchers.IO) { runCatching { File(preset.value).delete() } }
            }
        }
    }

    fun selectBackgroundColor(selection: String) {
        val suite = _uiState.value.editingSuite ?: return
        val currentIsImage = _uiState.value.backgrounds.any {
            it.selectionKey == suite.settings.backgroundSelection &&
                it.type == ReaderBackgroundType.IMAGE
        }
        val updatedSettings = suite.settings.copy(
            backgroundSelection = if (currentIsImage) {
                suite.settings.backgroundSelection
            } else {
                selection
            },
            backgroundColorSelection = selection
        )
        _uiState.update { state ->
            state.copy(
                suites = state.suites.map {
                    if (it.id == suite.id) it.copy(settings = updatedSettings) else it
                }
            )
        }
        viewModelScope.launch {
            dataStoreManager.updateReaderThemeSuite(
                suite.id,
                updatedSettings
            )
        }
    }
}
