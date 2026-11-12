package com.huangder.lumibooks.ui.settings

import com.huangder.lumibooks.ui.animation.pagePushToolbarItem
import com.huangder.lumibooks.ui.icons.ControlIcon

import com.huangder.lumibooks.ui.components.AppDialogAction
import com.huangder.lumibooks.ui.components.CollapsingPageScaffold
import com.huangder.lumibooks.ui.components.AppDialogActionRole
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.icons.AppIcons

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.domain.model.resolveImageSource
import com.huangder.lumibooks.ui.reader.engine.PageContentView
import com.huangder.lumibooks.ui.reader.engine.ReadView
import com.huangder.lumibooks.ui.reader.engine.ReadViewCallbacks
import com.huangder.lumibooks.ui.reader.engine.ReaderParagraphFormatter
import com.huangder.lumibooks.ui.reader.engine.toAnnotationStyle
import com.huangder.lumibooks.ui.reader.ReaderExpandingSliderRow
import com.huangder.lumibooks.ui.reader.readerPanelSectionSurface
import com.huangder.lumibooks.ui.components.GroupedCard
import com.huangder.lumibooks.ui.components.GroupedRowDivider
import com.huangder.lumibooks.ui.components.GroupedSectionFooter
import com.huangder.lumibooks.ui.components.GroupedSectionTitle
import com.huangder.lumibooks.ui.components.LumiActionRow
import com.huangder.lumibooks.ui.components.LumiRowMinHeight
import com.huangder.lumibooks.ui.components.LumiSegmentedControl
import com.huangder.lumibooks.ui.components.lumiRowClickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.reader.ReaderSettingsSegmentedTag
import com.huangder.lumibooks.ui.reader.TextColorSetting
import com.huangder.lumibooks.ui.reader.TextColorSwatch
import com.huangder.lumibooks.ui.reader.darkenReaderSolidColor
import com.huangder.lumibooks.ui.reader.readerPresetBackgroundSelections
import com.huangder.lumibooks.ui.reader.readerPresetBackgroundColor
import com.huangder.lumibooks.ui.reader.readerPresetTextColor
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.LumiMotion
import com.huangder.lumibooks.ui.animation.PageTransitions
import com.huangder.lumibooks.ui.components.ConfigurableActivityBack
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.PillSlider
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.LocalPredictiveBackEnabled
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.components.horizontalBleed
import com.huangder.lumibooks.util.LaunchThemeController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.core.graphics.ColorUtils
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.huangder.lumibooks.ui.animation.finishWithPush

@AndroidEntryPoint
class ReaderSettingsPreviewActivity : ComponentActivity() {
    private var systemDarkMode by mutableStateOf(false)

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.isNightModeEnabled()
        val initialMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_THEMES
        val launchTheme = LaunchThemeController.themeSnapshot(this)

