package com.huangder.lumibooks.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.domain.model.resolveImageSource
import com.huangder.lumibooks.ui.reader.engine.PageContentView
import com.huangder.lumibooks.ui.reader.engine.ReadView
import com.huangder.lumibooks.ui.reader.engine.ReadViewCallbacks
import com.huangder.lumibooks.ui.reader.engine.ReaderParagraphFormatter
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
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.util.LaunchThemeController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

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
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = launchTheme.globalFontMode)
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = launchTheme.liquidGlassTransparency)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = launchTheme.liquidGlassHdrHighlightEnabled)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = launchTheme.darkMode)
            val motionPreferenceValue by dataStoreManager.motionPreference.collectAsState(initial = launchTheme.motionPreference)
            val liquidGlassCapability = rememberLiquidGlassCapability(view = LocalView.current)
            val resolvedAppTheme = effectiveAppTheme(appTheme, liquidGlassCapability)
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = resolvedAppTheme == "material3",
                appTheme = resolvedAppTheme,
                appAccentColor = appAccentColor,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled,
                globalFontMode = globalFontMode,
                motionPreference = MotionPreference.fromStoredValue(motionPreferenceValue)
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalPredictiveBackEnabled provides predictiveBackEnabled
                ) {
                    ConfigurableActivityBack(
                        predictiveBackEnabled = predictiveBackEnabled,
                        onBack = ::finish
                    )
                    ConfigurableBackHandler(
                        enabled = initialMode == MODE_THEMES && state.editingSuite != null,
                        onBack = viewModel::closeEditor
                    )
                    ReaderSettingsPreviewContent(
                        initialMode = initialMode,
                        state = state,
                        onClose = ::finish,
                        onEdit = viewModel::beginEditing,
                        onExitEditor = viewModel::closeEditor,
                        onCreate = viewModel::createTheme,
                        onActivate = viewModel::setActiveTheme,
                        onRename = viewModel::renameTheme,
                        onDelete = viewModel::deleteTheme,
                        onMove = viewModel::moveTheme,
                        onPreviewUpdate = viewModel::previewTheme,
                        onUpdate = viewModel::updateTheme,
                        onSelectColor = viewModel::selectBackgroundColor,
                        onAddColor = viewModel::addBackgroundColor,
                        onAddPhoto = viewModel::addBackgroundPhoto,
                        onRemovePhoto = viewModel::removeBackgroundPhoto,
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
    onPreviewUpdate: (ReaderThemeSettings) -> Unit,
    onUpdate: (ReaderThemeSettings) -> Unit,
    onSelectColor: (String) -> Unit,
    onAddColor: (String) -> Unit,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRemovePhoto: () -> Unit,
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
    val dialogBackdrop = rememberLayerBackdrop()

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.WindowBg) {
        LiquidGlassDialogHost(
            modifier = Modifier.fillMaxSize(),
            backdrop = dialogBackdrop
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(dialogBackdrop)
            ) {
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
                            onMove = onMove
                        )
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
    onMove: (String, Int) -> Unit
) {
    var createDialog by remember { mutableStateOf(false) }
    var renameSuite by remember { mutableStateOf<ReaderThemeSuite?>(null) }
    var deleteSuite by remember { mutableStateOf<ReaderThemeSuite?>(null) }
    val motionEnabled = LocalMotionEnabled.current
    val backgroundBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(backgroundBackdrop)
            .background(AppColors.WindowBg)
    )
    ProvideLiquidGlassBackdrop(backgroundBackdrop) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
                    onClick = onClose,
                    settingsBackButton = true
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.reader_theme_suites),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_theme_suite),
                    onClick = { createDialog = true }
                )
            }
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = AppSpace.md,
                    top = AppSpace.sm,
                    end = AppSpace.md,
                    bottom = AppSpace.xl
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
            ) {
                items(state.suites, key = ReaderThemeSuite::id) { suite ->
                    ThemeSuiteRow(
                        suite = suite,
                        backgrounds = state.backgrounds,
                        active = suite.id == state.activeSuiteId,
                        modifier = Modifier.then(
                            if (motionEnabled) {
                                Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = tween(
                                        LumiMotion.MenuEnterMillis,
                                        easing = AppEasing.Smooth
                                    )
                                )
                            } else {
                                Modifier
                            }
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
            title = {
                Text(
                    stringResource(R.string.delete_theme_suite_title),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Text(
                    stringResource(R.string.delete_theme_suite_message, suite.customName.orEmpty()),
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.delete),
                    onClick = { deleteSuite = null; onDelete(suite.id) },
                    tintedColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { deleteSuite = null }
                )
            }
        )
    }
}

