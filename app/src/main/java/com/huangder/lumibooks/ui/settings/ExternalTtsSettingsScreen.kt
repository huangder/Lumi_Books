package com.huangder.lumibooks.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.ExternalTtsConfig
import com.huangder.lumibooks.tts.ExternalTtsProtocol
import com.huangder.lumibooks.tts.ExternalTtsSettings
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
private val OPENAI_VOICE_PRESETS = listOf(
    "alloy",
    "ash",
    "ballad",
    "coral",
    "echo",
    "fable",
    "nova",
    "onyx",
    "sage",
    "shimmer",
    "verse"
)

private val MIMO_VOICE_PRESETS = listOf(
    "mimo_default",
    "冰糖",
    "茉莉",
    "苏打",
    "白桦",
    "Mia",
    "Chloe",
    "Milo",
    "Dean"
)


@Composable
fun ExternalTtsSettingsDetail(
    viewModel: SettingsViewModel,
    onConfigure: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSettings = uiState.externalTtsSettings
    val isEnabled = currentSettings.enabled
    val hasToken = uiState.externalTtsHasToken

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        ExternalTtsStatusCard(settings = currentSettings, onConfigure = onConfigure)

        if (!isEnabled) {
            ExternalTtsPrimaryButton(
                label = stringResource(R.string.external_tts_configure),
                onClick = onConfigure
            )
        }

        if (isEnabled && hasToken) {
            ExternalTtsSecondaryButton(
                label = stringResource(R.string.external_tts_test_connection),
                icon = Icons.Outlined.NetworkCheck,
                onClick = viewModel::testExternalTtsConnection
            )
        }

        if (isEnabled) {
            ExternalTtsSecondaryButton(
                label = stringResource(R.string.external_tts_disable_only),
                icon = Icons.Outlined.CloudOff,
                onClick = { viewModel.disableExternalTts(clearKey = false) }
            )
            ExternalTtsSecondaryButton(
                label = stringResource(R.string.external_tts_disable_and_clear),
                icon = Icons.Outlined.DeleteOutline,
                destructive = true,
                onClick = { viewModel.disableExternalTts(clearKey = true) }
            )
        } else if (hasToken) {
            ExternalTtsSecondaryButton(
                label = stringResource(R.string.external_tts_clear_key),
                icon = Icons.Outlined.DeleteOutline,
                destructive = true,
                onClick = viewModel::clearExternalTtsToken
            )
        }

        ExternalTtsDisclosureCard()
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun ExternalTtsConfigurationDetail(
    viewModel: SettingsViewModel,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    if (!uiState.externalTtsSettingsLoaded) return
    val currentSettings = uiState.externalTtsSettings
    val isEnabled = currentSettings.enabled
    val hasToken = uiState.externalTtsHasToken
    var draftProtocol by rememberSaveable { mutableStateOf(currentSettings.protocol) }
    var draftBaseUrl by rememberSaveable { mutableStateOf(currentSettings.baseUrl) }
    var draftModel by rememberSaveable { mutableStateOf(currentSettings.model) }
    var draftVoice by rememberSaveable { mutableStateOf(currentSettings.voice) }
    var draftStyle by rememberSaveable { mutableStateOf(currentSettings.styleInstructions) }
    var draftAllowHttp by rememberSaveable { mutableStateOf(currentSettings.allowHttp) }
    var draftIsDirty by rememberSaveable { mutableStateOf(false) }
    var token by rememberSaveable { mutableStateOf("") }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var tokenError by rememberSaveable { mutableStateOf(false) }
    var baseUrlError by rememberSaveable { mutableStateOf(false) }
    var pendingRequest by remember { mutableStateOf<PendingExternalTtsAction?>(null) }
    var showHttpWarning by rememberSaveable { mutableStateOf(false) }
    val consentRequired = !isEnabled ||
        currentSettings.consentVersion < ExternalTtsConfig.CONSENT_VERSION

    fun loadDraft(settings: ExternalTtsSettings) {
        draftProtocol = settings.protocol
        draftBaseUrl = settings.baseUrl
        draftModel = settings.model
        draftVoice = settings.voice
        draftStyle = settings.styleInstructions
        draftAllowHttp = settings.allowHttp
    }

    LaunchedEffect(currentSettings) {
        if (!draftIsDirty) loadDraft(currentSettings)
    }

    fun buildDraft(): ExternalTtsSettings = ExternalTtsSettings(
        enabled = isEnabled,
        protocol = draftProtocol,
        baseUrl = draftBaseUrl.trim(),
        model = draftModel.trim(),
        voice = draftVoice.trim(),
        styleInstructions = draftStyle.trim(),
        allowHttp = draftAllowHttp,
        consentVersion = currentSettings.consentVersion,
        consentAcceptedAt = currentSettings.consentAcceptedAt
    )

    fun applyDefaults() {
        val defaults = ExternalTtsConfig.defaults(draftProtocol)
        draftBaseUrl = defaults.baseUrl
        draftModel = defaults.model
        draftVoice = defaults.voice
        draftIsDirty = true
    }

    fun saveConfiguration() {
        viewModel.enableExternalTts(buildDraft(), token) {
            onSaved()
        }
    }

    fun requestEnable() {
        tokenError = false
        baseUrlError = false
        if (!hasToken && token.isBlank()) {
            tokenError = true
            if (draftBaseUrl.isBlank()) baseUrlError = true
            return
        }
        if (draftBaseUrl.isBlank()) {
            baseUrlError = true
            return
        }
        if (consentRequired) {
            pendingRequest = PendingExternalTtsAction.Enable
        } else {
            saveConfiguration()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        Text(
            text = stringResource(R.string.external_tts_choose_protocol),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )

        ExternalTtsProtocolCard(
            title = stringResource(R.string.external_tts_protocol_openai),
            description = stringResource(R.string.external_tts_protocol_openai_desc),
            selected = draftProtocol == ExternalTtsProtocol.OPENAI_SPEECH,
            onClick = {
                if (draftProtocol != ExternalTtsProtocol.OPENAI_SPEECH) {
                    draftProtocol = ExternalTtsProtocol.OPENAI_SPEECH
                    applyDefaults()
                }
            }
        )
        ExternalTtsProtocolCard(
            title = stringResource(R.string.external_tts_protocol_mimo),
            description = stringResource(R.string.external_tts_protocol_mimo_desc),
            selected = draftProtocol == ExternalTtsProtocol.MIMO_CHAT,
            onClick = {
                if (draftProtocol != ExternalTtsProtocol.MIMO_CHAT) {
                    draftProtocol = ExternalTtsProtocol.MIMO_CHAT
                    applyDefaults()
                }
            }
        )

        Text(
            text = stringResource(R.string.external_tts_more_services_planned),
            modifier = Modifier.fillMaxWidth(),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Start
        )

        OutlinedTextField(
            value = draftBaseUrl,
            onValueChange = {
                draftBaseUrl = it
                draftIsDirty = true
                baseUrlError = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.external_tts_base_url_label)) },
            placeholder = {
                Text(
                    when (draftProtocol) {
                        ExternalTtsProtocol.OPENAI_SPEECH -> ExternalTtsConfig.DEFAULT_OPENAI_BASE_URL
                        ExternalTtsProtocol.MIMO_CHAT -> ExternalTtsConfig.DEFAULT_MIMO_BASE_URL
                    }
                )
            },
            supportingText = {
                if (baseUrlError) Text(stringResource(R.string.external_tts_base_url_required))
            },
            isError = baseUrlError,
            singleLine = true,
            shape = RoundedCornerShape(AppRadius.md)
        )

        OutlinedTextField(
            value = draftModel,
            onValueChange = {
                draftModel = it
                draftIsDirty = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.external_tts_model_label)) },
            placeholder = {
                Text(
                    when (draftProtocol) {
                        ExternalTtsProtocol.OPENAI_SPEECH -> ExternalTtsConfig.DEFAULT_OPENAI_MODEL
                        ExternalTtsProtocol.MIMO_CHAT -> ExternalTtsConfig.DEFAULT_MIMO_MODEL
                    }
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(AppRadius.md)
        )

        ExternalTtsVoiceSelection(
            protocol = draftProtocol,
            voice = draftVoice,
            onVoiceChange = {
                draftVoice = it
                draftIsDirty = true
            }
        )

        OutlinedTextField(
            value = draftStyle,
            onValueChange = {
                draftStyle = it
                draftIsDirty = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.external_tts_style_label)) },
            placeholder = { Text(stringResource(R.string.external_tts_style_placeholder)) },
            supportingText = { Text(stringResource(R.string.external_tts_style_description)) },
            singleLine = true,
            shape = RoundedCornerShape(AppRadius.md)
        )

        OutlinedTextField(
            value = token,
            onValueChange = {
                token = it
                tokenError = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.external_tts_token_label)) },
            placeholder = {
                Text(
                    if (hasToken) {
                        stringResource(R.string.external_tts_token_saved)
                    } else {
                        stringResource(R.string.external_tts_token_placeholder)
                    }
                )
            },
            supportingText = {
                Text(
                    if (tokenError) {
                        stringResource(R.string.external_tts_token_required)
                    } else {
                        stringResource(R.string.external_tts_token_storage_notice)
                    }
                )
            },
            isError = tokenError,
            singleLine = true,
            shape = RoundedCornerShape(AppRadius.md),
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(
                        if (tokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (tokenVisible) R.string.external_tts_hide_key else R.string.external_tts_show_key
                        )
                    )
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadius.md))
                .background(AppColors.CardBg)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        if (!draftAllowHttp) {
                            showHttpWarning = true
                        } else {
                            draftAllowHttp = false
                            draftIsDirty = true
                        }
                    }
                )
                .padding(horizontal = AppSpace.md, vertical = AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = if (draftAllowHttp) MaterialTheme.colorScheme.error else AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.size(AppSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.external_tts_allow_http),
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.external_tts_allow_http_desc),
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
            Checkbox(
                checked = draftAllowHttp,
                onCheckedChange = { checked ->
                    if (checked) {
                        showHttpWarning = true
                    } else {
                        draftAllowHttp = false
                        draftIsDirty = true
                    }
                },
                colors = CheckboxDefaults.colors(checkedColor = AppColors.Accent)
            )
        }

        ExternalTtsPrimaryButton(
            label = stringResource(
                if (isEnabled) R.string.external_tts_save_configuration else R.string.external_tts_enable
            ),
            onClick = ::requestEnable
        )
        Spacer(Modifier.height(32.dp))
    }

    pendingRequest?.let { action ->
        when (action) {
            PendingExternalTtsAction.Enable -> ExternalTtsConsentSheet(
                allowHttp = draftAllowHttp,
                onDismiss = { pendingRequest = null },
                onAccept = {
                    saveConfiguration()
                    pendingRequest = null
                }
            )
        }
    }

    if (showHttpWarning) {
        ExternalTtsHttpWarningSheet(
            onDismiss = { showHttpWarning = false },
            onAccept = {
                draftAllowHttp = true
                draftIsDirty = true
                showHttpWarning = false
            }
        )
    }
}

