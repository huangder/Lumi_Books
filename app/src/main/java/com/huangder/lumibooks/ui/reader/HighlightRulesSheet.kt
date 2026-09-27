package com.huangder.lumibooks.ui.reader

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.highlight.HighlightRuleCodec
import com.huangder.lumibooks.highlight.HighlightRuleMatcher
import com.huangder.lumibooks.highlight.HighlightRuleImportPlanner
import com.huangder.lumibooks.highlight.HighlightRuleScanState
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassColumnSheetContainer
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSegmentedControl
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.animateBottomSheetIn
import com.huangder.lumibooks.ui.components.animateBottomSheetOut
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.kyant.backdrop.Backdrop
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalUseMaterial3Theme
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val HighlightRuleDanger: Color
    @Composable get() = if (LocalEInkMode.current) {
        Color.Black
    } else if (LocalUseMaterial3Theme.current) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.84f)
    } else if (LocalIsDarkTheme.current) {
        Color(0xFFE07871)
    } else {
        Color(0xFFD45F58)
    }

private data class HighlightImportPreview(
    val bytes: ByteArray,
    val valid: Int,
    val duplicate: Int,
    val conflict: Int,
    val unsupported: Int
)

@Composable
fun HighlightRulesSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    rules: List<HighlightRule>,
    materializeNotes: Boolean,
    scanState: HighlightRuleScanState,
    eInkModeEnabled: Boolean,
    backdrop: Backdrop? = null,
    onMaterializeChange: (Boolean) -> Unit,
    onSaveRule: (HighlightRule) -> Unit,
    onDuplicateRule: (HighlightRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onRuleEnabledChange: (String, Boolean) -> Unit,
    onMoveRule: (String, Int) -> Unit,
    onImportRules: suspend (ByteArray, Boolean) -> HighlightRuleImportResult,
    onExportRules: (Boolean) -> ByteArray,
    onCancelScan: () -> Unit,
    onRebuild: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetOffset = remember { Animatable(1f) }
    var isClosing by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<HighlightRule?>(null) }
    var deleteRule by remember { mutableStateOf<HighlightRule?>(null) }
    var importPreview by remember { mutableStateOf<HighlightImportPreview?>(null) }
    var exportData by remember { mutableStateOf<ByteArray?>(null) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    LaunchedEffect(visible) {
        sheetOffset.snapTo(1f)
        if (eInkModeEnabled) sheetOffset.snapTo(0f) else sheetOffset.animateBottomSheetIn()
    }
    LaunchedEffect(requestClose) {
        if (requestClose) isClosing = true
    }
    LaunchedEffect(isClosing) {
        if (isClosing) {
            if (eInkModeEnabled) sheetOffset.snapTo(1f) else sheetOffset.animateBottomSheetOut()
            onDismiss()
        }
    }

    fun showImportError(error: Throwable) {
        Toast.makeText(
            context,
            context.getString(
                R.string.highlight_rules_import_failed,
                error.message ?: context.getString(R.string.error)
            ),
            Toast.LENGTH_LONG
        ).show()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { readRuleBytes(context, uri) }
                analyzeImport(bytes, rules)
            }.onSuccess { importPreview = it }
                .onFailure(::showImportError)
        }
    }
    fun writeExport(uri: Uri?) {
        val data = exportData
        exportData = null
        if (uri == null || data == null) return
        scope.launch {
            val succeeded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(data) }
                        ?: error("No output stream")
                }.isSuccess
            }
            Toast.makeText(
                context,
                if (succeeded) R.string.highlight_rules_export_success else R.string.highlight_rules_export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
        ::writeExport
    )
    val zipExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
        ::writeExport
    )

    Box(Modifier.fillMaxSize()) {
        if (editingRule == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    AppColors.Scrim.copy(alpha = 0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f)))
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { isClosing = true }
        )

        LiquidGlassColumnSheetContainer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(if (rules.isEmpty()) 0.56f else 0.90f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 16.dp),
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = backdrop,
            forceFallback = eInkModeEnabled
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.highlight_rules_title),
                        color = AppColors.TextPrimary,
                        fontSize = 20.sp,
                        fontFamily = resolveAppFontFamily(KaiTi),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.highlight_rules_subtitle),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption
                    )
                }
                LiquidGlassIconButton(
                    imageVector = AppIcons.X,
                    contentDescription = stringResource(R.string.close),
                    onClick = { isClosing = true },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = AppColors.BgGray
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 16.dp,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.highlight_rules_note_switch),
                                color = AppColors.TextPrimary,
                                fontSize = AppType.Body,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.highlight_rules_note_switch_hint),
                                color = AppColors.TextSecondary,
                                fontSize = AppType.Caption
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        LiquidGlassSwitch(
                            checked = materializeNotes,
                            onCheckedChange = onMaterializeChange
                        )
                    }
                }

                item {
                    HighlightScanStatus(
                        state = scanState,
                        materializeNotes = materializeNotes,
                        onCancel = onCancelScan,
                        onRebuild = onRebuild
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RuleCommandButton(
                                icon = AppIcons.Plus,
                                label = stringResource(R.string.highlight_rules_add),
                                modifier = Modifier.weight(1f)
                            ) {
                                editingRule = HighlightRule(
                                    id = UUID.randomUUID().toString(),
                                    name = "",
                                    pattern = "",
                                    position = (rules.maxOfOrNull(HighlightRule::position) ?: -1) + 1
                                )
                            }
                            RuleCommandButton(
                                icon = AppIcons.DownloadSimple,
                                label = stringResource(R.string.highlight_rules_import),
                                modifier = Modifier.weight(1f)
                            ) {
                                importLauncher.launch(
                                    arrayOf("application/json", "application/zip", "application/octet-stream")
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RuleCommandButton(
                                icon = AppIcons.UploadSimple,
                                label = stringResource(R.string.highlight_rules_export_json),
                                modifier = Modifier.weight(1f)
                            ) {
                                exportData = onExportRules(false)
                                jsonExportLauncher.launch(exportFileName(false))
                            }
                            RuleCommandButton(
                                icon = AppIcons.Archive,
                                label = stringResource(R.string.highlight_rules_export_zip),
                                modifier = Modifier.weight(1f)
                            ) {
                                exportData = onExportRules(true)
                                zipExportLauncher.launch(exportFileName(true))
                            }
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.highlight_rules_count, rules.size),
                            color = AppColors.TextPrimary,
                            fontSize = AppType.BodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = AppColors.Divider)
                }

                if (rules.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.highlight_rules_empty),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            color = AppColors.TextSecondary,
                            fontSize = AppType.BodySmall
                        )
                    }
                }

                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    HighlightRuleRow(
                        rule = rule,
                        canMoveUp = index > 0,
                        canMoveDown = index < rules.lastIndex,
                        onEnabledChange = { onRuleEnabledChange(rule.id, it) },
                        onEdit = { editingRule = rule },
                        onDuplicate = { onDuplicateRule(rule) },
                        onDelete = { deleteRule = rule },
                        onMoveUp = { onMoveRule(rule.id, -1) },
                        onMoveDown = { onMoveRule(rule.id, 1) }
                    )
                }

                item {
                    Text(
                        stringResource(R.string.highlight_rules_compat_notice),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        }
    }

    editingRule?.let { rule ->
        HighlightRuleEditorSheet(
            rule = rule,
            forceFallback = eInkModeEnabled,
            backdrop = backdrop,
            onDismiss = {
                scope.launch {
                    sheetOffset.snapTo(1f)
                    editingRule = null
                    if (eInkModeEnabled) sheetOffset.snapTo(0f) else sheetOffset.animateBottomSheetIn()
                }
            },
            onSave = {
                onSaveRule(it)
            }
        )
    }

    deleteRule?.let { rule ->
            LiquidGlassAlertDialog(
                onDismissRequest = { deleteRule = null },
                backdrop = backdrop,
                contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
                backgroundScrimColor = Color.Black.copy(alpha = 0.10f),
                backgroundBlurRadius = 0.dp,
                transparencyOverride = 0.24f,
                title = {
                    Text(
                        stringResource(R.string.highlight_rule_delete_title),
                        color = AppColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        stringResource(R.string.highlight_rule_delete_message, rule.name),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.BodySmall
                    )
                },
                confirmButton = {
                    LiquidGlassTextButton(
                        text = stringResource(R.string.delete),
                        onClick = {
                            onDeleteRule(rule.id)
                            deleteRule = null
                        },
                        tintedColor = HighlightRuleDanger,
                        contentColor = Color.White
                    )
                },
                dismissButton = {
                    LiquidGlassTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { deleteRule = null },
                        tintedColor = AppColors.BgGray,
                        contentColor = AppColors.TextPrimary
                    )
                }
        )
    }

    importPreview?.let { preview ->
        LiquidGlassAlertDialog(
            onDismissRequest = { importPreview = null },
            backdrop = backdrop,
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.10f),
            backgroundBlurRadius = 0.dp,
            transparencyOverride = 0.24f,
            title = {
                Text(
                    stringResource(R.string.highlight_rules_import_preview),
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.highlight_rules_import_valid, preview.valid), color = AppColors.TextPrimary)
                    Text(stringResource(R.string.highlight_rules_import_duplicate, preview.duplicate), color = AppColors.TextSecondary)
                    Text(stringResource(R.string.highlight_rules_import_conflict, preview.conflict), color = AppColors.TextSecondary)
                    Text(stringResource(R.string.highlight_rules_import_unsupported, preview.unsupported), color = AppColors.TextSecondary)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiquidGlassTextButton(
                        text = stringResource(R.string.highlight_rules_import_replace),
                        onClick = {
                            importPreview = null
                            scope.launch { importRules(context, preview.bytes, true, onImportRules) }
                        },
                        tintedColor = AppColors.BgGray,
                        contentColor = AppColors.TextPrimary
                    )
                    LiquidGlassTextButton(
                        text = stringResource(R.string.highlight_rules_import_keep),
                        onClick = {
                            importPreview = null
                            scope.launch { importRules(context, preview.bytes, false, onImportRules) }
                        },
                        tintedColor = AppColors.Accent,
                        contentColor = AppColors.OnAccent
                    )
                }
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { importPreview = null },
                    tintedColor = AppColors.BgGray,
                    contentColor = AppColors.TextPrimary
                )
            }
        )
    }
}

