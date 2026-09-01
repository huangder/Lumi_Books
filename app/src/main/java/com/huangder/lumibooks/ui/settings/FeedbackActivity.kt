package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.util.diagnostics.DiagnosticBundleRequest
import com.huangder.lumibooks.util.diagnostics.DiagnosticIssueType
import com.huangder.lumibooks.util.diagnostics.DiagnosticLogger
import com.huangder.lumibooks.util.diagnostics.DiagnosticSessionManager
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class FeedbackActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val appAccentColor by dataStoreManager.appAccentColor.collectAsState(initial = DEFAULT_APP_ACCENT_HEX)
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = "system")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val capability = rememberLiquidGlassCapability(view = LocalView.current)
            val resolvedAppTheme = effectiveAppTheme(appTheme, capability)

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = resolvedAppTheme == "material3",
                appTheme = resolvedAppTheme,
                appAccentColor = appAccentColor,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled,
                globalFontMode = globalFontMode
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = { finish() }
                )
                FeedbackOverviewPage(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun FeedbackOverviewPage(onBack: () -> Unit) {
    val context = LocalContext.current
    DetailPage(title = stringResource(R.string.feedback_title), onBack = onBack) {
        Text(stringResource(R.string.feedback_desc), fontSize = AppType.BodySmall, color = AppColors.TextSecondary, modifier = Modifier.padding(horizontal = AppSpace.lg))
        Spacer(Modifier.height(AppSpace.md))
        FeedbackLinkSection(
            label = stringResource(R.string.diagnostic_capture_title),
            title = stringResource(R.string.diagnostic_entry_desc),
            onClick = { context.startActivity(Intent(context, DiagnosticActivity::class.java)) }
        )
        Spacer(Modifier.height(AppSpace.lg))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.lg).shadow(12.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000)).clip(RoundedCornerShape(AppRadius.lg)).background(AppColors.CardBg).padding(AppSpace.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.feedback_qr), stringResource(R.string.feedback_qr_desc), Modifier.size(220.dp).clip(RoundedCornerShape(AppRadius.md)), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(AppSpace.md))
            Text(stringResource(R.string.feedback_thanks), fontSize = AppType.Body, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(AppSpace.lg))
        FeedbackLinkSection(stringResource(R.string.feedback_website), "huangder.top") { openFeedbackUrl(context, "https://huangder.top") }
        Spacer(Modifier.height(AppSpace.md))
        FeedbackLinkSection(stringResource(R.string.feedback_github_issues), stringResource(R.string.feedback_github_issues_desc)) { openFeedbackUrl(context, "https://github.com/huangder/Lumi_Books/issues") }
        Spacer(Modifier.height(AppSpace.md))
        FeedbackLinkSection(stringResource(R.string.feedback_qq_channel), stringResource(R.string.feedback_qq_channel_desc)) { openFeedbackUrl(context, "https://pd.qq.com/s/29t6pms4a?b=9") }
    }
}

private fun openFeedbackUrl(context: android.content.Context, url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show() }