@Composable
private fun ThemeSuiteRow(
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
    val shape = RoundedCornerShape(AppRadius.md)
    val previewShape = RoundedCornerShape(AppRadius.md)
    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (active) 1.5.dp else 0.dp,
                color = if (active) AppColors.Accent else Color.Transparent,
                shape = shape
            ),
        shape = shape,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.84f),
        onClick = onEdit,
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemeSuiteThumbnail(suite.settings, backgrounds, previewShape)
                Spacer(Modifier.width(AppSpace.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        themeName(suite),
                        fontSize = AppType.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        if (active) {
                            stringResource(R.string.theme_suite_current)
                        } else {
                            stringResource(R.string.theme_suite_tap_to_edit)
                        },
                        fontSize = AppType.Caption,
                        color = AppColors.TextSecondary
                    )
                }
                if (active) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = AppColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(AppSpace.sm))
                Icon(
                    Icons.Outlined.DragHandle,
                    contentDescription = stringResource(R.string.theme_suite_drag_to_reorder),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier
                        .size(36.dp)
                        .padding(6.dp)
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
            if (!active || !suite.isBuiltIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!active) {
                        LiquidGlassTextButton(
                            text = stringResource(R.string.theme_suite_set_current),
                            onClick = onActivate,
                            tintedColor = AppColors.Accent
                        )
                    }
                    if (!suite.isBuiltIn) {
                        LiquidGlassIconButton(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.rename),
                            onClick = onRename,
                            size = 40.dp,
                            iconSize = 18.dp
                        )
                        LiquidGlassIconButton(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete),
                            onClick = onDelete,
                            size = 40.dp,
                            iconSize = 18.dp,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSuiteThumbnail(
    settings: ReaderThemeSettings,
    backgrounds: List<ReaderBackgroundPreset>,
    shape: RoundedCornerShape
) {
    val basePreset = backgrounds.firstOrNull {
        it.selectionKey == settings.backgroundColorSelection
    }
    val imagePreset = backgrounds.firstOrNull {
        it.selectionKey == settings.backgroundSelection && it.type == ReaderBackgroundType.IMAGE
    }
    val baseColor = Color(backgroundColor(settings.backgroundColorSelection, basePreset))
    val imageSource = imagePreset?.resolveImageSource(settings.backgroundImageBlurDp)
    val imagePath = imageSource?.path
    val runtimeBlur = (imageSource?.runtimeBlurDp ?: 0f).dp

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(baseColor)
            .border(1.dp, AppColors.Divider, shape)
    ) {
        if (imagePath != null) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(runtimeBlur)
                    .graphicsLayer {
                        alpha = settings.backgroundImageOpacity.coerceIn(0f, 1f)
                    }
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(30) },
                singleLine = true,
                shape = RoundedCornerShape(AppRadius.md)
            )
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = stringResource(R.string.confirm),
                enabled = value.isNotBlank(),
                onClick = { if (value.isNotBlank()) onConfirm(value) },
                tintedColor = AppColors.Accent
            )
        },
        dismissButton = {
            LiquidGlassTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
        }
    )
}

private enum class ThemePanel { NONE, BACKGROUND, TEXT }