        setContent {
            val viewModel: ReaderSettingsPreviewViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsState()
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = launchTheme.predictiveBackEnabled)
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = launchTheme.appTheme)
            val appAccentColor by dataStoreManager.appAccentColor.collectAsState(initial = launchTheme.appAccentColor)
            val appFontSize by dataStoreManager.appFontSize.collectAsState(initial = launchTheme.appFontSize)
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = launchTheme.globalFontMode)
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = launchTheme.liquidGlassTransparency)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = launchTheme.liquidGlassHdrHighlightEnabled)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = launchTheme.darkMode)
            val motionPreferenceValue by dataStoreManager.motionPreference.collectAsState(initial = launchTheme.motionPreference)
            val eInkMode by dataStoreManager.eInkModeEnabled.collectAsState(initial = launchTheme.eInkModeEnabled)
            val liquidGlassCapability = rememberLiquidGlassCapability(eInkMode, LocalView.current)
            val resolvedAppTheme = effectiveAppTheme(appTheme, liquidGlassCapability)
            val isDark = if (eInkMode) false else when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }

            val menuAnimationStyleValue by dataStoreManager.menuAnimationStyle.collectAsState(initial = launchTheme.menuAnimationStyle)
            val liquidGlassStyle by dataStoreManager.liquidGlassStyle
                .collectAsState(initial = launchTheme.liquidGlassStyle)
            val liquidGlassSamplingQuality by dataStoreManager.liquidGlassSamplingQuality
                .collectAsState(initial = launchTheme.liquidGlassSamplingQuality)
            val reduceTransparencyEnabled by dataStoreManager.reduceTransparencyEnabled
                .collectAsState(initial = launchTheme.reduceTransparencyEnabled)
            EBookReaderTheme(
                eInkMode = eInkMode,
                liquidGlassStyle = liquidGlassStyle,
                liquidGlassSamplingQuality = liquidGlassSamplingQuality,
                reduceTransparencyEnabled = reduceTransparencyEnabled,
                menuAnimationStyle = com.huangder.lumibooks.domain.model.MenuAnimationStyle.fromStoredValue(menuAnimationStyleValue),
                darkTheme = isDark,
                dynamicColor = resolvedAppTheme == "material3",
                appTheme = resolvedAppTheme,
                appAccentColor = appAccentColor,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled,
                appFontSize = appFontSize,
                globalFontMode = globalFontMode,
                motionPreference = MotionPreference.fromStoredValue(motionPreferenceValue)
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalPredictiveBackEnabled provides predictiveBackEnabled
                ) {
                    ConfigurableActivityBack(
                        predictiveBackEnabled = predictiveBackEnabled,
                        onBack = { finishWithPush() }
                    )
                    ConfigurableBackHandler(
                        enabled = initialMode == MODE_THEMES && state.editingSuite != null,
                        onBack = viewModel::closeEditor
                    )
                    // The suite editor starts on the mode the settings page is
                    // currently rendered in. Once the user enters the editor,
                    // their explicit light/dark selection remains untouched.
                    LaunchedEffect(isDark) {
                        if (state.editingSuiteId == null) {
                            viewModel.selectEditingMode(isDark)
                        }
                    }
                    var pendingExportSuiteId by remember { mutableStateOf<String?>(null) }
                    val exportLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/json")
                    ) { uri ->
                        val suiteId = pendingExportSuiteId
                        pendingExportSuiteId = null
                        if (uri != null && suiteId != null) {
                            viewModel.exportThemeBundle(uri, suiteId) { result ->
                                Toast.makeText(
                                    this@ReaderSettingsPreviewActivity,
                                    if (result.isSuccess) R.string.theme_bundle_export_success else R.string.theme_bundle_export_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    val importLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        uri?.let {
                            viewModel.importThemeBundle(it) { result ->
                                val message = result.fold(
                                    onSuccess = { report ->
                                        Toast.makeText(
                                            this@ReaderSettingsPreviewActivity,
                                            getString(R.string.theme_bundle_import_success, report.importedCount),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        if (report.missingBackgroundImages.isNotEmpty()) {
                                            Toast.makeText(
                                                this@ReaderSettingsPreviewActivity,
                                                getString(
                                                    R.string.theme_bundle_missing_background_fallback,
                                                    report.missingBackgroundImages.joinToString("、")
                                                ),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        null
                                    },
                                    onFailure = { error -> getString(R.string.theme_bundle_import_failed, error.message ?: "invalid file") }
                                )
                                message?.let {
                                    Toast.makeText(this@ReaderSettingsPreviewActivity, it, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    ReaderSettingsPreviewContent(
                        initialMode = initialMode,
                        state = state,
                        onClose = { finishWithPush() },
                        onEdit = viewModel::beginEditing,
                        onExitEditor = viewModel::closeEditor,
                        onCreate = viewModel::createTheme,
                        onActivate = viewModel::setActiveTheme,
                        onRename = viewModel::renameTheme,
                        onDelete = viewModel::deleteTheme,
                        onMove = viewModel::moveTheme,
                        onImportBundle = { importLauncher.launch(arrayOf("application/json", "application/zip", "text/plain", "application/octet-stream")) },
                        onExportBundle = { suiteId ->
                            pendingExportSuiteId = suiteId
                            exportLauncher.launch("lumi-theme.lumi-theme.json")
                        },
                        onPreviewUpdate = viewModel::previewTheme,
                        onUpdate = viewModel::updateTheme,
                        onSelectColor = viewModel::selectBackgroundColor,
                        onAddColor = viewModel::addBackgroundColor,
                        onAddPhoto = viewModel::addBackgroundPhoto,
                        onRemovePhoto = viewModel::removeBackgroundPhoto,
                        onLayoutChange = viewModel::selectEditingLayout,
                        onThemeModeChange = viewModel::selectEditingMode,
                        onModeChange = viewModel::setAnimationMode,
                        onDurationPreview = viewModel::previewAnimationDuration,
                        onDurationChange = viewModel::setAnimationDuration
                    )
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = newConfig.isNightModeEnabled()
    }

    private fun Configuration.isNightModeEnabled(): Boolean {
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_THEMES = "themes"
        const val MODE_ANIMATIONS = "animations"
    }
}

private enum class PreviewDestination { THEME_LIST, THEME_EDITOR, ANIMATIONS }

@Composable
private fun ReaderSettingsPreviewContent(
    initialMode: String,
    state: ReaderSettingsPreviewUiState,
    onClose: () -> Unit,
    onEdit: (String) -> Unit,
    onExitEditor: () -> Unit,
    onCreate: (String) -> Unit,
    onActivate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onImportBundle: () -> Unit,
    onExportBundle: (String) -> Unit,
    onPreviewUpdate: (ReaderThemeSettings) -> Unit,
    onUpdate: (ReaderThemeSettings) -> Unit,
    onSelectColor: (String) -> Unit,
    onAddColor: (String) -> Unit,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onLayoutChange: (ReaderLayoutTarget) -> Unit,
    onThemeModeChange: (Boolean) -> Unit,
    onModeChange: (String) -> Unit,
    onDurationPreview: (String, Int) -> Unit,
    onDurationChange: (String, Int) -> Unit
) {
    val motionEnabled = LocalMotionEnabled.current
    val destination = when {
        initialMode == ReaderSettingsPreviewActivity.MODE_ANIMATIONS -> PreviewDestination.ANIMATIONS
        state.editingSuite != null -> PreviewDestination.THEME_EDITOR
        else -> PreviewDestination.THEME_LIST
    }
    var lastEditingSuite by remember { mutableStateOf<ReaderThemeSuite?>(null) }
    LaunchedEffect(state.editingSuite) {
        state.editingSuite?.let { lastEditingSuite = it }
    }
    val isLiquidGlass = com.huangder.lumibooks.ui.theme.LocalAppTheme.current == "liquid_glass"
    val pageBackdrop = rememberLayerBackdrop()
    val controlsBackdrop = rememberLayerBackdrop()
    val activePageBackdrop = pageBackdrop.takeIf { isLiquidGlass }
    val activeControlsBackdrop = controlsBackdrop.takeIf { isLiquidGlass }
    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.WindowBg) {
        LiquidGlassDialogHost(
            modifier = Modifier.fillMaxSize(),
            backdrop = activePageBackdrop
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(activePageBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(activeControlsBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                        .background(AppColors.WindowBg)
                )
                ProvideLiquidGlassBackdrop(activeControlsBackdrop) {
                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = {
                            if (!motionEnabled || targetState == PreviewDestination.ANIMATIONS) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else if (targetState == PreviewDestination.THEME_EDITOR) {
                                PageTransitions.enter togetherWith PageTransitions.exit
                            } else {
                                PageTransitions.popEnter togetherWith PageTransitions.popExit
                            }
                        },
                        label = "readerSettingsDestination"
                    ) { target ->
                        when (target) {
                            PreviewDestination.ANIMATIONS -> AnimationPreviewScreen(
                                state = state,
                                onClose = onClose,
                                onModeChange = onModeChange,
                                onDurationPreview = onDurationPreview,
                                onDurationChange = onDurationChange
                            )
                            PreviewDestination.THEME_EDITOR -> {
                                val suite = state.editingSuite ?: lastEditingSuite
                                if (suite != null) {
                                    ThemeEditorScreen(
                                        suite = suite,
                                        backgrounds = state.backgrounds,
                                        customFonts = state.customFonts,
                                        requestedLayout = state.editingLayout,
                                        editingDark = state.editingDark,
                                        onLayoutChange = onLayoutChange,
                                        onThemeModeChange = onThemeModeChange,
                                        onExit = onExitEditor,
                                        onPreviewUpdate = onPreviewUpdate,
                                        onUpdate = onUpdate,
                                        onSelectColor = onSelectColor,
                                        onAddColor = onAddColor,
                                        onAddPhoto = onAddPhoto,
                                        onRemovePhoto = onRemovePhoto
                                    )
                                }
                            }
                            PreviewDestination.THEME_LIST -> ThemeSuiteListScreen(
                                state = state,
                                onClose = onClose,
                                onEdit = onEdit,
                                onCreate = onCreate,
                                onActivate = onActivate,
                                onRename = onRename,
                                onDelete = onDelete,
                                onMove = onMove,
                                onImportBundle = onImportBundle,
                                onExportBundle = onExportBundle
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSuiteListScreen(
    state: ReaderSettingsPreviewUiState,
    onClose: () -> Unit,
    onEdit: (String) -> Unit,
    onCreate: (String) -> Unit,
    onActivate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onImportBundle: () -> Unit,
    onExportBundle: (String) -> Unit
) {
    var createDialog by remember { mutableStateOf(false) }
    var renameSuite by remember { mutableStateOf<ReaderThemeSuite?>(null) }
    var deleteSuite by remember { mutableStateOf<ReaderThemeSuite?>(null) }
    var exportSuite by remember { mutableStateOf<ReaderThemeSuite?>(null) }
    val motionEnabled = LocalMotionEnabled.current
    val listState = rememberLazyListState()
    CollapsingPageScaffold(
        title = stringResource(R.string.reader_theme_suites),
        onBack = onClose,
        actions = {
            LiquidGlassIconButton(AppIcons.Plus, stringResource(R.string.add_theme_suite), { createDialog = true })
        }
    ) {
        // 与设置页一致的分组列表：套装放在同一张卡片里，导入导出单独成组。
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .testTag("theme_suite_list"),
            contentPadding = PaddingValues(bottom = AppSpace.lg),
        ) {
            item(key = "suites_title") {
                GroupedSectionTitle(stringResource(R.string.reader_theme_suites_section))
            }
            item(key = "suites_card") {
                GroupedCard {
                    state.suites.forEachIndexed { index, suite ->
                        key(suite.id) {
                            if (index > 0) GroupedRowDivider(startInset = ThemeSuiteRowTextInset)
                            ThemeSuiteRow(
                                suite = suite,
                                backgrounds = state.backgrounds,
                                active = suite.id == state.activeSuiteId,
                                modifier = Modifier.then(
                                    if (motionEnabled) {
                                        Modifier.animateContentSize(
                                            tween(LumiMotion.MenuEnterMillis, easing = AppEasing.Smooth)
                                        )
                                    } else Modifier
                                ),
                                onEdit = { onEdit(suite.id) },
                                onActivate = { onActivate(suite.id) },
                                onRename = { renameSuite = suite },
                                onDelete = { deleteSuite = suite },
                                onMove = { delta -> onMove(suite.id, delta) }
                            )
                        }
                    }
                }
            }
            item(key = "suites_footer") {
                GroupedSectionFooter(stringResource(R.string.reader_theme_suites_footer))
            }
            item(key = "bundle_title") {
                GroupedSectionTitle(stringResource(R.string.reader_theme_bundle_section))
            }
            item(key = "bundle_card") {
                GroupedCard {
                    LumiActionRow(
                        title = stringResource(R.string.theme_bundle_import),
                        icon = AppIcons.UploadSimple,
                        onClick = onImportBundle,
                        modifier = Modifier.testTag("theme_bundle_import")
                    )
                    GroupedRowDivider(startInset = 54.dp)
                    LumiActionRow(
                        title = stringResource(R.string.theme_bundle_export),
                        icon = AppIcons.DownloadSimple,
                        value = state.suites.firstOrNull { it.id == state.activeSuiteId }?.let { themeName(it) },
                        onClick = { exportSuite = state.suites.firstOrNull { it.id == state.activeSuiteId } },
                        modifier = Modifier.testTag("theme_bundle_export")
                    )
                }
            }
        }
    }
    if (createDialog) {
        NameDialog(
            stringResource(R.string.new_theme_suite_title),
            stringResource(R.string.theme_suite_default_name),
            onDismiss = { createDialog = false }
        ) {
            createDialog = false
            onCreate(it)
        }
    }
    renameSuite?.let { suite ->
        NameDialog(
            stringResource(R.string.rename_theme_suite_title),
            suite.customName.orEmpty(),
            onDismiss = { renameSuite = null }
        ) {
            renameSuite = null
            onRename(suite.id, it)
        }
    }
    deleteSuite?.let { suite ->
        LiquidGlassAlertDialog(
            onDismissRequest = { deleteSuite = null },
            titleText = stringResource(R.string.delete_theme_suite_title),
            text = {
                Text(
                    stringResource(R.string.delete_theme_suite_message, suite.customName.orEmpty()),
                    color = AppColors.TextSecondary
                )
            },
            actions = listOf(
                AppDialogAction(
                    text = stringResource(R.string.delete),
                    onClick = { deleteSuite = null; onDelete(suite.id) },
                    role = AppDialogActionRole.Destructive,
                    tintedColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                AppDialogAction(
                    text = stringResource(R.string.cancel),
                    onClick = { deleteSuite = null },
                    role = AppDialogActionRole.Cancel
                )
            ))
    }
    exportSuite?.let { suite ->
        LiquidGlassAlertDialog(
            onDismissRequest = { exportSuite = null },
            titleText = stringResource(R.string.theme_bundle_export_confirm_title),
            text = {
                Text(
                    stringResource(R.string.theme_bundle_export_confirm_message, themeName(suite)),
                    color = AppColors.TextSecondary
                )
            },
            actions = listOf(
                AppDialogAction(
                    text = stringResource(R.string.theme_bundle_export),
                    onClick = { exportSuite = null; onExportBundle(suite.id) },
                    role = AppDialogActionRole.Primary,
                    tintedColor = AppColors.Accent
                ),
                AppDialogAction(
                    text = stringResource(R.string.cancel),
                    onClick = { exportSuite = null },
                    role = AppDialogActionRole.Cancel
                )
            ))
    }
}

@Composable
internal fun ThemeSuiteRow(
    suite: ReaderThemeSuite,
    backgrounds: List<ReaderBackgroundPreset>,
    active: Boolean,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    val threshold = with(LocalDensity.current) { 54.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val previewShape = AppRoundedCornerShape(AppRadius.sm)
    val density = LocalDensity.current
    val revealPx = with(density) { 112.dp.toPx() }
    val offset = remember(suite.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isDragging by remember(suite.id) { mutableStateOf(false) }
    var rawOffset by remember(suite.id) { mutableFloatStateOf(0f) }
    val displayOffset = if (isDragging) rawOffset else offset.value
    val revealProgress = (-displayOffset / revealPx).coerceIn(0f, 1f)
    Box(modifier = modifier.fillMaxWidth().testTag("theme_suite_row_" + suite.id)) {
        if (!suite.isBuiltIn) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = AppSpace.sm)
                    .graphicsLayer {
                        alpha = revealProgress
                        translationX = (1f - revealProgress) * 24.dp.toPx()
                    },
                horizontalArrangement = Arrangement.spacedBy(AppSpace.xs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    imageVector = AppIcons.PencilSimple,
                    contentDescription = stringResource(R.string.rename),
                    onClick = { onRename() },
                    size = 44.dp,
                    iconSize = 20.dp
                )
                LiquidGlassIconButton(
                    imageVector = AppIcons.Trash,
                    contentDescription = stringResource(R.string.delete),
                    onClick = { onDelete() },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = MaterialTheme.colorScheme.error
                )
            }
        }
        // 行本身铺卡片底色，左滑时盖住身后的操作按钮；卡片负责圆角与阴影。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(displayOffset.roundToInt(), 0) }
                .background(AppColors.CardBg)
                .then(if (suite.isBuiltIn) Modifier else Modifier.pointerInput(suite.id, revealPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            rawOffset = offset.value
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            val target = if (rawOffset < -revealPx * 0.4f) -revealPx else 0f
                            scope.launch {
                                offset.snapTo(rawOffset)
                                offset.animateTo(target, spring(dampingRatio = 0.6f, stiffness = 300f))
                                rawOffset = 0f
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                offset.snapTo(rawOffset)
                                offset.animateTo(
                                    if (offset.value < -revealPx * 0.4f) -revealPx else 0f,
                                    spring(dampingRatio = 0.6f, stiffness = 300f)
                                )
                                rawOffset = 0f
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            rawOffset = (rawOffset + amount).coerceIn(-revealPx, 0f)
                        }
                    )
                })
                .lumiRowClickable {
                    if (displayOffset < -1f) scope.launch { offset.animateTo(0f, spring()) }
                    else onEdit()
                }
                .heightIn(min = LumiRowMinHeight)
                .padding(start = AppSpace.md, end = AppSpace.xs, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeSuiteThumbnail(
                suite = suite,
                backgrounds = backgrounds,
                shape = previewShape
            )
            Spacer(Modifier.width(ThemeSuiteThumbnailGap))
            Column(Modifier.weight(1f)) {
                Text(
                    themeName(suite),
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (active) {
                        stringResource(R.string.theme_suite_current)
                    } else {
                        stringResource(R.string.theme_suite_tap_to_edit)
                    },
                    fontSize = AppType.Caption,
                    color = if (active) AppColors.Accent else AppColors.TextSecondary
                )
            }
            Spacer(Modifier.width(AppSpace.sm))
            if (active) {
                Icon(
                    AppIcons.Check,
                    contentDescription = stringResource(R.string.theme_suite_current),
                    tint = AppColors.Accent,
                    modifier = Modifier.size(20.dp).testTag("theme_suite_active_check")
                )
            } else {
                // 「设为当前」做成行内的小胶囊，不再单独占一行。
                Text(
                    stringResource(R.string.theme_suite_set_current),
                    fontSize = AppType.Footnote,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Accent,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(AppRoundedCornerShape(AppRadius.full))
                        .background(AppColors.Accent.copy(alpha = 0.12f))
                        .clickable(onClick = onActivate)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("theme_suite_set_current")
                )
            }
            Icon(
                AppIcons.DotsSixVertical,
                contentDescription = stringResource(R.string.theme_suite_drag_to_reorder),
                tint = AppColors.TextSecondary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(40.dp)
                    .padding(10.dp)
                    .pointerInput(suite.id) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { dragDistance = 0f },
                            onDragCancel = { dragDistance = 0f }
                        ) { change, drag ->
                            change.consume()
                            dragDistance += drag.y
                            if (abs(dragDistance) >= threshold) {
                                onMove(if (dragDistance > 0f) 1 else -1)
                                dragDistance = 0f
                            }
                        }
                    }
            )
        }
    }
}

/** 缩略图与文字的间距；行间分隔线从文字处开始（与设置页一致）。 */
private val ThemeSuiteThumbnailGap = 14.dp
private val ThemeSuiteRowTextInset = AppSpace.md + 44.dp + ThemeSuiteThumbnailGap

@Composable
private fun ThemeSuiteThumbnail(
    suite: ReaderThemeSuite,
    backgrounds: List<ReaderBackgroundPreset>,
    shape: androidx.compose.ui.graphics.Shape
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .border(1.dp, AppColors.Divider, shape)
    ) {
        Row(Modifier.fillMaxSize()) {
            ThemeSuiteThumbnailHalf(
                settings = suite.settingsFor(ReaderLayoutTarget.READER_LAYOUT, dark = false),
                backgrounds = backgrounds,
                dark = false,
                modifier = Modifier.weight(1f)
            )
            ThemeSuiteThumbnailHalf(
                settings = suite.settingsFor(ReaderLayoutTarget.READER_LAYOUT, dark = true),
                backgrounds = backgrounds,
                dark = true,
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(AppColors.Divider)
        )
    }
}

@Composable
private fun ThemeSuiteThumbnailHalf(
    settings: ReaderThemeSettings,
    backgrounds: List<ReaderBackgroundPreset>,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    val basePreset = backgrounds.firstOrNull {
        it.selectionKey == settings.backgroundColorSelection && it.type == ReaderBackgroundType.COLOR
    }
    val imagePreset = backgrounds.firstOrNull {
        it.selectionKey == settings.backgroundSelection && it.type == ReaderBackgroundType.IMAGE
    }
    val background = backgroundColor(
        settings.backgroundColorSelection,
        basePreset,
        dark && imagePreset == null
    )
    val imageSource = imagePreset?.resolveImageSource(settings.backgroundImageBlurDp)
    val iconColor = automaticTextColor(imagePreset?.dominantColor ?: background)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(background)),
        contentAlignment = Alignment.Center
    ) {
        imageSource?.path?.let { path ->
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(imageSource.runtimeBlurDp.dp)
                    .graphicsLayer { alpha = settings.backgroundImageOpacity.coerceIn(0f, 1f) }
            )
        }
        Icon(
            imageVector = if (dark) AppIcons.Moon else AppIcons.SunDim,
            contentDescription = stringResource(
                if (dark) R.string.reader_theme_mode_dark else R.string.reader_theme_mode_light
            ),
            tint = Color(iconColor),
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var value by remember(initial) { mutableStateOf(initial) }
    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        shape = AppRoundedCornerShape(24.dp),
        transparencyOverride = (LocalLiquidGlassTransparency.current - 0.10f).coerceIn(0f, 0.90f),
        fallbackBlurRadius = 12.dp
    ) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 22.dp)) {
            Text(
                title,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppRoundedCornerShape(AppRadius.md))
                    .background(AppColors.BgGray)
                    .border(1.dp, AppColors.Divider, AppRoundedCornerShape(AppRadius.md))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it.take(30) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = AppType.Body),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (value.isNotBlank()) onConfirm(value)
                    }),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                stringResource(R.string.theme_suite_name_hint),
                                color = AppColors.TextSecondary,
                                fontSize = AppType.Body
                            )
                        }
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                LiquidGlassTextButton(
                    text = stringResource(R.string.confirm),
                    enabled = value.isNotBlank(),
                    onClick = { if (value.isNotBlank()) onConfirm(value) },
                    tintedColor = AppColors.Accent
                )
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun ThemeModeSelector(
    editingDark: Boolean,
    onThemeModeChange: (Boolean) -> Unit
) {
    ReaderSettingsSegmentedTag(
        labels = listOf(
            stringResource(R.string.reader_theme_mode_light),
            stringResource(R.string.reader_theme_mode_dark)
        ),
        selectedIndex = if (editingDark) 1 else 0,
        onSelect = { onThemeModeChange(it == 1) }
    )
}

private enum class ThemePanel { NONE, BACKGROUND, TEXT }

@Composable
private fun ThemeEditorScreen(
    suite: ReaderThemeSuite,
    backgrounds: List<ReaderBackgroundPreset>,
    customFonts: List<CustomFontPreset>,
    requestedLayout: ReaderLayoutTarget,
    editingDark: Boolean,
    onLayoutChange: (ReaderLayoutTarget) -> Unit,
    onThemeModeChange: (Boolean) -> Unit,
    onExit: () -> Unit,
    onPreviewUpdate: (ReaderThemeSettings) -> Unit,
    onUpdate: (ReaderThemeSettings) -> Unit,
    onSelectColor: (String) -> Unit,
    onAddColor: (String) -> Unit,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRemovePhoto: () -> Unit
) {
    var panel by remember { mutableStateOf(ThemePanel.NONE) }
    val motionEnabled = LocalMotionEnabled.current
    // 「原排版」只用于书籍原排版：始终编辑原排版设置，没有阅读器排版可切换。
    val layout = if (suite.isBookLayoutOnly) ReaderLayoutTarget.BOOK_LAYOUT else requestedLayout
    LaunchedEffect(suite.id, layout) {
        if (suite.isBookLayoutOnly) onLayoutChange(ReaderLayoutTarget.BOOK_LAYOUT)
    }
    val settings = suite.settingsFor(layout, editingDark)
    val preservePublisherLayout = layout == ReaderLayoutTarget.BOOK_LAYOUT
    val previewBackdrop = rememberLayerBackdrop()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(onAddPhoto)
    }
    val sample = rememberSampleText()
    val imagePreset = backgrounds.firstOrNull {
        it.selectionKey == settings.backgroundSelection && it.type == ReaderBackgroundType.IMAGE
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(previewBackdrop)
        ) {
            PreviewReadView(
                settings = settings,
                backgrounds = backgrounds,
                customFonts = customFonts,
                dark = editingDark,
                pageTransition = "slide",
                pageDurationMs = ReaderPageAnimationSettings.SLIDE_DEFAULT_MS,
                sample = sample,
                showAnnotationSamples = true,
                bookLayout = layout == ReaderLayoutTarget.BOOK_LAYOUT
            )
        }
        ProvideLiquidGlassBackdrop(previewBackdrop) {

            ThemeEditorTopBar(
                layout = layout,
                onLayoutChange = onLayoutChange,
                onExit = onExit,
                showReaderLayout = !suite.isBookLayoutOnly,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            AnimatedContent(
                targetState = panel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 76.dp)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight / 2),
                transitionSpec = {
                    when {
                        !motionEnabled -> EnterTransition.None togetherWith ExitTransition.None
                        targetState == ThemePanel.NONE -> EnterTransition.None togetherWith (
                            slideOutVertically(
                                animationSpec = tween(
                                    LumiMotion.SheetExitMillis,
                                    easing = AppEasing.Accelerate
                                )
                            ) { it / 4 } + fadeOut(tween(LumiMotion.MenuExitMillis))
                        )
                        initialState == ThemePanel.NONE -> (
                            slideInVertically(
                                animationSpec = tween(
                                    LumiMotion.SheetEnterMillis,
                                    easing = AppEasing.Smooth
                                )
                            ) { it / 4 } + fadeIn(tween(LumiMotion.MenuEnterMillis))
                        ) togetherWith ExitTransition.None
                        else -> fadeIn(tween(LumiMotion.MenuEnterMillis)) togetherWith
                            fadeOut(tween(LumiMotion.MenuExitMillis))
                    }
                },
                label = "themeEditorPanel"
            ) { targetPanel ->
                if (targetPanel != ThemePanel.NONE) {
                    ReaderFloatingPanel {
                        when (targetPanel) {
                            ThemePanel.BACKGROUND -> BackgroundPanel(
                                settings = settings,
                                backgrounds = backgrounds,
                                hasImage = imagePreset != null,
                                showThemeMode = true,
                                editingDark = editingDark,
                                onThemeModeChange = onThemeModeChange,
                                onPreviewUpdate = onPreviewUpdate,
                                onUpdate = onUpdate,
                                onSelectColor = onSelectColor,
                                onAddColor = onAddColor,
                                onAddPhoto = { photoPicker.launch("image/*") },
                                onRemovePhoto = onRemovePhoto
                            )
                            ThemePanel.TEXT -> TextPanel(
                                settings = settings,
                                backgrounds = backgrounds,
                                customFonts = customFonts,
                                preservePublisherLayout = preservePublisherLayout,
                                editingDark = editingDark,
                                onThemeModeChange = onThemeModeChange,
                                lockTextColor = suite.isBookLayoutOnly,
                                onPreviewUpdate = onPreviewUpdate,
                                onUpdate = onUpdate
                            )
                            ThemePanel.NONE -> Unit
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
            ) {
                if (!suite.isBookLayoutOnly) {
                    CapsuleButton(
                        text = stringResource(R.string.reader_background_settings),
                        icon = AppIcons.PalettePair,
                        selected = panel == ThemePanel.BACKGROUND
                    ) {
                        panel = if (panel == ThemePanel.BACKGROUND) {
                            ThemePanel.NONE
                        } else {
                            ThemePanel.BACKGROUND
                        }
                    }
                }
                CapsuleButton(
                    text = stringResource(R.string.reader_text_settings),
                    icon = AppIcons.TextAaPair,
                    selected = panel == ThemePanel.TEXT
                ) {
                    panel = if (panel == ThemePanel.TEXT) ThemePanel.NONE else ThemePanel.TEXT
                }
            }
        }
    }
}

@Composable
internal fun ThemeEditorTopBar(
    layout: ReaderLayoutTarget,
    onLayoutChange: (ReaderLayoutTarget) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** 「原排版」套装只有书籍原排版，隐藏阅读器排版选项。 */
    showReaderLayout: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth().statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp).testTag("theme_editor_topbar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassIconButton(
            imageVector = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
            contentDescription = stringResource(R.string.reader_back),
            onClick = onExit,
            modifier = Modifier.pagePushToolbarItem().testTag("theme_editor_back")
        )
        if (showReaderLayout) {
            OptionCapsule(
                label = stringResource(R.string.reader_layout_editor_reader),
                selected = layout == ReaderLayoutTarget.READER_LAYOUT,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).pagePushToolbarItem().testTag("theme_editor_reader_layout"),
                onClick = { onLayoutChange(ReaderLayoutTarget.READER_LAYOUT) }
            )
        }
        OptionCapsule(
            label = stringResource(R.string.reader_layout_editor_book),
            selected = layout == ReaderLayoutTarget.BOOK_LAYOUT,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).pagePushToolbarItem().testTag("theme_editor_book_layout"),
            onClick = { onLayoutChange(ReaderLayoutTarget.BOOK_LAYOUT) }
        )
    }
}