@Composable
private fun HighlightScanStatus(
    state: HighlightRuleScanState,
    materializeNotes: Boolean,
    onCancel: () -> Unit,
    onRebuild: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        when (state) {
            HighlightRuleScanState.Idle -> Unit
            is HighlightRuleScanState.Running -> {
                val progress = if (state.total > 0) state.current.toFloat() / state.total else 0f
                Text(
                    if (state.total > 0) {
                        stringResource(R.string.highlight_rules_scan_progress, state.current, state.total)
                    } else {
                        stringResource(R.string.highlight_rules_scan_waiting)
                    },
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Caption
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                LiquidGlassTextButton(
                    text = stringResource(R.string.highlight_rules_cancel_scan),
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                    tintedColor = AppColors.BgGray,
                    contentColor = AppColors.TextPrimary
                )
            }
            is HighlightRuleScanState.Succeeded -> Text(
                stringResource(R.string.highlight_rules_scan_complete, state.noteCount),
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption
            )
            is HighlightRuleScanState.Failed -> Text(
                stringResource(R.string.highlight_rules_scan_failed, state.message),
                color = HighlightRuleDanger,
                fontSize = AppType.Caption
            )
            HighlightRuleScanState.Cancelled -> Text(
                stringResource(R.string.highlight_rules_scan_cancelled),
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption
            )
        }
        if (materializeNotes) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.highlight_rules_rebuild),
                        color = AppColors.TextPrimary,
                        fontSize = AppType.BodySmall
                    )
                    Text(
                        stringResource(R.string.highlight_rules_rebuild_hint),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption
                    )
                }
                LiquidGlassIconButton(
                    imageVector = AppIcons.ArrowsClockwise,
                    contentDescription = stringResource(R.string.highlight_rules_rebuild),
                    onClick = onRebuild,
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = AppColors.BgGray
                )
            }
        }
    }
}