@Composable
private fun ExternalTtsVoiceSelection(
    protocol: ExternalTtsProtocol,
    voice: String,
    onVoiceChange: (String) -> Unit
) {
    val presetVoices = when (protocol) {
        ExternalTtsProtocol.OPENAI_SPEECH -> OPENAI_VOICE_PRESETS
        ExternalTtsProtocol.MIMO_CHAT -> MIMO_VOICE_PRESETS
    }
    val isCustomVoice = voice !in presetVoices
    val customVoiceVisibility = remember { MutableTransitionState(isCustomVoice) }
    LaunchedEffect(isCustomVoice) {
        customVoiceVisibility.targetState = isCustomVoice
    }
    val customVoiceLabel = stringResource(R.string.external_tts_voice_custom)
    var menuExpanded by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val liquidMenuHost = LocalLiquidGlassMenuHost.current
    val selectorMenuWidth = with(LocalDensity.current) { menuAnchorBounds.width.toDp() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.external_tts_voice_label),
                fontSize = AppType.Body,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.width(AppSpace.md))
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.WindowBg)
                        .border(1.dp, AppColors.Divider, RoundedCornerShape(14.dp))
                        .onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
                        .clickable(
                            role = Role.Button,
                            onClick = {
                                if (isLiquidGlass && liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                                    liquidMenuHost.show(
                                        LiquidGlassMenuSpec(
                                            anchorBounds = menuAnchorBounds,
                                            width = selectorMenuWidth,
                                            items = presetVoices.map { preset ->
                                                LiquidGlassMenuItem(
                                                    label = preset,
                                                    selected = preset == voice,
                                                    onClick = { onVoiceChange(preset) }
                                                )
                                            } + LiquidGlassMenuItem(
                                                label = customVoiceLabel,
                                                selected = isCustomVoice,
                                                onClick = {
                                                    if (!isCustomVoice) onVoiceChange("")
                                                }
                                            )
                                        )
                                    )
                                } else {
                                    menuExpanded = true
                                }
                            }
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = voice.ifBlank { customVoiceLabel },
                        fontSize = AppType.BodySmall,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.width(selectorMenuWidth),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = AppColors.WindowBg,
                    border = BorderStroke(1.dp, AppColors.Divider),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    presetVoices.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = preset,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (preset == voice) AppColors.Accent else AppColors.TextPrimary,
                                    fontSize = AppType.BodySmall,
                                    fontWeight = if (preset == voice) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            },
                            onClick = {
                                onVoiceChange(preset)
                                menuExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = customVoiceLabel,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isCustomVoice) AppColors.Accent else AppColors.TextPrimary,
                                fontSize = AppType.BodySmall,
                                fontWeight = if (isCustomVoice) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        },
                        onClick = {
                            if (!isCustomVoice) onVoiceChange("")
                            menuExpanded = false
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visibleState = customVoiceVisibility,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(260)
            ) + slideInVertically(animationSpec = tween(260)) { it / 3 } +
                fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(180)
            ) + slideOutVertically(animationSpec = tween(180)) { it / 4 } +
                fadeOut(animationSpec = tween(130)),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = voice,
                onValueChange = onVoiceChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpace.xs),
                label = { Text(stringResource(R.string.external_tts_voice_custom_label)) },
                singleLine = true,
                shape = RoundedCornerShape(AppRadius.md)
            )
        }
    }
}