@Composable
private fun ReaderFloatingPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = AppRoundedCornerShape(AppRadius.xl),
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.84f),
        contentAlignment = Alignment.TopStart
    ) {
        content()
    }
}

@Composable
internal fun BackgroundPanel(
    settings: ReaderThemeSettings,
    backgrounds: List<ReaderBackgroundPreset>,
    hasImage: Boolean,
    showThemeMode: Boolean,
    editingDark: Boolean,
    onThemeModeChange: (Boolean) -> Unit,
    onPreviewUpdate: (ReaderThemeSettings) -> Unit,
    onUpdate: (ReaderThemeSettings) -> Unit,
    onSelectColor: (String) -> Unit,
    onAddColor: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    var colorDialog by remember { mutableStateOf(false) }
    PanelColumn {
        PanelHeader(stringResource(R.string.reader_background_settings)) {
            if (showThemeMode) {
                ThemeModeSelector(editingDark = editingDark, onThemeModeChange = onThemeModeChange)
            }
        }
        PanelSection(stringResource(R.string.reader_panel_section_background_color)) {
            // 滚动到分区块边缘：抵消分区块的 16dp 内边距。
            LazyRow(
                modifier = Modifier.horizontalBleed(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(readerPresetBackgroundSelections) { selection ->
                    TextColorSwatch(
                        color = Color(backgroundColor(selection, null, editingDark)),
                        isSelected = settings.backgroundColorSelection == selection,
                        contentDescription = stringResource(when (selection) {
                            "night" -> R.string.theme_night
                            "sepia" -> R.string.theme_sepia
                            "green" -> R.string.theme_green
                            else -> R.string.theme_day
                        }),
                        onClick = { onSelectColor(selection) }
                    )
                }
                items(backgrounds.filter { it.type == ReaderBackgroundType.COLOR }) { preset ->
                    TextColorSwatch(
                        color = Color(backgroundColor(preset.selectionKey, preset, editingDark)),
                        isSelected = settings.backgroundColorSelection == preset.selectionKey,
                        contentDescription = preset.displayName(preset.value),
                        onClick = { onSelectColor(preset.selectionKey) }
                    )
                }
                item {
                    TextColorSwatch(
                        color = AppColors.BgGray,
                        isSelected = false,
                        contentDescription = stringResource(R.string.background_custom_color),
                        onClick = { colorDialog = true }
                    ) {
                        Icon(AppIcons.Plus, contentDescription = null,
                            tint = AppColors.TextSecondary, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
        PanelSection(stringResource(R.string.reader_panel_section_background_image)) {
            if (hasImage) {
                SliderPair(
                    first = { onActive ->
                        SettingSlider(
                            stringResource(R.string.background_photo_opacity),
                            settings.backgroundImageOpacity * 100f, 0f..100f, 99, "%",
                            onPreview = { onPreviewUpdate(settings.copy(backgroundImageOpacity = it / 100f)) },
                            onChange = { onUpdate(settings.copy(backgroundImageOpacity = it / 100f)) },
                            onActiveChange = onActive
                        )
                    },
                    second = { onActive ->
                        SettingSlider(
                            stringResource(R.string.background_photo_blur),
                            settings.backgroundImageBlurDp, 0f..40f, 39, "dp",
                            onPreview = { onPreviewUpdate(settings.copy(backgroundImageBlurDp = it)) },
                            onChange = { onUpdate(settings.copy(backgroundImageBlurDp = it)) },
                            onActiveChange = onActive
                        )
                    }
                )
                CommandCapsuleButton(
                    text = stringResource(R.string.background_remove_photo),
                    icon = AppIcons.Trash,
                    onClick = onRemovePhoto,
                    secondary = true
                )
            } else {
                CommandCapsuleButton(
                    text = stringResource(R.string.background_add_photo),
                    icon = AppIcons.Image,
                    onClick = onAddPhoto
                )
            }
        }
    }
    if (colorDialog) {
        val selectedPreset = backgrounds.firstOrNull {
            it.selectionKey == settings.backgroundColorSelection
        }
        val initialColorHex = remember(settings.backgroundColorSelection, selectedPreset, editingDark) {
            String.format(
                java.util.Locale.ROOT,
                "#%06X",
                backgroundColor(settings.backgroundColorSelection, selectedPreset, editingDark) and 0xFFFFFF
            )
        }
        ThemeColorDialog(
            initialColorHex = initialColorHex,
            onDismiss = { colorDialog = false },
            onConfirm = {
                colorDialog = false
                onAddColor(it)
            },
            dialogTitle = stringResource(R.string.background_custom_color_title),
            confirmText = stringResource(R.string.background_add),
            resetText = stringResource(R.string.background_reset_white),
            resetColorHex = "#FFFFFF"
        )
    }
}

@Composable
internal fun TextPanel(
    settings: ReaderThemeSettings,
    backgrounds: List<ReaderBackgroundPreset>,
    customFonts: List<CustomFontPreset>,
    preservePublisherLayout: Boolean,
    editingDark: Boolean,
    onThemeModeChange: (Boolean) -> Unit,
    /** 「原排版」：文字颜色不可改，隐藏色板。 */
    lockTextColor: Boolean = false,
    onPreviewUpdate: (ReaderThemeSettings) -> Unit,
    onUpdate: (ReaderThemeSettings) -> Unit
) {
    PanelColumn {
        PanelHeader(stringResource(R.string.reader_text_settings)) {
            ThemeModeSelector(editingDark = editingDark, onThemeModeChange = onThemeModeChange)
        }
        PanelSection(stringResource(R.string.reader_panel_section_text)) {
            if (lockTextColor) {
                Text(
                    stringResource(R.string.reader_theme_publisher_hint),
                    fontSize = AppType.Footnote,
                    color = AppColors.TextSecondary
                )
            } else {
                TextColorSetting(
                    currentOverride = settings.textColor,
                    effectiveTextColor = Color(themeTextColor(settings, backgrounds, editingDark)),
                    onColorChange = { onUpdate(settings.copy(textColor = it)) },
                    // 分区块内边距为 16dp，色板滚动到分区块边缘。
                    edgeBleed = 16.dp
                )
            }
            LabeledControl(stringResource(R.string.font_label)) {
                OptionRow(
                    options = listOf(
                        "system" to stringResource(R.string.font_system),
                        "serif" to stringResource(R.string.font_serif),
                        "kaiti" to stringResource(R.string.font_kaiti)
                    ) +
                        customFonts.mapIndexed { index, font ->
                            font.fontTypeKey to font.displayName(
                                stringResource(R.string.custom_font_numbered_name, index + 1)
                            )
                        },
                    selected = settings.fontType
                ) { onUpdate(settings.copy(fontType = it)) }
            }
            SliderPair(
                first = { onActive ->
                    SettingSlider(
                        stringResource(R.string.label_font_size),
                        settings.fontSize, ReaderThemeSettings.FONT_SIZE_RANGE, 15, "sp",
                        onPreview = { onPreviewUpdate(settings.copy(fontSize = it)) },
                        onChange = { onUpdate(settings.copy(fontSize = it)) },
                        onActiveChange = onActive
                    )
                },
                second = { onActive ->
                    SettingSlider(
                        stringResource(R.string.body_font_weight),
                        settings.bodyFontWeight, 100f..900f, 7999, "",
                        onPreview = { onPreviewUpdate(settings.copy(bodyFontWeight = (it * 10f).roundToInt() / 10f)) },
                        onChange = { onUpdate(settings.copy(bodyFontWeight = (it * 10f).roundToInt() / 10f)) },
                        onActiveChange = onActive
                    )
                }
            )
        }
        PanelSection(stringResource(R.string.reader_panel_section_spacing)) {
            SliderPair(
                first = { onActive ->
                    SettingSlider(
                        stringResource(R.string.label_line_height),
                        settings.lineHeight, ReaderThemeSettings.LINE_HEIGHT_RANGE, 19, "×",
                        onPreview = { onPreviewUpdate(settings.copy(lineHeight = it)) },
                        onChange = { onUpdate(settings.copy(lineHeight = it)) },
                        onActiveChange = onActive
                    )
                },
                second = { onActive ->
                    SettingSlider(
                        stringResource(R.string.label_letter_spacing),
                        settings.letterSpacing, ReaderThemeSettings.LETTER_SPACING_RANGE, 29, "dp",
                        onPreview = { onPreviewUpdate(settings.copy(letterSpacing = it)) },
                        onChange = { onUpdate(settings.copy(letterSpacing = it)) },
                        onActiveChange = onActive
                    )
                }
            )
            SliderPair(
                first = { onActive ->
                    SettingSlider(
                        stringResource(R.string.label_paragraph_spacing),
                        settings.paragraphSpacing, 0f..30f, 29, "dp",
                        onPreview = { onPreviewUpdate(settings.copy(paragraphSpacing = it)) },
                        onChange = { onUpdate(settings.copy(paragraphSpacing = it)) },
                        onActiveChange = onActive
                    )
                },
                second = { onActive ->
                    SettingSlider(
                        stringResource(R.string.label_first_line_indent),
                        settings.firstLineIndent, 0f..4f, 7, stringResource(R.string.reader_unit_character),
                        onPreview = { onPreviewUpdate(settings.copy(firstLineIndent = it)) },
                        onChange = { onUpdate(settings.copy(firstLineIndent = it)) },
                        onActiveChange = onActive
                    )
                }
            )
            // 五个选项的文字较长，用可横向滚动的胶囊（与字体一致），避免分段控件截断。
            LabeledControl(stringResource(R.string.label_text_alignment)) {
                OptionRow(
                    listOf(
                        ReaderTextAlignment.NATURAL.key to stringResource(R.string.text_alignment_natural),
                        ReaderTextAlignment.LEFT.key to stringResource(R.string.text_alignment_left),
                        ReaderTextAlignment.CENTER.key to stringResource(R.string.text_alignment_center),
                        ReaderTextAlignment.RIGHT.key to stringResource(R.string.text_alignment_right),
                        ReaderTextAlignment.JUSTIFY.key to stringResource(R.string.text_alignment_justify)
                    ),
                    settings.textAlignment.key
                ) { onUpdate(settings.copy(textAlignment = ReaderTextAlignment.fromKey(it))) }
            }
        }
        PanelSection(stringResource(R.string.reader_panel_section_margins)) {
            SliderPair(
                first = { onActive ->
                    SettingSlider(
                        stringResource(R.string.label_margin_left), settings.marginLeft, 0f..80f, 79, "dp",
                        onPreview = { onPreviewUpdate(settings.copy(marginLeft = it)) },
                        onChange = { onUpdate(settings.copy(marginLeft = it)) },
                        onActiveChange = onActive
                    )
                },
                second = { onActive ->
                    SettingSlider(
                        stringResource(R.string.label_margin_right), settings.marginRight, 0f..80f, 79, "dp",
                        onPreview = { onPreviewUpdate(settings.copy(marginRight = it)) },
                        onChange = { onUpdate(settings.copy(marginRight = it)) },
                        onActiveChange = onActive
                    )
                }
            )
            SliderPair(
                first = { onActive ->
                    SettingSlider(
                        stringResource(R.string.label_margin_top), settings.marginTop, 0f..120f, 119, "dp",
                        onPreview = { onPreviewUpdate(settings.copy(marginTop = it)) },
                        onChange = { onUpdate(settings.copy(marginTop = it)) },
                        onActiveChange = onActive
                    )
                },
                second = { onActive ->
                    SettingSlider(
                        stringResource(R.string.label_margin_bottom), settings.marginBottom, 0f..120f, 119, "dp",
                        onPreview = { onPreviewUpdate(settings.copy(marginBottom = it)) },
                        onChange = { onUpdate(settings.copy(marginBottom = it)) },
                        onActiveChange = onActive
                    )
                }
            )
        }
        PanelSection(stringResource(R.string.reader_annotation_geometry_title)) {
            AnnotationGeometrySliders(settings, onPreviewUpdate, onUpdate)
        }
    }
}

/** 笔记高亮与划线的位置/尺寸，步长 0.5dp：高亮三项在上，划线两项在下。 */
@Composable
private fun AnnotationGeometrySliders(
    settings: ReaderThemeSettings,
    onPreviewUpdate: (ReaderThemeSettings) -> Unit,
    onUpdate: (ReaderThemeSettings) -> Unit
) {
    fun halfDpSteps(range: ClosedFloatingPointRange<Float>): Int =
        ((range.endInclusive - range.start) / 0.5f).roundToInt() - 1
    val width = ReaderThemeSettings.HIGHLIGHT_WIDTH_RANGE
    val height = ReaderThemeSettings.HIGHLIGHT_HEIGHT_RANGE
    val highlightOffset = ReaderThemeSettings.HIGHLIGHT_OFFSET_Y_RANGE
    val thickness = ReaderThemeSettings.UNDERLINE_THICKNESS_RANGE
    val underlineOffset = ReaderThemeSettings.UNDERLINE_OFFSET_Y_RANGE
    SliderPair(
        first = { onActive ->
            SettingSlider(
                stringResource(R.string.label_highlight_width), settings.highlightWidthDp, width,
                halfDpSteps(width), "dp",
                onPreview = { onPreviewUpdate(settings.copy(highlightWidthDp = it)) },
                onChange = { onUpdate(settings.copy(highlightWidthDp = it)) },
                onActiveChange = onActive
            )
        },
        second = { onActive ->
            SettingSlider(
                stringResource(R.string.label_highlight_height), settings.highlightHeightDp, height,
                halfDpSteps(height), "dp",
                onPreview = { onPreviewUpdate(settings.copy(highlightHeightDp = it)) },
                onChange = { onUpdate(settings.copy(highlightHeightDp = it)) },
                onActiveChange = onActive
            )
        }
    )
    SettingSlider(
        stringResource(R.string.label_highlight_offset_y), settings.highlightOffsetYDp, highlightOffset,
        halfDpSteps(highlightOffset), "dp",
        onPreview = { onPreviewUpdate(settings.copy(highlightOffsetYDp = it)) },
        onChange = { onUpdate(settings.copy(highlightOffsetYDp = it)) }
    )
    SliderPair(
        first = { onActive ->
            SettingSlider(
                stringResource(R.string.label_underline_thickness), settings.underlineThicknessDp, thickness,
                halfDpSteps(thickness), "dp",
                onPreview = { onPreviewUpdate(settings.copy(underlineThicknessDp = it)) },
                onChange = { onUpdate(settings.copy(underlineThicknessDp = it)) },
                onActiveChange = onActive
            )
        },
        second = { onActive ->
            SettingSlider(
                stringResource(R.string.label_underline_offset_y), settings.underlineOffsetYDp, underlineOffset,
                halfDpSteps(underlineOffset), "dp",
                onPreview = { onPreviewUpdate(settings.copy(underlineOffsetYDp = it)) },
                onChange = { onUpdate(settings.copy(underlineOffsetYDp = it)) },
                onActiveChange = onActive
            )
        }
    )
}

// ── 浮动面板的版式组件：面板头 + 分区块，块内控件成对并排，避免逐项罗列 ──

/** 面板内容：可滚动，外边距 16dp，块与块之间 12dp。 */
@Composable
private fun PanelColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

/** 面板标题与右侧控件（如日/夜切换）同一行。 */
@Composable
private fun PanelHeader(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = AppType.Headline,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing()
    }
}

/** 与阅读器「高级设置」同款的分区块：标题 + 内容，内容间距 16dp。 */
@Composable
private fun PanelSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .readerPanelSectionSurface(LocalEInkMode.current)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            title,
            fontSize = AppType.Subhead,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        content()
    }
}

/** 小标签在上、控件在下的组合（字体、对齐方式）。 */
@Composable
private fun LabeledControl(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = AppType.Footnote, color = AppColors.TextSecondary)
        content()
    }
}

/** 两个相关滑杆并排；拖动的一侧临时放宽，松手后恢复对半。 */
@Composable
private fun SliderPair(
    first: @Composable (onActive: (Boolean) -> Unit) -> Unit,
    second: @Composable (onActive: (Boolean) -> Unit) -> Unit
) {
    var activeSide by remember { mutableStateOf<Int?>(null) }
    ReaderExpandingSliderRow(
        activeSide = activeSide,
        animate = true,
        first = {
            first { active -> activeSide = if (active) 0 else activeSide.takeUnless { it == 0 } }
        },
        second = {
            second { active -> activeSide = if (active) 1 else activeSide.takeUnless { it == 1 } }
        }
    )
}

@Composable
private fun AnimationPreviewScreen(
    state: ReaderSettingsPreviewUiState,
    onClose: () -> Unit,
    onModeChange: (String) -> Unit,
    onDurationPreview: (String, Int) -> Unit,
    onDurationChange: (String, Int) -> Unit
) {
    val mode = if (state.eInkMode &&
        !com.huangder.lumibooks.domain.model.ReaderPageTransition.isAvailableInEInk(state.animationMode)
    ) "fade" else state.animationMode
    val previewTheme = if (LocalIsDarkTheme.current) {
        ReaderThemeSuites.NIGHT_ID
    } else {
        ReaderThemeSuites.DAY_ID
    }
    val duration = state.animationSettings.durationFor(mode)
    val sample = rememberSampleText()
    val previewBackdrop = rememberLayerBackdrop()
    // 预览跟随拖动中的数值，松手后才写入设置。
    var previewDuration by remember(mode) { mutableIntStateOf(duration) }
    LaunchedEffect(duration, mode) { previewDuration = duration }
    // 与主题编辑页同一结构：全屏预览 + 悬浮返回键 + 底部浮动面板，不显示大标题。
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(previewBackdrop)
        ) {
            PreviewReadView(
                ReaderThemeSettings(
                    backgroundSelection = previewTheme,
                    backgroundColorSelection = previewTheme
                ),
                emptyList(),
                emptyList(),
                mode,
                previewDuration,
                sample,
                readerTheme = previewTheme
            )
        }
        ProvideLiquidGlassBackdrop(previewBackdrop) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("animation_settings_topbar"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    imageVector = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                    contentDescription = stringResource(R.string.reader_back),
                    onClick = onClose,
                    modifier = Modifier.pagePushToolbarItem().testTag("animation_settings_back")
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = (maxHeight - 88.dp).coerceAtLeast(0.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                ReaderFloatingPanel {
                    AnimationSettingsPanel(
                        mode = mode,
                        eInkMode = state.eInkMode,
                        durationMs = duration,
                        onModeChange = onModeChange,
                        onDurationPreview = { previewDuration = it; onDurationPreview(mode, it) },
                        onDurationChange = { previewDuration = it; onDurationChange(mode, it) }
                    )
                }
            }
        }
    }
}