@Composable
private fun RuleCommandButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        tintedColor = AppColors.BgGray,
        contentColor = AppColors.TextPrimary
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HighlightRuleRow(
    rule: HighlightRule,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val error = remember(rule) { HighlightRuleMatcher.validationError(rule) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppColors.BgGray
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                rule.textColor?.let {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(it))
                            .border(1.dp, AppColors.Divider, CircleShape)
                    )
                    Spacer(Modifier.width(9.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        rule.name,
                        color = AppColors.TextPrimary,
                        fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        rule.pattern,
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LiquidGlassSwitch(
                    checked = rule.enabled && error == null,
                    onCheckedChange = onEnabledChange,
                    enabled = error == null
                )
            }
            if (error != null) {
                Text(
                    stringResource(R.string.highlight_rule_unsupported, error),
                    color = HighlightRuleDanger,
                    fontSize = AppType.Caption,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                RuleIconButton(AppIcons.CaretUp, stringResource(R.string.move_up), canMoveUp, onMoveUp)
                RuleIconButton(AppIcons.CaretDown, stringResource(R.string.move_down), canMoveDown, onMoveDown)
                RuleIconButton(AppIcons.PencilSimple, stringResource(R.string.edit), true, onEdit)
                RuleIconButton(AppIcons.ArrowsLeftRight, stringResource(R.string.highlight_rule_duplicate), true, onDuplicate)
                RuleIconButton(AppIcons.Trash, stringResource(R.string.delete), true, onDelete, HighlightRuleDanger)
            }
        }
    }
}