@Composable
fun DiagnosticPage(
    onBack: () -> Unit,
    diagnosticSessionManager: DiagnosticSessionManager,
    diagnosticLogger: DiagnosticLogger
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val restoredSession = remember { diagnosticLogger.activeSession() }
    var issueType by remember { mutableStateOf(restoredSession?.issueType ?: DiagnosticIssueType.OTHER) }
    var description by remember { mutableStateOf("") }
    var screenshotUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var activeSessionId by remember { mutableStateOf<String?>(restoredSession?.id) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        screenshotUris = uris.take(3)
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = generatedFile ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("Unable to open destination")
            output.use { destination -> file.inputStream().use { it.copyTo(destination) } }
        }.onSuccess { Toast.makeText(context, R.string.diagnostic_saved, Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, it.message ?: "Save failed", Toast.LENGTH_LONG).show() }
    }

    fun share(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("diagnostic", uri)
            }, context.getString(R.string.diagnostic_share)))
        }.onFailure { Toast.makeText(context, it.message ?: "Share failed", Toast.LENGTH_LONG).show() }
    }

    fun generate() {
        isGenerating = true
        errorMessage = null
        scope.launch {
            runCatching {
                val sessionId = activeSessionId
                if (sessionId != null) diagnosticSessionManager.stop(sessionId)
                diagnosticSessionManager.buildBundle(
                    DiagnosticBundleRequest(
                        sessionId = sessionId,
                        issueType = issueType,
                        userDescription = description.trim().ifEmpty {
                            context.getString(R.string.diagnostic_missing_description)
                        },
                        screenshotUris = screenshotUris,
                        includePreviousCrash = diagnosticLogger.hasPreviousCrash()
                    )
                )
            }.onSuccess { file ->
                generatedFile = file
                activeSessionId = null
                diagnosticLogger.clearPreviousCrash()
                share(file)
            }.onFailure { errorMessage = it.message ?: "Unknown error" }
            isGenerating = false
        }
    }

    DetailPage(
        title = stringResource(R.string.diagnostic_capture_title),
        onBack = onBack
    ) {
        DiagnosticComposer(
            issueType = issueType,
            onIssueTypeChange = { issueType = it },
            description = description,
            onDescriptionChange = { description = it },
            screenshotCount = screenshotUris.size,
            isGenerating = isGenerating,
            hasPreviousCrash = diagnosticLogger.hasPreviousCrash(),
            hasActiveSession = activeSessionId != null,
            hasGeneratedFile = generatedFile != null,
            hasRecordingNote = activeSessionId != null,
            onAddScreenshot = { screenshotPicker.launch("image/*") },
            onStart = { showConsent = true },
            onGenerate = { showPreview = true },
            onGenerateRecent = {
                issueType = DiagnosticIssueType.OTHER
                if (description.trim().isEmpty()) description = context.getString(R.string.diagnostic_recent_description)
                showPreview = true
            },
            onGeneratePrevious = {
                issueType = DiagnosticIssueType.OTHER
                description = context.getString(R.string.diagnostic_previous_crash)
                showPreview = true
            },
            onShare = { generatedFile?.let(::share) },
            onSave = { generatedFile?.let { savePicker.launch(it.name) } }
        )

        errorMessage?.let {
            Text(
                text = stringResource(R.string.diagnostic_failed, it),
                color = AppColors.Accent,
                fontSize = AppType.BodySmall,
                modifier = Modifier.padding(horizontal = AppSpace.lg)
            )
        }

        if (showPreview) {
            LiquidGlassAlertDialog(
                onDismissRequest = { showPreview = false },
                title = { Text(stringResource(R.string.diagnostic_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                        Text(stringResource(R.string.diagnostic_preview))
                        val events = diagnosticLogger.snapshot()
                        val firstTimestamp = events.minOfOrNull { it.timestamp }
                        val lastTimestamp = events.maxOfOrNull { it.timestamp }
                        Text(stringResource(R.string.diagnostic_preview_files, screenshotUris.size, events.size))
                        if (firstTimestamp != null && lastTimestamp != null) {
                            Text(
                                stringResource(
                                    R.string.diagnostic_preview_range,
                                    formatDiagnosticTime(firstTimestamp),
                                    formatDiagnosticTime(lastTimestamp)
                                ),
                                color = AppColors.TextSecondary,
                                fontSize = AppType.Caption
                            )
                        }
                        if (screenshotUris.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
                            ) {
                                screenshotUris.forEach { uri -> DiagnosticScreenshotPreview(uri) }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPreview = false; generate() }, enabled = !isGenerating) {
                        Text(stringResource(R.string.diagnostic_stop))
                    }
                },
                dismissButton = { TextButton(onClick = { showPreview = false }) { Text(stringResource(android.R.string.cancel)) } }
            )
        }

        if (showConsent) {
            LiquidGlassAlertDialog(
                onDismissRequest = { showConsent = false },
                title = { Text(stringResource(R.string.diagnostic_consent_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
                    ) {
                        Text(
                            stringResource(R.string.diagnostic_consent_body),
                            color = AppColors.TextPrimary,
                            fontSize = AppType.BodySmall
                        )
                        Text(
                            stringResource(R.string.diagnostic_consent_body_extra),
                            color = AppColors.TextSecondary,
                            fontSize = AppType.BodySmall
                        )
                    }
                },
                confirmButton = {
                    LiquidGlassButton(
                        onClick = {
                        activeSessionId = diagnosticSessionManager.start(issueType).id
                        showConsent = false
                        },
                        tintedColor = AppColors.Accent,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(AppRadius.capsule),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(stringResource(R.string.diagnostic_consent_agree), color = Color.White)
                    }
                },
                dismissButton = {
                    LiquidGlassButton(
                        onClick = { showConsent = false },
                        contentColor = AppColors.TextPrimary,
                        shape = RoundedCornerShape(AppRadius.capsule),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(stringResource(R.string.diagnostic_consent_decline), color = AppColors.TextPrimary)
                    }
                }
            )
        }

    }
}

@Composable
private fun DiagnosticComposer(
    issueType: DiagnosticIssueType,
    onIssueTypeChange: (DiagnosticIssueType) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    screenshotCount: Int,
    isGenerating: Boolean,
    hasPreviousCrash: Boolean,
    hasActiveSession: Boolean,
    hasGeneratedFile: Boolean,
    hasRecordingNote: Boolean,
    onAddScreenshot: () -> Unit,
    onStart: () -> Unit,
    onGenerate: () -> Unit,
    onGenerateRecent: () -> Unit,
    onGeneratePrevious: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        Text(
            stringResource(R.string.diagnostic_step_one),
            fontSize = AppType.Section,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
        Text(
            stringResource(R.string.diagnostic_step_one_desc),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(AppColors.CardBg)
                .padding(AppSpace.md),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BugReport, contentDescription = null, tint = AppColors.Accent)
                Spacer(Modifier.width(AppSpace.sm))
                Text(
                    stringResource(R.string.diagnostic_capture_title),
                    fontSize = AppType.Title,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
            }
            Text(
                stringResource(R.string.diagnostic_issue_type),
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary
            )
            Column(verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                DiagnosticIssueType.values().toList().chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
                    ) {
                        row.forEach { type ->
                            DiagnosticIssueChip(
                                label = issueLabel(type),
                                selected = issueType == type,
                                onClick = { onIssueTypeChange(type) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        TextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.diagnostic_description_label)) },
            placeholder = { Text(stringResource(R.string.diagnostic_description_hint)) },
            minLines = 3,
            maxLines = 6,
            singleLine = false,
            shape = RoundedCornerShape(26.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AppColors.BgGray,
                unfocusedContainerColor = AppColors.BgGray,
                disabledContainerColor = AppColors.BgGray,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
        LiquidGlassButton(
            onClick = onAddScreenshot,
            enabled = screenshotCount < 3 && !isGenerating,
            tintedColor = AppColors.BgGray,
            contentColor = AppColors.TextPrimary,
            shape = RoundedCornerShape(AppRadius.capsule),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(AppSpace.xs))
            Text("${stringResource(R.string.diagnostic_add_screenshot)} ($screenshotCount/3)")
        }
        if (hasPreviousCrash) Text(stringResource(R.string.diagnostic_previous_crash), fontSize = AppType.Caption, color = AppColors.TextSecondary)
        if (hasRecordingNote) Text(stringResource(R.string.diagnostic_recording_note), fontSize = AppType.Caption, color = AppColors.TextSecondary)
        Text(stringResource(R.string.diagnostic_step_two), fontSize = AppType.Section, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        Text(stringResource(R.string.diagnostic_step_two_desc), fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            if (!hasActiveSession) {
                LiquidGlassButton(
                    onClick = onStart,
                    enabled = description.trim().isNotEmpty() && !isGenerating,
                    tintedColor = AppColors.Accent,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(AppRadius.capsule),
                    prominentShadow = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(AppSpace.xs))
                    Text(stringResource(R.string.diagnostic_start), color = Color.White)
                }
            } else {
                LiquidGlassButton(
                    onClick = onGenerate,
                    enabled = !isGenerating,
                    tintedColor = AppColors.Accent,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(AppRadius.capsule),
                    prominentShadow = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(AppSpace.xs))
                    Text(stringResource(R.string.diagnostic_stop), color = Color.White)
                }
            }
            if (hasPreviousCrash && !hasActiveSession) {
                LiquidGlassButton(
                    onClick = onGeneratePrevious,
                    enabled = !isGenerating,
                    tintedColor = AppColors.BgGray,
                    contentColor = AppColors.TextPrimary,
                    shape = RoundedCornerShape(AppRadius.capsule),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(stringResource(R.string.diagnostic_previous_crash)) }
            }
        }
        LiquidGlassButton(
            onClick = onGenerateRecent,
            enabled = !isGenerating,
            tintedColor = AppColors.BgGray,
            contentColor = AppColors.TextPrimary,
            shape = RoundedCornerShape(AppRadius.capsule),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(stringResource(R.string.diagnostic_export_recent))
        }
        if (isGenerating) Text(stringResource(R.string.diagnostic_generating), color = AppColors.TextSecondary)
        if (hasGeneratedFile) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
            ) {
                LiquidGlassButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f).height(52.dp),
                    tintedColor = AppColors.BgGray,
                    contentColor = AppColors.TextPrimary,
                    shape = RoundedCornerShape(AppRadius.capsule)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.width(AppSpace.xs))
                    Text(stringResource(R.string.diagnostic_share), maxLines = 1)
                }
                LiquidGlassButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).height(52.dp),
                    tintedColor = AppColors.BgGray,
                    contentColor = AppColors.TextPrimary,
                    shape = RoundedCornerShape(AppRadius.capsule)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Spacer(Modifier.width(AppSpace.xs))
                    Text(stringResource(R.string.diagnostic_save), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticIssueChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) AppColors.Accent.copy(alpha = 0.14f) else AppColors.BgGray)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AppColors.Accent else AppColors.Divider,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.Accent else AppColors.TextPrimary,
            fontSize = AppType.BodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun DiagnosticScreenshotPreview(uri: Uri) {
    val context = LocalContext.current
    val bitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }.value
    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = stringResource(R.string.diagnostic_screenshot_preview),
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(AppRadius.sm)),
            contentScale = ContentScale.Crop
        )
    } else {
        Spacer(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(AppColors.CardBg)
        )
    }
}

