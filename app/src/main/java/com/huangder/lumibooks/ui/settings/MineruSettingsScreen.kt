package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.huangder.lumibooks.R
import com.huangder.lumibooks.mineru.MineruConfig
import com.huangder.lumibooks.mineru.MineruMode
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton

@Composable
fun MineruSettingsDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val manualResultPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importManualMineruResult)
    }
    val currentMode = MineruMode.fromKey(uiState.mineruMode)
    var selectedMode by rememberSaveable {
        mutableStateOf(if (currentMode == MineruMode.DISABLED) MineruMode.AGENT else currentMode)
    }
    var token by rememberSaveable { mutableStateOf("") }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var tokenError by rememberSaveable { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<MineruMode?>(null) }
    val consentRequired = currentMode == MineruMode.DISABLED ||
        uiState.mineruConsentVersion < MineruConfig.CONSENT_VERSION

    LaunchedEffect(uiState.mineruMode) {
        if (pendingMode == null && currentMode != MineruMode.DISABLED) selectedMode = currentMode
    }

    fun applyMode(mode: MineruMode, acceptConsent: Boolean) {
        viewModel.enableMineru(
            mode = mode,
            token = token.takeIf { mode == MineruMode.PRECISE && it.isNotBlank() },
            acceptConsent = acceptConsent
        )
        token = ""
        tokenError = false
    }

    fun requestEnable() {
        if (selectedMode == MineruMode.PRECISE && token.isBlank() && !uiState.mineruHasToken) {
            tokenError = true
            return
        }
        tokenError = false
        if (consentRequired) pendingMode = selectedMode else applyMode(selectedMode, false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        MineruStatusCard(currentMode)

        Text(
            text = stringResource(R.string.mineru_choose_mode),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )

        MineruModeCard(
            title = stringResource(R.string.mineru_mode_agent_title),
            description = stringResource(R.string.mineru_mode_agent_description),
            icon = Icons.Outlined.Bolt,
            selected = selectedMode == MineruMode.AGENT,
            onClick = { selectedMode = MineruMode.AGENT }
        )
        MineruModeCard(
            title = stringResource(R.string.mineru_mode_precise_title),
            description = stringResource(R.string.mineru_mode_precise_description),
            icon = Icons.Outlined.Key,
            selected = selectedMode == MineruMode.PRECISE,
            onClick = { selectedMode = MineruMode.PRECISE }
        )

        if (selectedMode == MineruMode.PRECISE) {
            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it
                    tokenError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.mineru_token_label)) },
                placeholder = {
                    Text(
                        if (uiState.mineruHasToken) {
                            stringResource(R.string.mineru_token_saved)
                        } else {
                            stringResource(R.string.mineru_token_placeholder)
                        }
                    )
                },
                supportingText = {
                    Text(
                        if (tokenError) {
                            stringResource(R.string.mineru_token_required)
                        } else {
                            stringResource(R.string.mineru_token_storage_notice)
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
                            contentDescription = null
                        )
                    }
                }
            )
            MineruExternalLink(
                label = stringResource(R.string.mineru_get_token),
                url = MineruConfig.API_MANAGEMENT_URL,
                icon = Icons.Outlined.OpenInNew
            )
        }

        MineruPrimaryButton(
            label = when (selectedMode) {
                MineruMode.AGENT -> stringResource(R.string.mineru_enable_agent)
                MineruMode.PRECISE -> stringResource(R.string.mineru_enable_precise)
                MineruMode.DISABLED -> stringResource(R.string.mineru_enable_agent)
            },
            onClick = ::requestEnable
        )

        if (currentMode != MineruMode.DISABLED) {
            MineruSecondaryButton(
                label = stringResource(R.string.mineru_disable),
                icon = Icons.Outlined.CloudOff,
                onClick = { viewModel.disableMineru(clearToken = false) }
            )
        }
        if (uiState.mineruHasToken) {
            MineruSecondaryButton(
                label = stringResource(R.string.mineru_clear_token),
                icon = Icons.Outlined.DeleteOutline,
                destructive = true,
                onClick = viewModel::clearMineruToken
            )
        }

        MineruManualSection(
            importing = uiState.mineruManualImporting,
            onOpenWebsite = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MineruConfig.MANUAL_WEB_URL)))
            },
            onImportResult = {
                manualResultPicker.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "text/markdown",
                        "text/plain",
                        "application/octet-stream"
                    )
                )
            }
        )

        MineruDisclosureCard()
        Text(
            text = stringResource(R.string.mineru_powered_by),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.height(32.dp))
    }

    pendingMode?.let { mode ->
        MineruConsentSheet(
            onDismiss = { pendingMode = null },
            onAccept = {
                applyMode(mode, true)
                pendingMode = null
            }
        )
    }
}