@Composable
private fun RuleIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = AppColors.TextSecondary
) {
    LiquidGlassIconButton(
        imageVector = icon,
        contentDescription = description,
        onClick = onClick,
        enabled = enabled,
        size = 44.dp,
        iconSize = 18.dp,
        contentColor = if (enabled) tint else tint.copy(alpha = 0.35f),
        normalContainerColor = AppColors.BgGray
    )
}

@Composable
private fun HighlightRuleEditorSheet(
    rule: HighlightRule,
    forceFallback: Boolean = false,
    backdrop: Backdrop? = null,
    onDismiss: () -> Unit,
    onSave: (HighlightRule) -> Unit
) {
    var name by remember(rule.id) { mutableStateOf(rule.name) }
    var pattern by remember(rule.id) { mutableStateOf(rule.pattern) }
    var sample by remember(rule.id) { mutableStateOf(rule.sampleText) }
    var colorText by remember(rule.id) {
        mutableStateOf(rule.textColor?.let { String.format(Locale.ROOT, "#%08X", it) }.orEmpty())
    }
    var underlineMode by remember(rule.id) {
        mutableStateOf(
            rule.underlineMode.takeIf {
                it == HighlightRule.UNDERLINE_NONE ||
                    it == HighlightRule.UNDERLINE_STRAIGHT ||
                    it == HighlightRule.UNDERLINE_WAVE
            } ?: HighlightRule.UNDERLINE_NONE
        )
    }
    var bold by remember(rule.id) { mutableStateOf(rule.fontWeight >= 600) }
    var italic by remember(rule.id) { mutableStateOf(rule.isItalic) }
    var showCustomColorDialog by remember(rule.id) { mutableStateOf(false) }
    val parsedColor = remember(colorText) { parseRuleColor(colorText) }
    val colorInvalid = colorText.isNotBlank() && parsedColor == null
    val draft = rule.copy(
        name = name.trim(),
        pattern = pattern,
        targetScope = HighlightRule.TARGET_BODY,
        textColor = parsedColor,
        underlineMode = underlineMode,
        fontWeight = if (bold) 700 else 400,
        isItalic = italic,
        sampleText = sample
    )
    val requiredError = name.isBlank() || pattern.isBlank()
    val regexError = if (pattern.isBlank()) null else HighlightRuleMatcher.validationError(draft)
    val canSave = !requiredError && !colorInvalid && regexError == null
    val preview = remember(draft, sample) { previewText(draft.copy(enabled = true), sample) }
    val underlineLabels = listOf(
        stringResource(R.string.highlight_rule_underline_none),
        stringResource(R.string.highlight_rule_underline_straight),
        stringResource(R.string.highlight_rule_underline_wave)
    )
    val underlineValues = listOf(
        HighlightRule.UNDERLINE_NONE,
        HighlightRule.UNDERLINE_STRAIGHT,
        HighlightRule.UNDERLINE_WAVE
    )

    val sheetOffset = remember { Animatable(1f) }
    var isClosing by remember { mutableStateOf(false) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    LaunchedEffect(Unit) {
        sheetOffset.snapTo(1f)
        if (forceFallback) sheetOffset.snapTo(0f) else sheetOffset.animateBottomSheetIn()
    }
    LaunchedEffect(isClosing) {
        if (isClosing) {
            if (forceFallback) sheetOffset.snapTo(1f) else sheetOffset.animateBottomSheetOut()
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppColors.Scrim.copy(alpha = 0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f))))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { isClosing = true }
        )
        LiquidGlassColumnSheetContainer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 16.dp),
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = backdrop,
            forceFallback = forceFallback
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (rule.name.isBlank()) stringResource(R.string.highlight_rule_new_title)
                        else stringResource(R.string.highlight_rule_edit_title),
                        color = AppColors.TextPrimary,
                        fontSize = 20.sp,
                        fontFamily = resolveAppFontFamily(KaiTi),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidGlassIconButton(
                        imageVector = AppIcons.X,
                        contentDescription = stringResource(R.string.close),
                        onClick = { isClosing = true },
                        size = 44.dp,
                        iconSize = 20.dp,
                        contentColor = AppColors.TextPrimary,
                        normalContainerColor = AppColors.BgGray
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RuleEditorTextField(
                        label = stringResource(R.string.highlight_rule_name),
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true
                    )
                    RuleEditorTextField(
                        label = stringResource(R.string.highlight_rule_pattern),
                        value = pattern,
                        onValueChange = { pattern = it },
                        minLines = 2,
                        maxLines = 5,
                        isError = regexError != null
                    )
                    if (requiredError || regexError != null) {
                        Text(
                            regexError ?: stringResource(R.string.highlight_rule_required),
                            color = HighlightRuleDanger,
                            fontSize = AppType.Caption
                        )
                    }
                    RuleEditorTextField(
                        label = stringResource(R.string.highlight_rule_sample),
                        value = sample,
                        onValueChange = { sample = it },
                        minLines = 2,
                        maxLines = 4
                    )
                    Text(
                        stringResource(R.string.highlight_rule_text_color),
                        fontSize = AppType.BodySmall,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    val swatches = listOf(
                        0xFFE53935,
                        0xFF1976D2,
                        0xFF00897B,
                        0xFF7E57C2,
                        0xFFEF6C00,
                        0xFF202124
                    ).map { it.toInt() }
                    val customColorSelected = parsedColor != null && parsedColor !in swatches
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier
                                .width(maxWidth + 24.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            swatches.forEach { intValue ->
                                HighlightRuleColorSwatch(
                                    color = Color(intValue),
                                    isSelected = parsedColor == intValue,
                                    contentDescription = stringResource(R.string.highlight_rule_text_color),
                                    onClick = {
                                        colorText = String.format(Locale.ROOT, "#%08X", intValue)
                                    }
                                )
                            }
                            HighlightRuleColorSwatch(
                                color = parsedColor?.takeIf { customColorSelected }?.let(::Color)
                                    ?: AppColors.BgGray,
                                isSelected = customColorSelected,
                                contentDescription = stringResource(R.string.highlight_rule_custom_color),
                                onClick = { showCustomColorDialog = true }
                            ) {
                                if (!customColorSelected) {
                                    Icon(
                                        imageVector = AppIcons.Plus,
                                        contentDescription = null,
                                        tint = AppColors.TextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            LiquidGlassTextButton(
                                text = stringResource(R.string.highlight_rule_no_color),
                                onClick = { colorText = "" },
                                tintedColor = AppColors.BgGray,
                                contentColor = AppColors.TextPrimary
                            )
                        }
                    }
                    RuleEditorTextField(
                        label = stringResource(R.string.highlight_rule_text_color),
                        value = colorText,
                        onValueChange = { colorText = it },
                        placeholder = "#FF1976D2",
                        isError = colorInvalid,
                        singleLine = true
                    )
                    Text(
                        stringResource(R.string.highlight_rule_underline),
                        fontSize = AppType.BodySmall,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    LiquidGlassSegmentedControl(
                        itemCount = underlineValues.size,
                        selectedIndex = underlineValues.indexOf(underlineMode).coerceAtLeast(0),
                        onSelected = { underlineMode = underlineValues[it] },
                        modifier = Modifier.fillMaxWidth()
                    ) { index, selected ->
                        Text(
                            underlineLabels[index],
                            color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                            fontSize = AppType.Caption,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.highlight_rule_bold), fontSize = AppType.BodySmall, color = AppColors.TextPrimary)
                            Spacer(Modifier.weight(1f))
                            LiquidGlassSwitch(checked = bold, onCheckedChange = { bold = it })
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.highlight_rule_italic), fontSize = AppType.BodySmall, color = AppColors.TextPrimary)
                            Spacer(Modifier.weight(1f))
                            LiquidGlassSwitch(checked = italic, onCheckedChange = { italic = it })
                        }
                    }
                    Text(
                        stringResource(R.string.highlight_rule_preview),
                        color = AppColors.TextPrimary,
                        fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.BgGray,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (sample.isBlank()) {
                                AnnotatedString(stringResource(R.string.highlight_rule_preview_empty))
                            } else preview,
                            modifier = Modifier.padding(14.dp),
                            color = AppColors.TextPrimary,
                            fontSize = AppType.Body,
                            lineHeight = 25.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                    ) {
                        LiquidGlassTextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { isClosing = true },
                            tintedColor = AppColors.BgGray,
                            contentColor = AppColors.TextPrimary
                        )
                        LiquidGlassTextButton(
                            text = stringResource(R.string.save),
                            onClick = {
                                onSave(draft)
                                isClosing = true
                            },
                            enabled = canSave,
                            tintedColor = AppColors.Accent,
                            contentColor = AppColors.OnAccent
                        )
                    }
                    // Leave room for the control shadow before the sheet's clipped glass edge.
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showCustomColorDialog) {
        HighlightRuleColorDialog(
            initialColor = parsedColor ?: 0xFF202124.toInt(),
            onApply = { value ->
                colorText = String.format(Locale.ROOT, "#%08X", value)
                showCustomColorDialog = false
            },
            onDismiss = { showCustomColorDialog = false }
        )
    }
}