@Composable
private fun ThemeEditorScreen(
    suite: ReaderThemeSuite,
    backgrounds: List<ReaderBackgroundPreset>,
    customFonts: List<CustomFontPreset>,
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
    val previewBackdrop = rememberLayerBackdrop()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(onAddPhoto)
    }
    val sample = rememberSampleText()
    val imagePreset = backgrounds.firstOrNull {
        it.selectionKey == suite.settings.backgroundSelection && it.type == ReaderBackgroundType.IMAGE
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(previewBackdrop)
        ) {
            PreviewReadView(
                settings = suite.settings,
                backgrounds = backgrounds,
                customFonts = customFonts,
                pageTransition = "slide",
                pageDurationMs = ReaderPageAnimationSettings.SLIDE_DEFAULT_MS,
                sample = sample
            )
        }
        ProvideLiquidGlassBackdrop(previewBackdrop) {
            ExitButton(
                onExit,
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(AppSpace.sm)
            )
            AnimatedContent(
                targetState = panel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 76.dp)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp / 2),
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
                                settings = suite.settings,
                                backgrounds = backgrounds,
                                hasImage = imagePreset != null,
                                onPreviewUpdate = onPreviewUpdate,
                                onUpdate = onUpdate,
                                onSelectColor = onSelectColor,
                                onAddColor = onAddColor,
                                onAddPhoto = { photoPicker.launch("image/*") },
                                onRemovePhoto = onRemovePhoto
                            )
                            ThemePanel.TEXT -> TextPanel(
                                suite.settings,
                                customFonts,
                                onPreviewUpdate,
                                onUpdate
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
                CapsuleButton(
                    text = stringResource(R.string.reader_background_settings),
                    icon = Icons.Outlined.Palette,
                    selected = panel == ThemePanel.BACKGROUND
                ) {
                    panel = if (panel == ThemePanel.BACKGROUND) {
                        ThemePanel.NONE
                    } else {
                        ThemePanel.BACKGROUND
                    }
                }
                CapsuleButton(
                    text = stringResource(R.string.reader_text_settings),
                    icon = Icons.Outlined.TextFields,
                    selected = panel == ThemePanel.TEXT
                ) {
                    panel = if (panel == ThemePanel.TEXT) ThemePanel.NONE else ThemePanel.TEXT
                }
            }
        }
    }
}

@Composable
private fun ReaderFloatingPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.xl),
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.84f),
        contentAlignment = Alignment.TopStart
    ) {
        content()
    }
}

