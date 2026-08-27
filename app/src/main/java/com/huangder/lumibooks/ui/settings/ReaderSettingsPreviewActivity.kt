package com.huangder.lumibooks.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.ui.reader.engine.PageContentView
import com.huangder.lumibooks.ui.reader.engine.ReadView
import com.huangder.lumibooks.ui.reader.engine.ReadViewCallbacks
import com.huangder.lumibooks.ui.reader.engine.ReaderParagraphFormatter
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs
import kotlin.math.roundToInt

@AndroidEntryPoint
class ReaderSettingsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initialMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_THEMES
        setContent {
            EBookReaderTheme {
                val viewModel: ReaderSettingsPreviewViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsState()
                when {
                    initialMode == MODE_ANIMATIONS -> AnimationPreviewScreen(
                        state = state,
                        onClose = ::finish,
                        onModeChange = viewModel::setAnimationMode,
                        onDurationChange = viewModel::setAnimationDuration
                    )
                    state.editingSuite != null -> ThemeEditorScreen(
                        suite = state.editingSuite!!,
                        backgrounds = state.backgrounds,
                        customFonts = state.customFonts,
                        onExit = viewModel::closeEditor,
                        onUpdate = viewModel::updateTheme,
                        onSelectColor = viewModel::selectBackgroundColor,
                        onAddColor = viewModel::addBackgroundColor,
                        onAddPhoto = viewModel::addBackgroundPhoto,
                        onRemovePhoto = viewModel::removeBackgroundPhoto
                    )
                    else -> ThemeSuiteListScreen(
                        state = state,
                        onClose = ::finish,
                        onEdit = viewModel::beginEditing,
                        onCreate = viewModel::createTheme,
                        onActivate = viewModel::setActiveTheme,
                        onRename = viewModel::renameTheme,
                        onDelete = viewModel::deleteTheme,
                        onMove = viewModel::moveTheme
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_THEMES = "themes"
        const val MODE_ANIMATIONS = "animations"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题套装") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "退出")
                    }
                },
                actions = {
                    IconButton(onClick = { createDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建主题")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.suites, key = ReaderThemeSuite::id) { suite ->
                ThemeSuiteRow(
                    suite = suite,
                    active = suite.id == state.activeSuiteId,
                    onEdit = { onEdit(suite.id) },
                    onActivate = { onActivate(suite.id) },
                    onRename = { renameSuite = suite },
                    onDelete = { deleteSuite = suite },
                    onMove = { delta -> onMove(suite.id, delta) }
                )
            }
        }
    }
    if (createDialog) {
        NameDialog("新建主题套装", "我的主题", onDismiss = { createDialog = false }) {
            createDialog = false
            onCreate(it)
        }
    }
    renameSuite?.let { suite ->
        NameDialog("重命名主题", suite.customName.orEmpty(), onDismiss = { renameSuite = null }) {
            renameSuite = null
            onRename(suite.id, it)
        }
    }
    deleteSuite?.let { suite ->
        AlertDialog(
            onDismissRequest = { deleteSuite = null },
            title = { Text("删除主题") },
            text = { Text("确定删除“${suite.customName}”吗？") },
            confirmButton = {
                TextButton(onClick = { deleteSuite = null; onDelete(suite.id) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteSuite = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun ThemeSuiteRow(
    suite: ReaderThemeSuite,
    active: Boolean,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    val threshold = with(LocalDensity.current) { 54.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(6.dp),
                color = suitePreviewColor(suite.settings.backgroundColorSelection)
            ) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(themeName(suite), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (active) "当前主题" else "点击编辑",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (active) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onActivate) { Text("设为当前") }
            }
            if (!suite.isBuiltIn) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Outlined.Edit, contentDescription = "重命名")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除")
                }
            }
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = "长按拖动排序",
                modifier = Modifier.size(36.dp).padding(6.dp).pointerInput(suite.id) {
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

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it.take(30) }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private enum class ThemePanel { NONE, BACKGROUND, TEXT }

@Composable
private fun ThemeEditorScreen(
    suite: ReaderThemeSuite,
    backgrounds: List<ReaderBackgroundPreset>,
    customFonts: List<CustomFontPreset>,
    onExit: () -> Unit,
    onUpdate: (ReaderThemeSettings) -> Unit,
    onSelectColor: (String) -> Unit,
    onAddColor: (String) -> Unit,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRemovePhoto: () -> Unit
) {
    var panel by remember { mutableStateOf(ThemePanel.NONE) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(onAddPhoto)
    }
    val sample = rememberSampleText()
    val imagePreset = backgrounds.firstOrNull {
        it.selectionKey == suite.settings.backgroundSelection && it.type == ReaderBackgroundType.IMAGE
    }
    Box(Modifier.fillMaxSize()) {
        PreviewReadView(
            settings = suite.settings,
            backgrounds = backgrounds,
            customFonts = customFonts,
            pageTransition = "slide",
            pageDurationMs = ReaderPageAnimationSettings.SLIDE_DEFAULT_MS,
            sample = sample
        )
        ExitButton(onExit, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp))
        if (panel != ThemePanel.NONE) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 84.dp)
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp.dp / 2)),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                when (panel) {
                    ThemePanel.BACKGROUND -> BackgroundPanel(
                        settings = suite.settings,
                        backgrounds = backgrounds,
                        hasImage = imagePreset != null,
                        onUpdate = onUpdate,
                        onSelectColor = onSelectColor,
                        onAddColor = onAddColor,
                        onAddPhoto = { photoPicker.launch("image/*") },
                        onRemovePhoto = onRemovePhoto
                    )
                    ThemePanel.TEXT -> TextPanel(suite.settings, customFonts, onUpdate)
                    ThemePanel.NONE -> Unit
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CapsuleButton(
                text = "背景设置",
                icon = Icons.Outlined.Palette,
                selected = panel == ThemePanel.BACKGROUND
            ) { panel = if (panel == ThemePanel.BACKGROUND) ThemePanel.NONE else ThemePanel.BACKGROUND }
            CapsuleButton(
                text = "文本设置",
                icon = Icons.Outlined.TextFields,
                selected = panel == ThemePanel.TEXT
            ) { panel = if (panel == ThemePanel.TEXT) ThemePanel.NONE else ThemePanel.TEXT }
        }
    }
}