@Composable
private fun RuleEditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 5,
    isError: Boolean = false,
    placeholder: String? = null
) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = AppColors.TextSecondary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 40.dp else 64.dp)
                .clip(shape)
                .background(AppColors.BgGray)
                .then(if (isError) Modifier.border(1.dp, HighlightRuleDanger, shape) else Modifier)
                .padding(10.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier.height(24.dp) else Modifier),
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                decorationBox = { innerTextField ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
private fun HighlightRuleColorSwatch(
    color: Color,
    isSelected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .then(
                if (isSelected) Modifier.border(2.dp, AppColors.Accent, CircleShape)
                else Modifier
            )
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, AppColors.Divider, CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun HighlightRuleColorDialog(
    initialColor: Int,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1] * 100f) }
    var lightness by remember(initialColor) { mutableFloatStateOf(initialHsv[2] * 100f) }
    val sliderColor = android.graphics.Color.HSVToColor(
        floatArrayOf(hue, saturation / 100f, lightness / 100f)
    )
    var value by remember(initialColor) {
        mutableStateOf(String.format(Locale.ROOT, "#%08X", initialColor))
    }
    val parsed = remember(value) { parseRuleColor(value) }
    val invalid = value.isNotBlank() && parsed == null

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.highlight_rule_custom_color),
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = resolveAppFontFamily(KaiTi)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(sliderColor))
                    .border(1.dp, AppColors.Divider, CircleShape)
            )
            HighlightRuleColorSlider(
                label = stringResource(R.string.background_hue),
                value = hue,
                range = 0f..360f,
                onValueChange = {
                    hue = it
                    value = String.format(
                        Locale.ROOT,
                        "#%08X",
                        android.graphics.Color.HSVToColor(floatArrayOf(it, saturation / 100f, lightness / 100f))
                    )
                }
            )
            HighlightRuleColorSlider(
                label = stringResource(R.string.background_saturation),
                value = saturation,
                range = 0f..100f,
                onValueChange = {
                    saturation = it
                    value = String.format(
                        Locale.ROOT,
                        "#%08X",
                        android.graphics.Color.HSVToColor(floatArrayOf(hue, it / 100f, lightness / 100f))
                    )
                }
            )
            HighlightRuleColorSlider(
                label = stringResource(R.string.background_lightness),
                value = lightness,
                range = 0f..100f,
                onValueChange = {
                    lightness = it
                    value = String.format(
                        Locale.ROOT,
                        "#%08X",
                        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation / 100f, it / 100f))
                    )
                }
            )
            RuleEditorTextField(
                label = stringResource(R.string.highlight_rule_text_color),
                value = value,
                onValueChange = { candidate ->
                    value = candidate
                    parseRuleColor(candidate)?.let { color ->
                        FloatArray(3).also { hsv ->
                            android.graphics.Color.colorToHSV(color, hsv)
                            hue = hsv[0]
                            saturation = hsv[1] * 100f
                            lightness = hsv[2] * 100f
                        }
                    }
                },
                singleLine = true,
                isError = invalid,
                placeholder = "#FF1976D2"
            )
            if (invalid) {
                Text(
                    stringResource(R.string.highlight_rule_custom_color_invalid),
                    color = HighlightRuleDanger,
                    fontSize = AppType.Caption
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    tintedColor = AppColors.BgGray,
                    contentColor = AppColors.TextPrimary
                )
                LiquidGlassTextButton(
                    text = stringResource(R.string.save),
                    onClick = { parsed?.let(onApply) },
                    enabled = parsed != null,
                    tintedColor = AppColors.Accent,
                    contentColor = AppColors.OnAccent
                )
            }
        }
    }
}