@Composable
private fun BackgroundPanel(
    settings: ReaderThemeSettings,
    backgrounds: List<ReaderBackgroundPreset>,
    hasImage: Boolean,
    onPreviewUpdate: (ReaderThemeSettings) -> Unit,
    onUpdate: (ReaderThemeSettings) -> Unit,
    onSelectColor: (String) -> Unit,
    onAddColor: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    var colorDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.floating_subtitle_background_color),
            fontSize = AppType.Body,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(listOf("day", "sepia", "green", "night")) { selection ->
                ColorSwatch(
                    color = suitePreviewColor(selection),
                    selected = settings.backgroundColorSelection == selection,
                    onClick = { onSelectColor(selection) }
                )
            }
            items(backgrounds.filter { it.type == ReaderBackgroundType.COLOR }) { preset ->
                ColorSwatch(
                    color = runCatching { Color(android.graphics.Color.parseColor(preset.value)) }
                        .getOrDefault(Color.White),
                    selected = settings.backgroundColorSelection == preset.selectionKey,
                    onClick = { onSelectColor(preset.selectionKey) }
                )
            }
            item {
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.background_custom_color),
                    onClick = { colorDialog = true },
                    size = 36.dp,
                    iconSize = 18.dp
                )
            }
        }
        if (hasImage) {
            SettingSlider(
                stringResource(R.string.background_photo_opacity),
                settings.backgroundImageOpacity * 100f,
                0f..100f,
                99,
                "%",
                onPreview = {
                    onPreviewUpdate(settings.copy(backgroundImageOpacity = it / 100f))
                },
                onChange = {
                    onUpdate(settings.copy(backgroundImageOpacity = it / 100f))
                }
            )
            SettingSlider(
                stringResource(R.string.background_photo_blur),
                settings.backgroundImageBlurDp,
                0f..40f,
                39,
                "dp",
                onPreview = {
                    onPreviewUpdate(settings.copy(backgroundImageBlurDp = it))
                },
                onChange = {
                    onUpdate(settings.copy(backgroundImageBlurDp = it))
                }
            )
            CommandCapsuleButton(
                text = stringResource(R.string.background_remove_photo),
                icon = Icons.Outlined.Delete,
                onClick = onRemovePhoto,
                secondary = true
            )
        } else {
            CommandCapsuleButton(
                text = stringResource(R.string.background_add_photo),
                icon = Icons.Outlined.Image,
                onClick = onAddPhoto
            )
        }
    }
    if (colorDialog) {
        val selectedPreset = backgrounds.firstOrNull {
            it.selectionKey == settings.backgroundColorSelection
        }
        val initialColorHex = remember(settings.backgroundColorSelection, selectedPreset) {
            String.format(
                java.util.Locale.ROOT,
                "#%06X",
                backgroundColor(settings.backgroundColorSelection, selectedPreset) and 0xFFFFFF
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
private fun TextPanel(
    settings: ReaderThemeSettings,
    customFonts: List<CustomFontPreset>,
    onPreviewUpdate: (ReaderThemeSettings) -> Unit,
    onUpdate: (ReaderThemeSettings) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.reader_text_settings),
            fontSize = AppType.Body,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
        Text(
            stringResource(R.string.label_text_color),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0xFF222222, 0xFF5A4636, 0xFF1E5E36, 0xFFE7E7E7).forEach { argb ->
                ColorSwatch(Color(argb), settings.textColor == argb.toInt()) {
                    onUpdate(settings.copy(textColor = argb.toInt()))
                }
            }
            OptionCapsule(
                label = stringResource(R.string.text_color_auto),
                selected = settings.textColor == null,
                onClick = { onUpdate(settings.copy(textColor = null)) }
            )
        }
        SettingSlider(
            stringResource(R.string.label_font_size),
            settings.fontSize,
            12f..28f,
            15,
            "sp",
            onPreview = { onPreviewUpdate(settings.copy(fontSize = it)) },
            onChange = { onUpdate(settings.copy(fontSize = it)) }
        )
        Text(
            stringResource(R.string.font_label),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )
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
        SettingSlider(
            stringResource(R.string.body_font_weight),
            settings.bodyFontWeight.toFloat(),
            100f..900f,
            7,
            "",
            onPreview = {
                onPreviewUpdate(settings.copy(bodyFontWeight = (it / 100).roundToInt() * 100))
            },
            onChange = {
                onUpdate(settings.copy(bodyFontWeight = (it / 100).roundToInt() * 100))
            }
        )
        SettingSlider(
            stringResource(R.string.label_line_height),
            settings.lineHeight,
            1f..2.5f,
            14,
            "×",
            onPreview = { onPreviewUpdate(settings.copy(lineHeight = it)) },
            onChange = { onUpdate(settings.copy(lineHeight = it)) }
        )
        SettingSlider(
            stringResource(R.string.label_letter_spacing),
            settings.letterSpacing,
            0f..10f,
            19,
            "dp",
            onPreview = { onPreviewUpdate(settings.copy(letterSpacing = it)) },
            onChange = { onUpdate(settings.copy(letterSpacing = it)) }
        )
        Text(
            stringResource(R.string.label_text_alignment),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )
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
        SettingSlider(
            stringResource(R.string.label_paragraph_spacing),
            settings.paragraphSpacing, 0f..30f, 29, "dp",
            onPreview = { onPreviewUpdate(settings.copy(paragraphSpacing = it)) },
            onChange = { onUpdate(settings.copy(paragraphSpacing = it)) }
        )
        SettingSlider(
            stringResource(R.string.label_first_line_indent),
            settings.firstLineIndent, 0f..4f, 7, stringResource(R.string.reader_unit_character),
            onPreview = { onPreviewUpdate(settings.copy(firstLineIndent = it)) },
            onChange = { onUpdate(settings.copy(firstLineIndent = it)) }
        )
        SettingSlider(
            stringResource(R.string.label_margin_left), settings.marginLeft, 0f..80f, 79, "dp",
            onPreview = { onPreviewUpdate(settings.copy(marginLeft = it)) },
            onChange = { onUpdate(settings.copy(marginLeft = it)) }
        )
        SettingSlider(
            stringResource(R.string.label_margin_right), settings.marginRight, 0f..80f, 79, "dp",
            onPreview = { onPreviewUpdate(settings.copy(marginRight = it)) },
            onChange = { onUpdate(settings.copy(marginRight = it)) }
        )
        SettingSlider(
            stringResource(R.string.label_margin_top), settings.marginTop, 0f..120f, 119, "dp",
            onPreview = { onPreviewUpdate(settings.copy(marginTop = it)) },
            onChange = { onUpdate(settings.copy(marginTop = it)) }
        )
        SettingSlider(
            stringResource(R.string.label_margin_bottom), settings.marginBottom, 0f..120f, 119, "dp",
            onPreview = { onPreviewUpdate(settings.copy(marginBottom = it)) },
            onChange = { onUpdate(settings.copy(marginBottom = it)) }
        )
    }
}