@Composable
private fun ExternalTtsStatusCard(
    settings: ExternalTtsSettings,
    onConfigure: () -> Unit
) {
    val serviceName = when (settings.protocol) {
        ExternalTtsProtocol.OPENAI_SPEECH -> stringResource(R.string.external_tts_protocol_openai)
        ExternalTtsProtocol.MIMO_CHAT -> stringResource(R.string.external_tts_protocol_mimo)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.external_tts_configure),
                onClick = onConfigure
            )
            .heightIn(min = 48.dp)
            .padding(AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (settings.enabled) AppColors.Accent.copy(alpha = 0.12f) else AppColors.BgGray,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (settings.enabled) Icons.Outlined.CheckCircle else Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = if (settings.enabled) AppColors.Accent else AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.size(AppSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.external_tts_service_status),
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary
            )
            Text(
                serviceName,
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            if (!settings.enabled) {
                Text(
                    stringResource(R.string.external_tts_not_configured),
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ─── Protocol Card ─────────────────────────────────────────

@Composable
private fun ExternalTtsProtocolCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AppColors.Accent else AppColors.Divider,
                shape = RoundedCornerShape(AppRadius.md)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(AppSpace.md),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Outlined.SettingsVoice,
            null,
            tint = if (selected) AppColors.Accent else AppColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.size(AppSpace.md))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = AppType.Body, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(description, fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = AppColors.Accent)
        )
    }
}