@Composable
private fun HighlightRuleColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(value.toInt().toString(), fontSize = 12.sp, color = AppColors.TextSecondary)
    }
    com.huangder.lumibooks.ui.components.PillSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        step = 1f,
        onDragValueChange = onValueChange
    )
}

private fun previewText(rule: HighlightRule, sample: String): AnnotatedString {
    if (sample.isEmpty()) return AnnotatedString("")
    val builder = AnnotatedString.Builder(sample)
    HighlightRuleMatcher.match(sample, 0, listOf(rule)).forEach { match ->
        builder.addStyle(
            SpanStyle(
                color = match.style.textColor?.let(::Color) ?: Color.Unspecified,
                fontWeight = if (match.style.fontWeight >= 600) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (match.style.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (match.style.underlineMode == HighlightRule.UNDERLINE_NONE) {
                    TextDecoration.None
                } else {
                    TextDecoration.Underline
                }
            ),
            match.start,
            match.end
        )
    }
    return builder.toAnnotatedString()
}

private fun parseRuleColor(value: String): Int? {
    if (value.isBlank()) return null
    return runCatching { android.graphics.Color.parseColor(value.trim()) }.getOrNull()
}

private fun analyzeImport(bytes: ByteArray, current: List<HighlightRule>): HighlightImportPreview {
    val incoming = HighlightRuleCodec.decode(bytes)
    val analysis = HighlightRuleImportPlanner.analyze(current, incoming)
    return HighlightImportPreview(
        bytes = bytes,
        valid = analysis.supported,
        duplicate = analysis.duplicates,
        conflict = analysis.conflicts,
        unsupported = analysis.unsupported
    )
}

private suspend fun importRules(
    context: android.content.Context,
    bytes: ByteArray,
    replace: Boolean,
    importer: suspend (ByteArray, Boolean) -> HighlightRuleImportResult
) {
    runCatching { importer(bytes, replace) }
        .onSuccess { result ->
            Toast.makeText(
                context,
                context.getString(
                    R.string.highlight_rules_import_result,
                    result.imported,
                    result.skipped,
                    result.renamedConflicts
                ),
                Toast.LENGTH_LONG
            ).show()
        }
        .onFailure { error ->
            Toast.makeText(
                context,
                context.getString(
                    R.string.highlight_rules_import_failed,
                    error.message ?: context.getString(R.string.error)
                ),
                Toast.LENGTH_LONG
            ).show()
        }
}

private fun readRuleBytes(context: android.content.Context, uri: Uri): ByteArray {
    val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open file")
    input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            require(total <= HighlightRuleCodec.MAX_IMPORT_BYTES) { "规则文件不能超过 5 MB" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

private fun exportFileName(zip: Boolean): String {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    return "LUMI-高亮规则-$date.${if (zip) "zip" else "json"}"
}