@Composable
private fun AnimationPreviewScreen(
    state: ReaderSettingsPreviewUiState,
    onClose: () -> Unit,
    onModeChange: (String) -> Unit,
    onDurationPreview: (String, Int) -> Unit,
    onDurationChange: (String, Int) -> Unit
) {
    val mode = state.animationMode
    val duration = state.animationSettings.durationFor(mode)
    val range = ReaderPageAnimationSettings.rangeFor(mode)
    val step = ReaderPageAnimationSettings.stepFor(mode)
    val sample = rememberSampleText()
    val previewBackdrop = rememberLayerBackdrop()
    var showDurationInput by remember(mode) { mutableStateOf(false) }
    var displayedDuration by remember(mode) { mutableFloatStateOf(duration.toFloat()) }
    var durationIsDragging by remember(mode) { mutableStateOf(false) }
    LaunchedEffect(duration, mode) {
        if (!durationIsDragging) displayedDuration = duration.toFloat()
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(previewBackdrop)
        ) {
            PreviewReadView(
                ReaderThemeSettings(),
                emptyList(),
                emptyList(),
                mode,
                displayedDuration.roundToInt(),
                sample
            )
        }
        ProvideLiquidGlassBackdrop(previewBackdrop) {
            ExitButton(
                onClose,
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(AppSpace.sm)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, bottom = 76.dp)
                    .fillMaxWidth()
            ) {
                ReaderFloatingPanel(modifier = Modifier.widthIn(max = 640.dp)) {
                    Column(Modifier.padding(AppSpace.md)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.page_turn_speed),
                                fontSize = AppType.Body,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            ClickableSliderValue(
                                text = "${displayedDuration.roundToInt()} ms",
                                onClick = { showDurationInput = true }
                            )
                        }
                        Spacer(Modifier.height(AppSpace.sm))
                        PillSlider(
                            value = displayedDuration,
                            onValueChange = {
                                durationIsDragging = false
                                val snapped = snapAnimationDuration(it, range, step)
                                displayedDuration = snapped.toFloat()
                                onDurationChange(mode, snapped)
                            },
                            onDragValueChange = {
                                durationIsDragging = true
                                val snapped = snapAnimationDuration(it, range, step)
                                displayedDuration = snapped.toFloat()
                                onDurationPreview(mode, snapped)
                            },
                            valueRange = range.first.toFloat()..range.last.toFloat(),
                            step = step.toFloat(),
                            opaqueLiquidThumb = true
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimationCapsule(stringResource(R.string.page_animation_slide_short), "slide", mode, onModeChange)
                AnimationCapsule(stringResource(R.string.page_animation_scroll_short), "scroll", mode, onModeChange)
                AnimationCapsule(stringResource(R.string.page_animation_fade_short), "fade", mode, onModeChange)
                AnimationCapsule(stringResource(R.string.page_animation_curl_short), "curl", mode, onModeChange)
                DurationCapsule(
                    displayedDuration.roundToInt(),
                    onClick = { showDurationInput = true }
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
                onDurationChange(mode, it.roundToInt())
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
private fun AnimationCapsule(
    label: String,
    mode: String,
    selectedMode: String,
    onClick: (String) -> Unit
) = CapsuleButton(label, Icons.Outlined.Animation, mode == selectedMode) { onClick(mode) }

@Composable
private fun PreviewReadView(
    settings: ReaderThemeSettings,
    backgrounds: List<ReaderBackgroundPreset>,
    customFonts: List<CustomFontPreset>,
    pageTransition: String,
    pageDurationMs: Int,
    sample: String
) {
    val density = LocalDensity.current.density
    val formattedSample = remember(
        sample,
        settings.firstLineIndent,
        settings.paragraphSpacing,
        settings.fontSize,
        density
    ) {
        ReaderParagraphFormatter.applyFirstLineIndent(
            text = sample,
            indentCharacters = settings.firstLineIndent,
            textSizePx = settings.fontSize * density,
            paragraphSpacingPx = settings.paragraphSpacing * density,
            skipFirstNonEmptyParagraph = true
        )
    }
    var previewView by remember { mutableStateOf<ReadView?>(null) }
    LaunchedEffect(formattedSample) {
        previewView?.let { view ->
            view.setContentProvider { formattedSample }
            view.forceRelayout()
        }
    }
    val background = backgrounds.firstOrNull { it.selectionKey == settings.backgroundSelection }
    val baseBackground = backgrounds.firstOrNull { it.selectionKey == settings.backgroundColorSelection }
    val color = backgroundColor(settings.backgroundColorSelection, baseBackground)
    val imagePreset = background?.takeIf { it.type == ReaderBackgroundType.IMAGE }
    val imageSource = imagePreset?.resolveImageSource(settings.backgroundImageBlurDp)
    val imagePath = imageSource?.path
    val imageBlurDp = imageSource?.runtimeBlurDp ?: 0f
    val textColor = settings.textColor ?: automaticTextColor(color)
    val customFontPath = settings.fontType.takeIf { it.startsWith("custom:") }
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
                        chapterTotalPages: Int
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
                    fontSizePx = settings.fontSize * density,
                    theme = "day",
                    chapterCount = 1,
                    startChapter = 0,
                    startPage = view.slotManager.getCurSlot().pageIndex.coerceAtLeast(0),
                    lineHeightMult = settings.lineHeight,
                    letterSpacingDp = settings.letterSpacing,
                    textAlignment = settings.textAlignment,
                    fontType = settings.fontType,
                    customFontPath = customFontPath,
                    marginLeftDp = settings.marginLeft,
                    marginRightDp = settings.marginRight,
                    marginTopDp = settings.marginTop,
                    marginBottomDp = settings.marginBottom,
                    paragraphSpacingDp = settings.paragraphSpacing,
                    firstLineIndent = settings.firstLineIndent,
                    bodyFontWeight = settings.bodyFontWeight,
                    width = view.width,
                    height = view.height
                )
                view.setReaderBackground(
                    color,
                    textColor,
                    imagePath,
                    settings.backgroundImageOpacity,
                    imageBlurDp
                )
                view.setPageTransitionTiming(pageTransition, pageDurationMs)
                view.setPageTransition(pageTransition)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun rememberSampleText(): String {
    val sample = stringResource(R.string.reader_settings_preview_sample)
    return remember(sample) { sample.repeat(20) }
}

@Composable
private fun ExitButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidGlassIconButton(
        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = stringResource(R.string.exit_preview),
        onClick = onClick,
        modifier = modifier,
        settingsBackButton = true
    )
}

@Composable
private fun CapsuleButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    ReaderControlCapsule(
        onClick = onClick,
        containerColor = if (selected) AppColors.Accent else AppColors.CardBg,
        contentColor = if (selected) AppColors.OnAccent else AppColors.TextPrimary,
        tintColor = AppColors.Accent.takeIf { selected }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
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
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(AppRadius.full),
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
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpace.sm))
            Text(text, fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DurationCapsule(duration: Int, onClick: () -> Unit) {
    ReaderControlCapsule(
        onClick = onClick,
        containerColor = AppColors.CardBg,
        contentColor = AppColors.TextPrimary
    ) {
        Text(
            "$duration ms",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            fontSize = AppType.Caption,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AppRadius.md)
    Box(
        Modifier.size(40.dp).background(color, shape)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) AppColors.Accent else AppColors.Divider,
                shape
            ).clickable(onClick = onClick)
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    suffix: String,
    onPreview: (Float) -> Unit,
    onChange: (Float) -> Unit
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = AppType.BodySmall, color = AppColors.TextPrimary)
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = AppType.Caption,
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
    step: Float,
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
            onConfirm(snapSliderValue(input, range, step))
            onDismiss()
        }
        Unit
    }

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
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
            val fieldShape = RoundedCornerShape(14.dp)
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
                        keyboardType = KeyboardType.Decimal,
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun OptionCapsule(label: String, selected: Boolean, onClick: () -> Unit) {
    ReaderControlCapsule(
        onClick = onClick,
        containerColor = if (selected) AppColors.Accent else AppColors.BgGray,
        contentColor = if (selected) AppColors.OnAccent else AppColors.TextPrimary,
        tintColor = AppColors.Accent.takeIf { selected }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            fontSize = AppType.Caption,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun themeName(suite: ReaderThemeSuite): String = suite.customName ?: when (suite.id) {
    ReaderThemeSuites.NIGHT_ID -> stringResource(R.string.theme_suite_night_name)
    ReaderThemeSuites.SEPIA_ID -> stringResource(R.string.theme_suite_sepia_name)
    ReaderThemeSuites.GREEN_ID -> stringResource(R.string.theme_suite_green_name)
    else -> stringResource(R.string.theme_suite_day_name)
}

private fun suitePreviewColor(selection: String): Color = when (selection) {
    ReaderThemeSuites.NIGHT_ID -> Color(0xFF1A1A1A)
    ReaderThemeSuites.SEPIA_ID -> Color(0xFFF5E6D3)
    ReaderThemeSuites.GREEN_ID -> Color(0xFFE8F5E9)
    else -> Color(0xFFFBFBFC)
}

private fun backgroundColor(selection: String, custom: ReaderBackgroundPreset?): Int = when {
    custom?.type == ReaderBackgroundType.COLOR -> runCatching {
        android.graphics.Color.parseColor(custom.value)
    }.getOrDefault(0xFFFBFBFC.toInt())
    selection == ReaderThemeSuites.NIGHT_ID -> 0xFF1A1A1A.toInt()
    selection == ReaderThemeSuites.SEPIA_ID -> 0xFFF5E6D3.toInt()
    selection == ReaderThemeSuites.GREEN_ID -> 0xFFE8F5E9.toInt()
    else -> 0xFFFBFBFC.toInt()
}

private fun automaticTextColor(backgroundColor: Int): Int {
    val r = android.graphics.Color.red(backgroundColor)
    val g = android.graphics.Color.green(backgroundColor)
    val b = android.graphics.Color.blue(backgroundColor)
    return if ((r * 299 + g * 587 + b * 114) / 1000 < 120) {
        0xFFE7E7E7.toInt()
    } else {
        0xFF2C2C2C.toInt()
    }
}