// ─── Disclosure Card ───────────────────────────────────────

@Composable
private fun ExternalTtsDisclosureCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.BgGray)
            .padding(AppSpace.md),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PrivacyTip, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(AppSpace.sm))
            Text(
                stringResource(R.string.external_tts_third_party_title),
                fontSize = AppType.BodySmall,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            stringResource(R.string.external_tts_third_party_notice),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
    }
}

// ─── Buttons ───────────────────────────────────────────────

@Composable
private fun ExternalTtsPrimaryButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    LiquidGlassButton(
        onClick = onClick,
        enabled = enabled,
        tintedColor = AppColors.Accent,
        shape = RoundedCornerShape(25.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(
            label,
            fontSize = AppType.BodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else AppColors.TextSecondary
        )
    }
}

@Composable
private fun ExternalTtsSecondaryButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val color = when {
        !enabled -> AppColors.TextSecondary
        destructive -> MaterialTheme.colorScheme.error
        else -> AppColors.TextPrimary
    }
    LiquidGlassButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(AppRadius.md),
        contentColor = color,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, AppColors.Divider, RoundedCornerShape(AppRadius.md))
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(AppSpace.sm))
        Text(label, fontSize = AppType.BodySmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Consent Sheet ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalTtsConsentSheet(
    allowHttp: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    var remainingSeconds by remember { mutableIntStateOf(CONSENT_COUNTDOWN_SECONDS) }
    var confirmed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds--
        }
    }

    val sheetContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.external_tts_consent_title),
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.external_tts_consent_subtitle),
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExternalTtsRiskItem(stringResource(R.string.external_tts_risk_send))
                ExternalTtsRiskItem(stringResource(R.string.external_tts_risk_key))
                ExternalTtsRiskItem(stringResource(R.string.external_tts_risk_third_party))
                ExternalTtsRiskItem(stringResource(R.string.external_tts_risk_costs))
            }

            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadius.md))
                    .background(AppColors.BgGray)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = AppSpace.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Security, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(AppSpace.sm))
                    Text(
                        stringResource(R.string.external_tts_key_local),
                        fontSize = AppType.BodySmall,
                        color = AppColors.TextPrimary
                    )
                }
            }

            if (allowHttp) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadius.md))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                        .padding(AppSpace.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(AppSpace.sm))
                        Text(
                            stringResource(R.string.external_tts_http_warning_title),
                            fontSize = AppType.BodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        stringResource(R.string.external_tts_http_warning_desc),
                        fontSize = AppType.Caption,
                        color = AppColors.TextPrimary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { confirmed = !confirmed }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = confirmed,
                    onCheckedChange = { confirmed = it },
                    colors = CheckboxDefaults.colors(checkedColor = AppColors.Accent)
                )
                Text(
                    stringResource(R.string.external_tts_consent_checkbox),
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            ExternalTtsPrimaryButton(
                label = if (remainingSeconds > 0) {
                    stringResource(R.string.external_tts_consent_countdown, remainingSeconds)
                } else {
                    stringResource(R.string.external_tts_consent_accept)
                },
                enabled = remainingSeconds == 0 && confirmed,
                onClick = onAccept
            )
            Spacer(Modifier.height(10.dp))
            LiquidGlassButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(25.dp),
                tintedColor = if (isLiquidGlass) null else AppColors.BgGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
            }
        }
    }

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.BottomCenter,
            shape = RoundedCornerShape(28.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.12f)
        ) {
            sheetContent()
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = AppColors.CardBg,
            contentColor = AppColors.TextPrimary,
            scrimColor = Color.Black.copy(alpha = 0.12f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(AppColors.TextSecondary.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            sheetContent()
        }
    }
}