/** 翻页动画面板：上方选择翻页方式（全宽分段），下方调整该方式的翻页速度。 */
@Composable
internal fun AnimationSettingsPanel(
    mode: String,
    eInkMode: Boolean,
    durationMs: Int,
    onModeChange: (String) -> Unit,
    onDurationPreview: (Int) -> Unit,
    onDurationChange: (Int) -> Unit
) {
    val modes = buildList {
        if (!eInkMode) {
            add("slide" to stringResource(R.string.page_animation_slide_short))
            add("scroll" to stringResource(R.string.page_animation_scroll_short))
        }
        add("fade" to stringResource(R.string.page_animation_fade_short))
        if (!eInkMode) add("curl" to stringResource(R.string.page_animation_curl_short))
        add("wave" to stringResource(R.string.page_animation_wave_short))
    }
    val range = ReaderPageAnimationSettings.rangeFor(mode)
    val step = ReaderPageAnimationSettings.stepFor(mode)
    var showDurationInput by remember(mode) { mutableStateOf(false) }
    var displayedDuration by remember(mode) { mutableFloatStateOf(durationMs.toFloat()) }
    var durationIsDragging by remember(mode) { mutableStateOf(false) }
    LaunchedEffect(durationMs, mode) {
        if (!durationIsDragging) displayedDuration = durationMs.toFloat()
    }
    PanelColumn {
        PanelSection(stringResource(R.string.reader_page_animation_mode)) {
            LumiSegmentedControl(
                labels = modes.map { it.second },
                selectedIndex = modes.indexOfFirst { it.first == mode }.coerceAtLeast(0),
                onSelect = { onModeChange(modes[it].first) },
                modifier = Modifier.testTag("animation_mode_selector")
            )
        }
        PanelSection(stringResource(R.string.page_turn_speed)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.reader_page_animation_speed_hint),
                        modifier = Modifier.weight(1f),
                        fontSize = AppType.Footnote,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ClickableSliderValue(
                        text = "${displayedDuration.roundToInt()} ms",
                        onClick = { showDurationInput = true }
                    )
                }
                PillSlider(
                    value = displayedDuration,
                    onValueChange = {
                        durationIsDragging = false
                        val snapped = snapAnimationDuration(it, range, step)
                        displayedDuration = snapped.toFloat()
                        onDurationChange(snapped)
                    },
                    onDragValueChange = {
                        durationIsDragging = true
                        val snapped = snapAnimationDuration(it, range, step)
                        displayedDuration = snapped.toFloat()
                        onDurationPreview(snapped)
                    },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    step = step.toFloat(),
                    opaqueLiquidThumb = true
                )
            }
        }
    }
    if (showDurationInput) {
        SliderValueInputDialog(
            label = stringResource(R.string.page_turn_speed),
            value = displayedDuration,
            range = range.first.toFloat()..range.last.toFloat(),
            step = step.toFloat(),
            format = { "${formatSliderNumber(it)} ms" },
            onConfirm = {
                displayedDuration = it
                onDurationChange(snapAnimationDuration(it, range, step))
            },
            onDismiss = { showDurationInput = false }
        )
    }
}