@Composable
private fun MineruManualSection(
    importing: Boolean,
    onOpenWebsite: () -> Unit,
    onImportResult: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .border(1.dp, AppColors.Divider, RoundedCornerShape(AppRadius.md))
            .padding(AppSpace.md),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        Text(
            text = stringResource(R.string.mineru_manual_title),
            fontSize = AppType.Body,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
        Text(
            text = stringResource(R.string.mineru_manual_description),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )
        MineruSecondaryButton(
            label = stringResource(R.string.mineru_manual_open_website),
            icon = Icons.Outlined.Public,
            onClick = onOpenWebsite
        )
        MineruSecondaryButton(
            label = stringResource(
                if (importing) R.string.mineru_manual_importing else R.string.mineru_manual_import_result
            ),
            icon = Icons.Outlined.FileOpen,
            enabled = !importing,
            onClick = onImportResult
        )
    }
}

@Composable
private fun MineruStatusCard(mode: MineruMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .padding(AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (mode == MineruMode.DISABLED) AppColors.BgGray else AppColors.Accent.copy(alpha = 0.12f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (mode == MineruMode.DISABLED) Icons.Outlined.CloudOff else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (mode == MineruMode.DISABLED) AppColors.TextSecondary else AppColors.Accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.size(AppSpace.md))
        Column {
            Text(stringResource(R.string.mineru_service_status), fontSize = AppType.Caption, color = AppColors.TextSecondary)
            Text(
                when (mode) {
                    MineruMode.AGENT -> stringResource(R.string.mineru_mode_agent_short)
                    MineruMode.PRECISE -> stringResource(R.string.mineru_mode_precise_short)
                    MineruMode.DISABLED -> stringResource(R.string.mineru_not_configured)
                },
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MineruModeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(icon, null, tint = if (selected) AppColors.Accent else AppColors.TextSecondary, modifier = Modifier.size(22.dp))
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

@Composable
private fun MineruDisclosureCard() {
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
                stringResource(R.string.mineru_third_party_title),
                fontSize = AppType.BodySmall,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            stringResource(R.string.mineru_third_party_notice),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
        MineruExternalLink(stringResource(R.string.mineru_service_terms), MineruConfig.SERVICE_TERMS_URL, Icons.Outlined.Policy)
        MineruExternalLink(stringResource(R.string.mineru_privacy_policy), MineruConfig.PRIVACY_POLICY_URL, Icons.Outlined.PrivacyTip)
        MineruExternalLink(stringResource(R.string.mineru_api_limits), MineruConfig.API_LIMITS_URL, Icons.Outlined.OpenInNew)
    }
}

@Composable
private fun MineruExternalLink(
    label: String,
    url: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.sm))
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .padding(vertical = AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(AppSpace.sm))
        Text(label, fontSize = AppType.BodySmall, color = AppColors.Accent, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.OpenInNew, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MineruPrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
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
private fun MineruSecondaryButton(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MineruConsentSheet(onDismiss: () -> Unit, onAccept: () -> Unit) {
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
                stringResource(R.string.mineru_consent_title),
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.mineru_consent_subtitle),
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
                MineruRiskItem(stringResource(R.string.mineru_risk_upload))
                MineruRiskItem(stringResource(R.string.mineru_risk_third_party))
                MineruRiskItem(stringResource(R.string.mineru_risk_accuracy))
                MineruRiskItem(stringResource(R.string.mineru_risk_rights))
            }

            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadius.md))
                    .background(AppColors.BgGray)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                MineruExternalLink(stringResource(R.string.mineru_service_terms), MineruConfig.SERVICE_TERMS_URL, Icons.Outlined.Policy)
                MineruExternalLink(stringResource(R.string.mineru_privacy_policy), MineruConfig.PRIVACY_POLICY_URL, Icons.Outlined.PrivacyTip)
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
                    stringResource(R.string.mineru_consent_checkbox),
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            MineruPrimaryButton(
                label = if (remainingSeconds > 0) {
                    stringResource(R.string.mineru_consent_countdown, remainingSeconds)
                } else {
                    stringResource(R.string.mineru_consent_accept)
                },
                enabled = remainingSeconds == 0 && confirmed,
                onClick = onAccept
            )
            LiquidGlassTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentColor = AppColors.TextSecondary
            )
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

@Composable
private fun MineruRiskItem(text: String) {
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

private const val CONSENT_COUNTDOWN_SECONDS = 30