// ─── HTTP Warning Sheet ────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalTtsHttpWarningSheet(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    var confirmed by rememberSaveable { mutableStateOf(false) }

    val sheetContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.external_tts_http_warning_title),
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.external_tts_http_warning_desc),
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { confirmed = !confirmed }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = confirmed,
                    onCheckedChange = { confirmed = it },
                    colors = CheckboxDefaults.colors(checkedColor = AppColors.Accent)
                )
                Text(
                    stringResource(R.string.external_tts_http_confirm),
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            ExternalTtsPrimaryButton(
                label = stringResource(R.string.external_tts_http_enable),
                enabled = confirmed,
                onClick = onAccept
            )
            Spacer(Modifier.height(10.dp))
            LiquidGlassButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(25.dp),
                tintedColor = if (isLiquidGlass) null else AppColors.BgGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
            }
        }
    }

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.BottomCenter,
            shape = RoundedCornerShape(28.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.12f)
        ) {
            sheetContent()
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = AppColors.CardBg,
            contentColor = AppColors.TextPrimary,
            scrimColor = Color.Black.copy(alpha = 0.12f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(AppColors.TextSecondary.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            sheetContent()
        }
    }
}

// ─── Risk Item ─────────────────────────────────────────────

@Composable
private fun ExternalTtsRiskItem(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .background(AppColors.Accent, CircleShape)
        )
        Spacer(Modifier.size(AppSpace.sm))
        Text(text, fontSize = AppType.BodySmall, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
    }
}

// ─── Private types ─────────────────────────────────────────

private enum class PendingExternalTtsAction { Enable }

private const val CONSENT_COUNTDOWN_SECONDS = 30