private fun snapAnimationDuration(
    value: Float,
    range: IntRange,
    step: Int
): Int = (range.first + (((value - range.first) / step).roundToInt() * step))
    .coerceIn(range)

@Composable
private fun PreviewReadView(
    settings: ReaderThemeSettings,
    backgrounds: List<ReaderBackgroundPreset>,
    customFonts: List<CustomFontPreset>,
    pageTransition: String,
    pageDurationMs: Int,
    sample: String,
    readerTheme: String = "day",
    dark: Boolean = false,
    /** 在示例正文上画一段高亮和划线（主题编辑页用于观察几何参数）。 */
    showAnnotationSamples: Boolean = false,
    /** 预览原排版：按 EPUB WebView 的实际几何换算高亮/划线位置。 */
    bookLayout: Boolean = false
) {
    val density = LocalDensity.current.density
    val effectiveSettings = settings
    val formattedSample = remember(
        sample,
        effectiveSettings.firstLineIndent,
        effectiveSettings.paragraphSpacing,
        effectiveSettings.fontSize,
        density
    ) {
        ReaderParagraphFormatter.applyFirstLineIndent(
            text = sample,
            indentCharacters = effectiveSettings.firstLineIndent,
            textSizePx = effectiveSettings.fontSize * density,
            paragraphSpacingPx = effectiveSettings.paragraphSpacing * density,
            skipFirstNonEmptyParagraph = true
        )
    }
    val previewNotes = remember(sample, showAnnotationSamples) {
        if (showAnnotationSamples) annotationPreviewNotes(sample) else emptyList()
    }
    val annotationStyle = effectiveSettings.toAnnotationStyle()
        .let { if (bookLayout) it.webViewEquivalent() else it }
    var previewView by remember { mutableStateOf<ReadView?>(null) }
    LaunchedEffect(formattedSample) {
        previewView?.let { view ->
            view.setContentProvider { formattedSample }
            view.forceRelayout()
        }
    }
    val background = backgrounds.firstOrNull { it.selectionKey == effectiveSettings.backgroundSelection }
    val baseBackground = backgrounds.firstOrNull { it.selectionKey == effectiveSettings.backgroundColorSelection }
    val imagePreset = background?.takeIf { it.type == ReaderBackgroundType.IMAGE }
    val color = backgroundColor(
        effectiveSettings.backgroundColorSelection,
        baseBackground,
        dark && imagePreset == null
    )
    val imageSource = imagePreset?.resolveImageSource(effectiveSettings.backgroundImageBlurDp)
    val imagePath = imageSource?.path
    val imageBlurDp = imageSource?.runtimeBlurDp ?: 0f
    val textColor = themeTextColor(effectiveSettings, backgrounds, dark)
    val customFontPath = effectiveSettings.fontType.takeIf { it.startsWith("custom:") }
        ?.removePrefix("custom:")
        ?.let { id -> customFonts.firstOrNull { it.id == id }?.path }
    AndroidView(
        factory = { context ->
            ReadView(context).apply {
                previewView = this
                setContentProvider { formattedSample }
                setCallbacks(object : ReadViewCallbacks {
                    override fun onPageChanged(
                        globalPage: Int,
                        chapterIndex: Int,
                        pageInChapter: Int,
                        chapterTotalPages: Int,
                        origin: com.huangder.lumibooks.tts.TtsPageChangeOrigin
                    ) = Unit
                    override fun onMenuToggle() = Unit
                    override fun onLoadingChanged(isLoading: Boolean) = Unit
                    override fun onSelectionStarted(sourceView: PageContentView?) = Unit
                })
            }
        },
        update = { view ->
            view.post {
                view.configure(
                    fontSizePx = effectiveSettings.fontSize * density,
                    theme = readerTheme,
                    chapterCount = 1,
                    startChapter = 0,
                    startPage = view.slotManager.getCurSlot().pageIndex.coerceAtLeast(0),
                    lineHeightMult = effectiveSettings.lineHeight,
                    letterSpacingDp = effectiveSettings.letterSpacing,
                    textAlignment = effectiveSettings.textAlignment,
                    fontType = effectiveSettings.fontType,
                    customFontPath = customFontPath,
                    marginLeftDp = effectiveSettings.marginLeft,
                    marginRightDp = effectiveSettings.marginRight,
                    marginTopDp = effectiveSettings.marginTop,
                    marginBottomDp = effectiveSettings.marginBottom,
                    paragraphSpacingDp = effectiveSettings.paragraphSpacing,
                    firstLineIndent = effectiveSettings.firstLineIndent,
                    bodyFontWeight = effectiveSettings.bodyFontWeight,
                    width = view.width,
                    height = view.height
                )
                view.setAnnotationStyle(annotationStyle)
                view.setSavedNotes(previewNotes)
                view.setReaderBackground(
                    color,
                    textColor,
                    imagePath,
                    effectiveSettings.backgroundImageOpacity,
                    imageBlurDp
                )
                view.setPageTransitionTiming(pageTransition,
                    com.huangder.lumibooks.ui.animation.VisualMotion.durationMillis(pageDurationMs))
                view.setPageTransition(pageTransition)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * 预览用的示例高亮与划线：落在第一段正文（跳过标题）上，方便实时观察几何调整的效果。
 * 只存在于预览 ReadView 中，不写入数据库。
 */
internal fun annotationPreviewNotes(sample: String): List<Note> {
    var cursor = 0
    var nonEmptyLines = 0
    while (cursor < sample.length) {
        val lineEnd = sample.indexOf('\n', cursor).let { if (it < 0) sample.length else it }
        if (sample.substring(cursor, lineEnd).isNotBlank()) {
            nonEmptyLines++
            if (nonEmptyLines == 2) {
                val length = lineEnd - cursor
                if (length < 6) return emptyList()
                val highlightEnd = cursor + minOf(8, length / 3)
                val underlineStart = minOf(highlightEnd + 4, lineEnd - 2)
                val underlineEnd = minOf(underlineStart + 20, lineEnd)
                return listOf(
                    previewNote(-1, cursor, highlightEnd, sample, "highlight", "#66FFC107"),
                    previewNote(-2, underlineStart, underlineEnd, sample, "underline", "#FFE53935")
                )
            }
        }
        cursor = lineEnd + 1
    }
    return emptyList()
}

private fun previewNote(id: Long, start: Int, end: Int, sample: String, type: String, color: String) = Note(
    id = id,
    bookId = "reader-settings-preview",
    chapterIndex = 0,
    startPosition = start,
    endPosition = end,
    selectedText = sample.substring(start, end),
    note = "",
    color = color,
    createdAt = 0L,
    type = type
)

@Composable
private fun rememberSampleText(): String {
    val sample = stringResource(R.string.reader_settings_preview_sample)
    return remember(sample) { sample.repeat(20) }
}

@Composable
private fun CapsuleButton(
    text: String,
    icon: com.huangder.lumibooks.ui.icons.IconPair?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ReaderControlCapsule(
        modifier = modifier,
        onClick = onClick,
        containerColor = if (selected) AppColors.Accent else AppColors.CardBg,
        contentColor = if (selected) AppColors.OnAccent else AppColors.TextPrimary,
        tintColor = AppColors.Accent.takeIf { selected }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                ControlIcon(it.resolve(selected), contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, fontSize = AppType.Caption, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ReaderControlCapsule(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    tintColor: Color? = null,
    content: @Composable () -> Unit
) {
    val motionEnabled = LocalMotionEnabled.current
    val animatedContainer by animateColorAsState(
        targetValue = containerColor,
        animationSpec = if (motionEnabled) {
            tween(LumiMotion.MenuEnterMillis, easing = AppEasing.Smooth)
        } else {
            snap()
        },
        label = "readerControlContainer"
    )
    val animatedContent by animateColorAsState(
        targetValue = contentColor,
        animationSpec = if (motionEnabled) {
            tween(LumiMotion.MenuEnterMillis, easing = AppEasing.Smooth)
        } else {
            snap()
        },
        label = "readerControlContent"
    )
    LiquidGlassSurface(
        controlEdge = true,
        modifier = modifier.heightIn(min = 42.dp),
        shape = AppRoundedCornerShape(AppRadius.full),
        fallbackColor = animatedContainer,
        contentScrimColor = animatedContainer.copy(alpha = 0.86f),
        tintColor = tintColor,
        onClick = onClick,
        interactive = onClick != null
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides animatedContent
        ) {
            content()
        }
    }
}

@Composable
private fun CommandCapsuleButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    secondary: Boolean = false
) {
    ReaderControlCapsule(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (secondary) AppColors.CardBg else AppColors.Accent,
        contentColor = if (secondary) AppColors.TextPrimary else AppColors.OnAccent,
        tintColor = AppColors.Accent.takeUnless { secondary }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpace.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpace.sm))
            Text(text, fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    suffix: String,
    onPreview: (Float) -> Unit,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** 拖动开始/结束；并排滑杆据此临时放宽正在拖动的一侧。 */
    onActiveChange: (Boolean) -> Unit = {}
) {
    val step = (range.endInclusive - range.start) / (steps + 1).coerceAtLeast(1)
    val format: (Float) -> String = {
        formatSliderNumber(it) + suffix.takeIf { unit -> unit.isNotBlank() }
            ?.let { unit -> if (unit == "%") unit else " $unit" }
            .orEmpty()
    }
    var showInputDialog by remember { mutableStateOf(false) }
    var displayedValue by remember { mutableFloatStateOf(value) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!isDragging) displayedValue = value
    }
    LaunchedEffect(isDragging) { onActiveChange(isDragging) }
    // 标签与滑杆是一组，间距只留 2dp，不受外层列间距影响。
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                fontSize = AppType.Footnote,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ClickableSliderValue(
                text = format(displayedValue),
                onClick = { showInputDialog = true }
            )
        }
        ValueSlider(
            label = label,
            value = displayedValue,
            range = range,
            steps = steps,
            onDrag = {
                isDragging = true
                displayedValue = it
                onPreview(it)
            },
            onChange = {
                isDragging = false
                displayedValue = it
                onChange(it)
            }
        )
    }
    if (showInputDialog) {
        SliderValueInputDialog(
            label = label,
            value = displayedValue,
            range = range,
            step = step,
            format = format,
            onConfirm = {
                displayedValue = it
                onChange(it)
            },
            onDismiss = { showInputDialog = false }
        )
    }
}

@Composable
private fun ClickableSliderValue(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(AppRoundedCornerShape(10.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            // 右侧不留内边距，数值与滑杆右端对齐。
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = AppType.Footnote,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
    }
}

@Composable
private fun SliderValueInputDialog(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    @Suppress("UNUSED_PARAMETER") step: Float,
    format: (Float) -> String,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogTransparency = (LocalLiquidGlassTransparency.current - 0.10f).coerceIn(0f, 0.90f)
    val focusRequester = remember { FocusRequester() }
    val initialText = remember(value) { formatSliderNumber(value) }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    val parsedValue = fieldValue.text.toFloatOrNull()?.takeIf { it.isFinite() }
    val canConfirm = parsedValue != null
    val confirm = {
        parsedValue?.let { input ->
            onConfirm(input)
            onDismiss()
        }
        Unit
    }

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        shape = AppRoundedCornerShape(28.dp),
        transparencyOverride = dialogTransparency
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${format(range.start)} ~ ${format(range.endInclusive)}",
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
            Spacer(Modifier.height(18.dp))
            val fieldShape = AppRoundedCornerShape(14.dp)
            val fieldBorder = when {
                fieldValue.text.isBlank() -> AppColors.Divider
                canConfirm -> AppColors.TextPrimary.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.error
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(fieldShape)
                    .background(AppColors.BgGray)
                    .border(1.5.dp, fieldBorder, fieldShape)
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (range.start < 0f) KeyboardType.Text else KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    textStyle = TextStyle(
                        fontSize = AppType.Title,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canConfirm || fieldValue.text.isBlank()) {
                            AppColors.TextPrimary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        textAlign = TextAlign.Center
                    ),
                    decorationBox = { innerField ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (fieldValue.text.isBlank()) {
                                Text(
                                    text = formatSliderNumber(value),
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = AppType.Title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                            innerField()
                        }
                    }
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(44.dp),
                    contentColor = AppColors.TextSecondary
                )
                LiquidGlassTextButton(
                    text = stringResource(R.string.confirm_action),
                    onClick = confirm,
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f).height(44.dp),
                    tintedColor = if (canConfirm) AppColors.Accent else AppColors.TextSecondary
                )
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

private fun snapSliderValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float
): Float {
    val clamped = value.coerceIn(range)
    if (step <= 0f) return clamped
    return (range.start + ((clamped - range.start) / step).roundToInt() * step).coerceIn(range)
}

private fun formatSliderNumber(value: Float): String = when {
    value == value.roundToInt().toFloat() -> value.roundToInt().toString()
    else -> String.format(java.util.Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
}

@Composable
private fun ValueSlider(
    @Suppress("UNUSED_PARAMETER") label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onDrag: (Float) -> Unit,
    onChange: (Float) -> Unit
) {
    val step = (range.endInclusive - range.start) / (steps + 1).coerceAtLeast(1)
    PillSlider(
        value = value.coerceIn(range),
        onValueChange = onChange,
        onDragValueChange = onDrag,
        valueRange = range,
        step = step.coerceAtLeast(0.01f),
        opaqueLiquidThumb = true
    )
}

@Composable
private fun OptionRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    // Only used inside TextPanel: scroll through its 16.dp padding to the panel edge.
    LazyRow(
        modifier = Modifier.horizontalBleed(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(options) { (key, label) ->
            OptionCapsule(
                label = label,
                selected = key == selected,
                onClick = { onSelect(key) }
            )
        }
    }
}

@Composable
private fun OptionCapsule(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ReaderControlCapsule(
        onClick = onClick,
        modifier = modifier,
        containerColor = if (selected) AppColors.Accent else AppColors.BgGray,
        contentColor = if (selected) AppColors.OnAccent else AppColors.TextPrimary,
        tintColor = AppColors.Accent.takeIf { selected }
    ) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
            fontSize = AppType.Caption,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun themeName(suite: ReaderThemeSuite): String = suite.customName ?: when (suite.id) {
    ReaderThemeSuites.PUBLISHER_ID -> stringResource(R.string.reader_theme_publisher)
    ReaderThemeSuites.NIGHT_ID -> stringResource(R.string.theme_suite_night_name)
    ReaderThemeSuites.SEPIA_ID -> stringResource(R.string.theme_suite_sepia_name)
    ReaderThemeSuites.GREEN_ID -> stringResource(R.string.theme_suite_green_name)
    else -> stringResource(R.string.theme_suite_day_name)
}

private fun backgroundColor(selection: String, custom: ReaderBackgroundPreset?, dark: Boolean): Int = when {
    custom?.type == ReaderBackgroundType.COLOR -> runCatching {
        android.graphics.Color.parseColor(custom.value)
    }.getOrDefault(0xFFFBFBFC.toInt()).let { if (dark) darkenReaderSolidColor(it) else it }
    else -> readerPresetBackgroundColor(selection, dark)
}

private fun automaticTextColor(backgroundColor: Int): Int {
    return if (ColorUtils.calculateLuminance(backgroundColor) < 0.42) {
        0xFFE8E8EA.toInt()
    } else {
        0xFF333333.toInt()
    }
}

private fun themeTextColor(
    settings: ReaderThemeSettings,
    backgrounds: List<ReaderBackgroundPreset>,
    dark: Boolean
): Int {
    settings.textColor?.let { return it }
    val background = backgrounds.firstOrNull { it.selectionKey == settings.backgroundSelection }
        ?: return readerPresetTextColor(settings.backgroundSelection, dark)
    val baseBackground = backgrounds.firstOrNull { it.selectionKey == settings.backgroundColorSelection }
    val color = backgroundColor(settings.backgroundColorSelection, baseBackground,
        dark && background.type != ReaderBackgroundType.IMAGE)
    return automaticTextColor(background.dominantColor ?: color)
}
