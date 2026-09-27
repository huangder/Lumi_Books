package com.huangder.lumibooks.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.domain.model.LumiThemeBundleCodec
import com.huangder.lumibooks.util.ReaderBackgroundImageProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.json.JSONObject
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
    val eInkMode: Boolean = false,
    /** Which layout bucket the editor is touching. */
    val editingLayout: ReaderLayoutTarget = ReaderLayoutTarget.READER_LAYOUT
    , val editingDark: Boolean = false
) {
    val editingSuite: ReaderThemeSuite?
        get() = suites.firstOrNull { it.id == editingSuiteId }

    val editingSettings: ReaderThemeSettings?
        get() = editingSuite?.settingsFor(editingLayout, editingDark)
}

data class ReaderThemeImportReport(
    val importedCount: Int,
    val missingBackgroundImages: List<String> = emptyList()
)

@HiltViewModel
class ReaderSettingsPreviewViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderSettingsPreviewUiState())
    val uiState: StateFlow<ReaderSettingsPreviewUiState> = _uiState.asStateFlow()
    private var themeUpdateJob: Job? = null

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
                // The settings screen can receive the background list after a slider
                // change has already persisted the theme. Reconcile that state here so
                // a blurred copy is still produced instead of leaving the reader on
                // the original image.
                val editingSettings = _uiState.value.editingSettings ?: return@collectLatest
                val prepared = withContext(Dispatchers.IO) {
                    prepareProcessedBackground(editingSettings, backgrounds)
                }
                if (prepared != backgrounds) {
                    dataStoreManager.saveCustomReaderBackgrounds(prepared)
                    _uiState.update { it.copy(backgrounds = prepared) }
                }
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
                if (mode in setOf("slide", "scroll", "fade", "curl")) {
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

    fun selectEditingLayout(layout: ReaderLayoutTarget) {
        if (_uiState.value.editingLayout == layout) return
        _uiState.update { it.copy(editingLayout = layout) }
    }

    fun selectEditingMode(dark: Boolean) {
        _uiState.update { it.copy(editingDark = dark) }
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

    fun previewTheme(settings: ReaderThemeSettings) {
        val suiteId = _uiState.value.editingSuiteId ?: return
        val layout = _uiState.value.editingLayout
        _uiState.update { state ->
            state.copy(
                suites = state.suites.map { suite ->
                    if (suite.id == suiteId) {
                        if (layout == ReaderLayoutTarget.READER_LAYOUT) suite.withModeSettings(_uiState.value.editingDark, settings)
                        else suite.withSettings(layout, settings)
                    } else suite
                }
            )
        }
    }

    fun updateTheme(settings: ReaderThemeSettings) {
        val suiteId = _uiState.value.editingSuiteId ?: return
        val layout = _uiState.value.editingLayout
        previewTheme(settings)
        themeUpdateJob?.cancel()
        themeUpdateJob = viewModelScope.launch {
            val availableBackgrounds = _uiState.value.backgrounds.ifEmpty {
                dataStoreManager.customReaderBackgrounds.first()
            }
            val backgrounds = withContext(Dispatchers.IO) {
                prepareProcessedBackground(settings, availableBackgrounds)
            }
            val latestSuite = _uiState.value.suites.firstOrNull { it.id == suiteId }
            if (_uiState.value.editingSuiteId != suiteId ||
                latestSuite?.settingsFor(layout, _uiState.value.editingDark) != settings
            ) {
                return@launch
            }
            if (backgrounds != availableBackgrounds) {
                dataStoreManager.saveCustomReaderBackgrounds(backgrounds)
                _uiState.update { it.copy(backgrounds = backgrounds) }
            }
            dataStoreManager.updateReaderThemeSuite(
                suiteId,
                layout,
                settings,
                modeDark = _uiState.value.editingDark.takeIf { layout == ReaderLayoutTarget.READER_LAYOUT }
            )
        }
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

    fun exportThemeBundle(uri: Uri, suiteId: String, onResult: (Result<Unit>) -> Unit = {}) {
        val suite = _uiState.value.suites.firstOrNull { it.id == suiteId }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                requireNotNull(suite) { "Theme no longer exists" }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                        writer.write(LumiThemeBundleCodec.encode(listOf(suite)))
                    }
                } ?: error("Unable to open export file")
            }
            withContext(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    fun importThemeBundle(uri: Uri, onResult: (Result<ReaderThemeImportReport>) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            var stagedDirectory: File? = null
            val previousBackgrounds = _uiState.value.backgrounds
            val previousFonts = _uiState.value.customFonts
            val missingBackgroundImages = linkedSetOf<String>()
            val result: Result<Int> = try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to open theme file")
                val imported = decodeThemeInput(
                    bytes,
                    onStagedDirectory = { directory -> stagedDirectory = directory },
                    onMissingBackgroundImage = { imageName -> missingBackgroundImages += imageName }
                )
                require(imported.isNotEmpty()) { "Theme bundle is empty" }
                // The external ZIP importer persists image/font assets before returning. Wait
                // until every custom selection referenced by the imported light/dark settings
                // is visible in DataStore before publishing the suite to the editor. A plain
                // first() can observe the collector's initial value and make the preview render
                // the day fallback while the image presets are still being persisted.
                val requiredBackgroundKeys = imported
                    .flatMap { suite ->
                        buildList {
                            add(suite.settings)
                            suite.lightSettings?.let(::add)
                            suite.darkSettings?.let(::add)
                            add(suite.bookLayoutSettings)
                        }
                    }
                    .flatMap { settings ->
                        listOf(settings.backgroundSelection, settings.backgroundColorSelection)
                    }
                    .filter { it.startsWith("custom:") }
                    .toSet()
                val persistedBackgrounds = if (stagedDirectory != null) {
                    dataStoreManager.customReaderBackgrounds.first { backgrounds ->
                        requiredBackgroundKeys.all { key -> backgrounds.any { it.selectionKey == key } }
                    }
                } else {
                    // A JSON-only LUMI export may intentionally reference no local assets.
                    // Do not wait forever for custom presets that the file does not contain.
                    dataStoreManager.customReaderBackgrounds.first()
                }
                val persistedFonts = dataStoreManager.customFonts.first()
                val state = _uiState.value.copy(
                    backgrounds = persistedBackgrounds,
                    customFonts = persistedFonts
                )
                val existingIds = state.suites.mapTo(mutableSetOf()) { it.id }
                val existingNames = state.suites.mapNotNull { it.customName }.mapTo(mutableSetOf()) { it.lowercase() }
                val copies = imported.map { suite ->
                    val baseName = suite.customName?.takeIf(String::isNotBlank) ?: "LUMI Theme"
                    var name = baseName
                    var suffix = 1
                    while (name.lowercase() in existingNames) {
                        name = if (suffix == 1) context.getString(R.string.theme_bundle_copy_name, baseName)
                        else context.getString(R.string.theme_bundle_copy_name_number, baseName, suffix)
                        suffix++
                    }
                    existingNames += name.lowercase()
                    var id = UUID.randomUUID().toString()
                    while (!existingIds.add(id)) id = UUID.randomUUID().toString()
                    suite.copy(id = id, customName = name)
                }
                val merged = ReaderThemeSuites.normalized(state.suites + copies)
                dataStoreManager.saveReaderThemeSuiteState(merged, state.activeSuiteId, applyActiveSuite = false)
                _uiState.value = state.copy(suites = merged)
                Result.success(copies.size)
            } catch (error: Throwable) {
                // External bundles may have staged their assets before the theme
                // state is written. Restore the previous lists if any later step
                // fails so a rejected file cannot leave partial preferences.
                runCatching { dataStoreManager.saveCustomReaderBackgrounds(previousBackgrounds) }
                runCatching { dataStoreManager.saveCustomFonts(previousFonts) }
                _uiState.update { it.copy(backgrounds = previousBackgrounds, customFonts = previousFonts) }
                Result.failure(error)
            }
            if (result.isFailure) {
                stagedDirectory?.deleteRecursively()
            }
            val reportResult = result.map { ReaderThemeImportReport(it, missingBackgroundImages.toList()) }
            withContext(Dispatchers.Main.immediate) { onResult(reportResult) }
        }
    }

    /**
     * Accepts our portable LUMI JSON and ZIP files, plus the common external
     * reader theme ZIP shape (`readConfig.json` + image/font resources).
     */
    private suspend fun decodeThemeInput(
        bytes: ByteArray,
        onStagedDirectory: (File) -> Unit,
        onMissingBackgroundImage: (String) -> Unit
    ): List<ReaderThemeSuite> {
        val text = bytes.toString(StandardCharsets.UTF_8)
        if (text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) {
            return LumiThemeBundleCodec.decode(text)
        }

        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream().buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry!!.isDirectory) {
                    val name = entry!!.name.replace('\\', '/')
                    require(name.length <= 512) { "Invalid ZIP entry name" }
                    val content = zip.readBytes()
                    require(content.size <= MAX_THEME_ASSET_BYTES) { "Theme asset is too large" }
                    entries[name] = content
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(entries.isNotEmpty()) { "Theme ZIP is empty" }

        val lumiJson = entries.entries.firstOrNull { (name, content) ->
            name.substringAfterLast('/').equals("lumi-theme.json", ignoreCase = true) ||
                name.substringAfterLast('/').equals("lumi-theme-bundle.json", ignoreCase = true) ||
                (name.lowercase().endsWith(".json") &&
                    content.toString(StandardCharsets.UTF_8).contains("\"lumi-theme-bundle\""))
        }?.value
        if (lumiJson != null) {
            return LumiThemeBundleCodec.decode(lumiJson.toString(StandardCharsets.UTF_8))
        }

        val configEntry = entries.entries.firstOrNull {
            it.key.substringAfterLast('/').equals("readConfig.json", ignoreCase = true)
        } ?: error("ZIP 中未找到 LUMI 主题文件或 readConfig.json")
        val stage = File(context.filesDir, "theme_imports/${UUID.randomUUID()}").apply { mkdirs() }
        onStagedDirectory(stage)
        return importExternalThemeZip(
            JSONObject(configEntry.value.toString(StandardCharsets.UTF_8)),
            entries,
            stage,
            onMissingBackgroundImage
        )
    }

    private suspend fun importExternalThemeZip(
        config: JSONObject,
        entries: Map<String, ByteArray>,
        stage: File,
        onMissingBackgroundImage: (String) -> Unit
    ): List<ReaderThemeSuite> {
        // The settings screen collector can lag behind a previous import or a change made
        // elsewhere in the app. Read the durable lists first so this import appends resources
        // instead of replacing them with a stale UI snapshot.
        val backgrounds = dataStoreManager.customReaderBackgrounds.first().toMutableList()
        val fonts = dataStoreManager.customFonts.first().toMutableList()
        val assetLookup = entries.entries.associateBy { it.key.substringAfterLast('/').lowercase() }
        val sharedFontType = config.optString("textFont").takeIf { it.isNotBlank() }?.let { fontName ->
            val asset = assetLookup[fontName.substringAfterLast('/').lowercase()]
                ?: error("ZIP 中未找到字体文件：$fontName")
            val id = UUID.randomUUID().toString().replace("-", "").take(12)
            val extension = fontName.substringAfterLast('.').lowercase().takeIf { it.length in 2..5 } ?: "ttf"
            val target = File(stage, "font_$id.$extension")
            target.writeBytes(asset.value)
            val preset = CustomFontPreset(id = id, path = target.absolutePath, name = fontName.substringAfterLast('/'))
            fonts += preset
            "custom:$id"
        } ?: "system"

        fun settingsFor(dark: Boolean): ReaderThemeSettings {
            val imageName = config.optString(if (dark) "bgStrNight" else "bgStr")
            val bgType = config.optInt(if (dark) "bgTypeNight" else "bgType", config.optInt("bgType", 0))
            val imageKey = imageName.substringAfterLast('/').lowercase()
            // The common readConfig format uses 1 for an image background. Some
            // exporters use 2, so retain that compatibility too. A filename
            // extension is also enough to recognize an image when the type flag
            // is missing or incorrect.
            val isImage = bgType == 1 || bgType == 2 ||
                assetLookup.containsKey(imageKey) ||
                imageKey.substringAfterLast('.', "").lowercase() in setOf(
                    "jpg", "jpeg", "png", "webp", "gif", "bmp"
                )
            val textColor = parseExternalColor(
                config.optString(if (dark) "textColorNight" else "textColor")
            )
            val opacity = (config.optDouble("bgAlpha", 100.0) / 100.0).toFloat().coerceIn(0f, 1f)
            val base = if (isImage) {
                val asset = assetLookup[imageKey]
                if (asset == null) {
                    if (imageName.isNotBlank()) onMissingBackgroundImage(imageName)
                    // Keep the theme import usable when an exporter omitted its
                    // referenced image. Both light and dark modes use LUMI's
                    // default daytime background in this case.
                    ReaderThemeSuites.DAY_ID to ReaderThemeSuites.DAY_ID
                } else {
                val imageId = UUID.randomUUID().toString()
                val extension = imageName.substringAfterLast('.').lowercase().takeIf { it.length in 2..5 } ?: "jpg"
                val target = File(stage, "background_$imageId.$extension")
                target.writeBytes(asset.value)
                val imagePreset = ReaderBackgroundPreset(
                    id = imageId,
                    type = ReaderBackgroundType.IMAGE,
                    value = target.absolutePath,
                    name = imageName.substringAfterLast('/')
                )
                backgrounds += imagePreset
                val fallbackId = UUID.randomUUID().toString()
                val fallbackColor = if (dark) {
                    parseExternalColor(config.optString("bgStrEInk")) ?: 0xFF1A1A1A.toInt()
                } else {
                    0xFFFBFBFC.toInt()
                }
                backgrounds += ReaderBackgroundPreset(
                    id = fallbackId,
                    type = ReaderBackgroundType.COLOR,
                    value = String.format("#%08X", fallbackColor),
                    name = "${imageName.substringAfterLast('/')} fallback"
                )
                imagePreset.selectionKey to "custom:$fallbackId"
                }
            } else {
                val color = parseExternalColor(imageName)
                    ?: if (dark) 0xFF1A1A1A.toInt() else 0xFFFBFBFC.toInt()
                val colorId = UUID.randomUUID().toString()
                backgrounds += ReaderBackgroundPreset(
                    id = colorId,
                    type = ReaderBackgroundType.COLOR,
                    value = String.format("#%08X", color),
                    name = if (dark) "Imported dark" else "Imported light"
                )
                "custom:$colorId" to "custom:$colorId"
            }
            val (backgroundSelection, backgroundColorSelection) = base
            return ReaderThemeSettings(
                backgroundSelection = backgroundSelection,
                backgroundColorSelection = backgroundColorSelection,
                backgroundImageOpacity = opacity,
                textColor = textColor,
                fontSize = config.optDouble("textSize", 16.0).toFloat(),
                fontType = sharedFontType,
                bodyFontWeight = config.optInt("textBold", 400).coerceIn(100, 900),
                lineHeight = (1.35f + config.optDouble("lineSpacingExtra", 0.0).toFloat() /
                    config.optDouble("textSize", 16.0).toFloat().coerceAtLeast(1f)).coerceIn(1f, 2.5f),
                letterSpacing = config.optDouble("letterSpacing", 0.0).toFloat(),
                paragraphSpacing = config.optDouble("paragraphSpacing", 2.0).toFloat(),
                firstLineIndent = if (config.optString("paragraphIndent").isNotBlank()) 2f else 0f,
                marginLeft = config.optDouble("paddingLeft", 38.0).toFloat(),
                marginRight = config.optDouble("paddingRight", 38.0).toFloat(),
                marginTop = config.optDouble("paddingTop", 64.0).toFloat(),
                marginBottom = config.optDouble("paddingBottom", 64.0).toFloat()
            )
        }

        val light = settingsFor(dark = false)
        val dark = settingsFor(dark = true)
        dataStoreManager.saveCustomReaderBackgrounds(backgrounds)
        dataStoreManager.saveCustomFonts(fonts)
        _uiState.update { it.copy(backgrounds = backgrounds, customFonts = fonts) }
        return listOf(
            ReaderThemeSuite(
                id = UUID.randomUUID().toString(),
                customName = config.optString("name").trim().ifBlank { "Imported theme" },
                settings = light,
                lightSettings = light,
                darkSettings = dark
            )
        )
    }

    private fun parseExternalColor(value: String): Int? {
        val candidate = value.trim().takeIf { it.isNotBlank() } ?: return null
        return runCatching { android.graphics.Color.parseColor(candidate) }.getOrNull()
    }

    private companion object {
        const val MAX_THEME_ASSET_BYTES = 32 * 1024 * 1024
    }

    fun setAnimationMode(mode: String) {
        if (mode !in setOf("slide", "scroll", "fade", "curl") || _uiState.value.eInkMode) return
        _uiState.update { it.copy(animationMode = mode) }
        viewModelScope.launch { dataStoreManager.savePageTransition(mode) }
    }

    fun previewAnimationDuration(mode: String, durationMs: Int) {
        val updated = _uiState.value.animationSettings.withDuration(mode, durationMs)
        _uiState.update { it.copy(animationSettings = updated) }
    }

    fun setAnimationDuration(mode: String, durationMs: Int) {
        previewAnimationDuration(mode, durationMs)
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
            val layout = state.editingLayout
            val currentSettings = suite.settingsFor(
                layout,
                state.editingDark
            )
            dataStoreManager.updateReaderThemeSuite(
                suiteId,
                layout,
                currentSettings.copy(backgroundSelection = preset.selectionKey),
                modeDark = state.editingDark.takeIf { layout == ReaderLayoutTarget.READER_LAYOUT }
            )
        }
    }

    fun removeBackgroundPhoto() {
        val state = _uiState.value
        val suite = state.editingSuite ?: return
        val layout = state.editingLayout
        val suiteSettings = suite.settingsFor(layout, state.editingDark)
        val preset = _uiState.value.backgrounds.firstOrNull {
            it.selectionKey == suiteSettings.backgroundSelection
        }
        val currentSettings = suiteSettings
        val updatedSettings = currentSettings.copy(
            backgroundSelection = currentSettings.backgroundColorSelection,
            backgroundImageOpacity = 1f,
            backgroundImageBlurDp = 0f
        )
        viewModelScope.launch {
            dataStoreManager.updateReaderThemeSuite(
                suite.id,
                layout,
                updatedSettings,
                modeDark = state.editingDark.takeIf { layout == ReaderLayoutTarget.READER_LAYOUT }
            )
            val usedByAnotherSuite = preset != null && _uiState.value.suites.any {
                it.id != suite.id && ReaderLayoutTarget.entries.any { target ->
                    it.settingsFor(target).backgroundSelection == preset.selectionKey
                }
            }
            if (preset?.type == ReaderBackgroundType.IMAGE && !usedByAnotherSuite) {
                dataStoreManager.saveCustomReaderBackgrounds(
                    _uiState.value.backgrounds.filterNot { it.id == preset.id }
                )
                withContext(Dispatchers.IO) {
                    runCatching { File(preset.value).delete() }
                    preset.processedValue?.let { runCatching { File(it).delete() } }
                }
            }
        }
    }

    private fun prepareProcessedBackground(
        settings: ReaderThemeSettings,
        backgrounds: List<ReaderBackgroundPreset>
    ): List<ReaderBackgroundPreset> {
        val preset = backgrounds.firstOrNull {
            it.selectionKey == settings.backgroundSelection && it.type == ReaderBackgroundType.IMAGE
        } ?: return backgrounds
        val blur = settings.backgroundImageBlurDp.coerceIn(0f, 40f)
        if (blur < 0.01f) {
            preset.processedValue?.let { runCatching { File(it).delete() } }
            return backgrounds.map {
                if (it.id == preset.id) it.copy(processedValue = null, processedBlurDp = null) else it
            }
        }
        if (preset.processedValue != null &&
            preset.processedBlurDp != null &&
            kotlin.math.abs(preset.processedBlurDp - blur) < 0.01f &&
            File(preset.processedValue).isFile
        ) return backgrounds
        val blurKey = "%.2f".format(java.util.Locale.US, blur)
            .replace('.', '_')
        val target = File(
            context.filesDir,
            "reader_backgrounds/${preset.id}.blurred-$blurKey.jpg"
        )
        val generated = target.isFile ||
            ReaderBackgroundImageProcessor.createBlurredCopy(
                source = File(preset.value),
                target = target,
                blurDp = blur,
                density = context.resources.displayMetrics.density
            )
        if (!generated) return backgrounds
        preset.processedValue?.takeUnless { it == target.absolutePath }?.let {
            runCatching { File(it).delete() }
        }
        return backgrounds.map {
            if (it.id == preset.id) it.copy(
                processedValue = target.absolutePath,
                processedBlurDp = blur
            ) else it
        }
    }

    fun selectBackgroundColor(selection: String) {
        val state = _uiState.value
        val suite = state.editingSuite ?: return
        val layout = state.editingLayout
        val currentSettings = suite.settingsFor(layout, state.editingDark)
        val currentIsImage = _uiState.value.backgrounds.any {
            it.selectionKey == currentSettings.backgroundSelection &&
                it.type == ReaderBackgroundType.IMAGE
        }
        val updatedSettings = currentSettings.copy(
            backgroundSelection = if (currentIsImage) {
                currentSettings.backgroundSelection
            } else {
                selection
            },
            backgroundColorSelection = selection
        )
        _uiState.update { state ->
            state.copy(
                suites = state.suites.map {
                    if (it.id == suite.id) {
                        if (layout == ReaderLayoutTarget.READER_LAYOUT) {
                            it.withModeSettings(state.editingDark, updatedSettings)
                        } else {
                            it.withSettings(layout, updatedSettings)
                        }
                    } else it
                }
            )
        }
        viewModelScope.launch {
            dataStoreManager.updateReaderThemeSuite(
                suite.id,
                layout,
                updatedSettings,
                modeDark = state.editingDark.takeIf { layout == ReaderLayoutTarget.READER_LAYOUT }
            )
        }
    }
}