@Composable
private fun BackgroundPanel(
    settings: ReaderThemeSettings,
    backgrounds: List<ReaderBackgroundPreset>,
    hasImage: Boolean,
    onUpdate: (ReaderThemeSettings) -> Unit,
    onSelectColor: (String) -> Unit,
    onAddColor: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    var colorDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("背景颜色", style = MaterialTheme.typography.titleMedium)
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
                IconButton(onClick = { colorDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "自定义颜色")
                }
            }
        }
        if (hasImage) {
            ValueSlider("照片透明度", settings.backgroundImageOpacity, 0f..1f, 20) {
                onUpdate(settings.copy(backgroundImageOpacity = it))
            }
            Text("${(settings.backgroundImageOpacity * 100).roundToInt()}%")
            ValueSlider("照片模糊度", settings.backgroundImageBlurDp, 0f..40f, 39) {
                onUpdate(settings.copy(backgroundImageBlurDp = it))
            }
            Text("${settings.backgroundImageBlurDp.roundToInt()} dp")
            OutlinedButton(onClick = onRemovePhoto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text("移除照片")
            }
        } else {
            Button(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text("添加照片")
            }
        }
    }
    if (colorDialog) {
        var value by remember { mutableStateOf("#FFFFFF") }
        AlertDialog(
            onDismissRequest = { colorDialog = false },
            title = { Text("自定义背景颜色") },
            text = { OutlinedTextField(value, { value = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { colorDialog = false; onAddColor(value) }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { colorDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun TextPanel(
    settings: ReaderThemeSettings,
    customFonts: List<CustomFontPreset>,
    onUpdate: (ReaderThemeSettings) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("文本设置", style = MaterialTheme.typography.titleMedium)
        Text("文字颜色")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0xFF222222, 0xFF5A4636, 0xFF1E5E36, 0xFFE7E7E7).forEach { argb ->
                ColorSwatch(Color(argb), settings.textColor == argb.toInt()) {
                    onUpdate(settings.copy(textColor = argb.toInt()))
                }
            }
            TextButton(onClick = { onUpdate(settings.copy(textColor = null)) }) { Text("自动") }
        }
        SettingSlider("字号", settings.fontSize, 12f..28f, 15, "sp") {
            onUpdate(settings.copy(fontSize = it))
        }
        Text("字体")
        OptionRow(
            options = listOf("system" to "系统", "serif" to "宋体", "kaiti" to "楷体") +
                customFonts.mapIndexed { index, font -> font.fontTypeKey to font.displayName(index) },
            selected = settings.fontType
        ) { onUpdate(settings.copy(fontType = it)) }
        SettingSlider("字重", settings.bodyFontWeight.toFloat(), 100f..900f, 7, "") {
            onUpdate(settings.copy(bodyFontWeight = (it / 100).roundToInt() * 100))
        }
        SettingSlider("行距", settings.lineHeight, 1f..2.5f, 14, "×") {
            onUpdate(settings.copy(lineHeight = it))
        }
        SettingSlider("字距", settings.letterSpacing, 0f..10f, 19, "dp") {
            onUpdate(settings.copy(letterSpacing = it))
        }
        Text("对齐")
        OptionRow(
            listOf(
                ReaderTextAlignment.NATURAL.key to "默认",
                ReaderTextAlignment.LEFT.key to "左对齐",
                ReaderTextAlignment.CENTER.key to "居中",
                ReaderTextAlignment.RIGHT.key to "右对齐",
                ReaderTextAlignment.JUSTIFY.key to "两端"
            ),
            settings.textAlignment.key
        ) { onUpdate(settings.copy(textAlignment = ReaderTextAlignment.fromKey(it))) }
        SettingSlider("段距", settings.paragraphSpacing, 0f..30f, 29, "dp") {
            onUpdate(settings.copy(paragraphSpacing = it))
        }
        SettingSlider("首行缩进", settings.firstLineIndent, 0f..4f, 7, "字") {
            onUpdate(settings.copy(firstLineIndent = it))
        }
        SettingSlider("左边距", settings.marginLeft, 0f..80f, 79, "dp") {
            onUpdate(settings.copy(marginLeft = it))
        }
        SettingSlider("右边距", settings.marginRight, 0f..80f, 79, "dp") {
            onUpdate(settings.copy(marginRight = it))
        }
        SettingSlider("上边距", settings.marginTop, 0f..120f, 119, "dp") {
            onUpdate(settings.copy(marginTop = it))
        }
        SettingSlider("下边距", settings.marginBottom, 0f..120f, 119, "dp") {
            onUpdate(settings.copy(marginBottom = it))
        }
    }
}

@Composable
private fun AnimationPreviewScreen(
    state: ReaderSettingsPreviewUiState,
    onClose: () -> Unit,
    onModeChange: (String) -> Unit,
    onDurationChange: (String, Int) -> Unit
) {
    val mode = state.animationMode
    val duration = state.animationSettings.durationFor(mode)
    val range = ReaderPageAnimationSettings.rangeFor(mode)
    val step = ReaderPageAnimationSettings.stepFor(mode)
    val sample = rememberSampleText()
    Box(Modifier.fillMaxSize()) {
        PreviewReadView(
            ReaderThemeSettings(),
            emptyList(),
            emptyList(),
            mode,
            duration,
            sample
        )
        ExitButton(onClose, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp))
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 86.dp).fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("翻页速度", style = MaterialTheme.typography.titleMedium)
                    Text("$duration ms", color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = duration.toFloat(),
                    onValueChange = {
                        val snapped = range.first +
                            (((it - range.first) / step).roundToInt() * step)
                        onDurationChange(mode, snapped)
                    },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimationCapsule("滑动", "slide", mode, onModeChange)
            AnimationCapsule("渐变", "fade", mode, onModeChange)
            AnimationCapsule("卷曲", "curl", mode, onModeChange)
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text("$duration ms", Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontSize = 12.sp)
            }
        }
    }
}

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
    val imagePath = background?.takeIf { it.type == ReaderBackgroundType.IMAGE }?.value
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
                    width = view.width,
                    height = view.height
                )
                view.setReaderBackground(
                    color,
                    textColor,
                    imagePath,
                    settings.backgroundImageOpacity,
                    settings.backgroundImageBlurDp
                )
                view.setBodyFontWeight(settings.bodyFontWeight)
                view.setPageTransitionTiming(pageTransition, pageDurationMs)
                view.setPageTransition(pageTransition)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun rememberSampleText(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.assets.open("theme_preview.txt").bufferedReader().use { it.readText() }
        }.getOrElse {
            "第一章 光落在书页上\n\n清晨的风越过窗台，纸张轻轻翻动。阅读让时间慢下来，也让遥远的声音在此刻变得清晰。"
                .repeat(20)
        }
    }
}

@Composable
private fun ExitButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50)) {
        Icon(Icons.Outlined.Close, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("退出")
    }
}

@Composable
private fun CapsuleButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = if (selected) {
        androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
    }
    FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(50), colors = colors) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp)); Text(text, fontSize = 12.sp)
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).background(color, RoundedCornerShape(6.dp))
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(6.dp)
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
    onChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label); Text("${String.format("%.1f", value)} $suffix")
    }
    ValueSlider(label, value, range, steps, onChange)
}

@Composable
private fun ValueSlider(
    @Suppress("UNUSED_PARAMETER") label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
}

@Composable
private fun OptionRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { (key, label) ->
            if (key == selected) Button(onClick = { onSelect(key) }) { Text(label) }
            else OutlinedButton(onClick = { onSelect(key) }) { Text(label) }
        }
    }
}

private fun themeName(suite: ReaderThemeSuite): String = suite.customName ?: when (suite.id) {
    ReaderThemeSuites.NIGHT_ID -> "夜间"
    ReaderThemeSuites.SEPIA_ID -> "羊皮纸"
    ReaderThemeSuites.GREEN_ID -> "护眼"
    else -> "日间"
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