private fun formatDiagnosticTime(timestamp: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(timestamp))

@Composable
private fun issueLabel(type: DiagnosticIssueType): String = when (type) {
    DiagnosticIssueType.IMPORT -> stringResource(R.string.diagnostic_issue_import)
    DiagnosticIssueType.OPEN_BOOK -> stringResource(R.string.diagnostic_issue_open_book)
    DiagnosticIssueType.RENDER -> stringResource(R.string.diagnostic_issue_render)
    DiagnosticIssueType.PAGE_TURN -> stringResource(R.string.diagnostic_issue_page_turn)
    DiagnosticIssueType.SELECTION -> stringResource(R.string.diagnostic_issue_selection)
    DiagnosticIssueType.TTS -> stringResource(R.string.diagnostic_issue_tts)
    DiagnosticIssueType.SYNC -> stringResource(R.string.diagnostic_issue_sync)
    DiagnosticIssueType.BACKUP -> stringResource(R.string.diagnostic_issue_backup)
    DiagnosticIssueType.OTHER -> stringResource(R.string.diagnostic_issue_other)
}

@Composable
private fun FeedbackLinkSection(
    label: String,
    title: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
    ) {
        Text(
            text = label,
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(bottom = AppSpace.xs)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(AppRadius.md), ambientColor = Color(0x04000000), spotColor = Color(0x04000000))
                .clip(RoundedCornerShape(AppRadius.md))
                .background(AppColors.CardBg)
                .clickable(onClick = onClick)
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(AppSpace.sm))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
