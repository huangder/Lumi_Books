package com.huangder.lumibooks.ui.settings

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.WebdavConfig
import com.huangder.lumibooks.domain.model.WebdavSyncContent
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Status page ────────────────────────────────────────────────────

@Composable
fun WebdavSettingsDetail(
    viewModel: SettingsViewModel,
    onConfigure: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val config = uiState.webdavConfig
    val isEnabled = config.enabled
    val hasToken = uiState.webdavHasToken
    val lastSyncText = if (config.lastSyncTime > 0) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        stringResource(R.string.webdav_last_sync_time, fmt.format(Date(config.lastSyncTime)))
    } else {
        stringResource(R.string.webdav_not_configured)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        // Status card
        WebdavStatusCard(
            isEnabled = isEnabled,
            lastSyncText = lastSyncText,
            isSyncing = uiState.isWebdavSyncing,
            onConfigure = onConfigure
        )

        if (!isEnabled) {
            WebdavPrimaryButton(
                label = stringResource(R.string.webdav_configure),
                onClick = onConfigure
            )
        }

        if (isEnabled && hasToken) {
            // Sync mode pill selector
            WebdavSyncModeSelector(
                selectedMode = config.syncMode,
                onModeChange = { viewModel.saveWebdavSyncMode(it) }
            )

            WebdavSyncContentSelector(
                config = config,
                onContentChange = viewModel::setWebdavSyncContent
            )

            WebdavSecondaryButton(
                label = stringResource(R.string.webdav_test_connection),
                icon = Icons.Outlined.NetworkCheck,
                onClick = viewModel::testWebdavConnection
            )
            WebdavSecondaryButton(
                label = stringResource(R.string.webdav_sync_now),
                icon = Icons.Outlined.Sync,
                onClick = { if (!uiState.isWebdavSyncing) viewModel.syncWebdavNow() }
            )
            if (uiState.webdavSyncResult.isNotBlank()) {
                Text(
                    text = uiState.webdavSyncResult,
                    fontSize = AppType.Caption,
                    color = if (uiState.webdavSyncSucceeded) AppColors.TextSecondary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (isEnabled) {
            WebdavSecondaryButton(
                label = stringResource(R.string.webdav_disable),
                icon = Icons.Outlined.CloudOff,
                onClick = { viewModel.disableWebdav(clearKey = false) }
            )
            WebdavSecondaryButton(
                label = stringResource(R.string.webdav_clear_config),
                icon = Icons.Outlined.DeleteOutline,
                destructive = true,
                onClick = { viewModel.clearWebdavConfig() }
            )
        }

        WebdavDisclosureCard()
        Spacer(Modifier.height(32.dp))
    }
}

// ─── Configuration page ─────────────────────────────────────────────

@Composable
fun WebdavConfigurationDetail(
    viewModel: SettingsViewModel,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentConfig = uiState.webdavConfig
    val hasToken = uiState.webdavHasToken

    var draftServerUrl by rememberSaveable { mutableStateOf(currentConfig.serverUrl) }
    var draftUsername by rememberSaveable { mutableStateOf(currentConfig.username) }
    var draftSyncPath by rememberSaveable { mutableStateOf(currentConfig.syncPath) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var serverUrlError by rememberSaveable { mutableStateOf(false) }
    var usernameError by rememberSaveable { mutableStateOf(false) }

    fun saveConfiguration() {
        val config = WebdavConfig(
            enabled = true,
            serverUrl = draftServerUrl.trim(),
            username = draftUsername.trim(),
            syncPath = draftSyncPath.trim().ifEmpty { "LumiBooks" },
            lastSyncTime = currentConfig.lastSyncTime,
            syncMode = currentConfig.syncMode,
            syncBookFiles = currentConfig.syncBookFiles,
            syncProfileAndSettings = currentConfig.syncProfileAndSettings,
            syncLibraryOrganization = currentConfig.syncLibraryOrganization,
            syncReadingData = currentConfig.syncReadingData
        )
        viewModel.enableWebdav(config, password)
        onSaved()
    }

    fun validateAndSave() {
        serverUrlError = false
        usernameError = false
        var valid = true

        if (draftServerUrl.isBlank()) {
            serverUrlError = true
            valid = false
        }
        if (draftUsername.isBlank()) {
            usernameError = true
            valid = false
        }
        if (!valid) return

        saveConfiguration()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        // Server URL
        OutlinedTextField(
            value = draftServerUrl,
            onValueChange = { draftServerUrl = it; serverUrlError = false },
            label = { Text(stringResource(R.string.webdav_server_url_label)) },
            placeholder = { Text(stringResource(R.string.webdav_server_url_hint)) },
            isError = serverUrlError,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Username
        OutlinedTextField(
            value = draftUsername,
            onValueChange = { draftUsername = it; usernameError = false },
            label = { Text(stringResource(R.string.webdav_username_label)) },
            isError = usernameError,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.webdav_password_label)) },
            placeholder = { Text(stringResource(R.string.webdav_password_hint)) },
            visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible)
                            stringResource(R.string.webdav_hide_password)
                        else stringResource(R.string.webdav_show_password)
                    )
                }
            },
            supportingText = if (hasToken) {
                { Text(stringResource(R.string.webdav_token_saved)) }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Sync path (optional)
        OutlinedTextField(
            value = draftSyncPath,
            onValueChange = { draftSyncPath = it },
            label = { Text(stringResource(R.string.webdav_sync_path_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Key storage notice
        Text(
            text = stringResource(R.string.webdav_token_storage_notice),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )

        Spacer(Modifier.height(AppSpace.sm))

        // Save button — uses LiquidGlassButton matching ExternalTts pattern
        WebdavPrimaryButton(
            label = stringResource(R.string.webdav_enable),
            onClick = ::validateAndSave
        )
    }
}

// ─── Shared components ──────────────────────────────────────────────

@Composable
private fun WebdavStatusCard(
    isEnabled: Boolean,
    lastSyncText: String,
    isSyncing: Boolean,
    onConfigure: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .padding(AppSpace.md)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Outlined.CheckCircle else Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = if (isEnabled) AppColors.Accent else AppColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(AppSpace.sm))
                Text(
                    text = stringResource(R.string.webdav_status_card_title),
                    fontSize = AppType.Body,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary
                )
            }
            if (isSyncing) {
                CircularProgressIndicator(
                    color = AppColors.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = if (isEnabled) stringResource(R.string.webdav_enabled_status)
                else stringResource(R.string.webdav_not_configured),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = lastSyncText,
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun WebdavPrimaryButton(
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
private fun WebdavSecondaryButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = AppSpace.md, vertical = AppSpace.md + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else AppColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppSpace.md))
        Text(
            text = label,
            fontSize = AppType.Body,
            color = if (destructive) MaterialTheme.colorScheme.error else AppColors.TextPrimary
        )
    }
}

@Composable
private fun WebdavDisclosureCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.BgGray)
            .padding(AppSpace.md),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Sync, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(AppSpace.sm))
            Text(
                stringResource(R.string.webdav_third_party_title),
                fontSize = AppType.BodySmall,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            stringResource(R.string.webdav_third_party_notice),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
    }
}

// ─── Sync mode pill selector ────────────────────────────────────────

@Composable
private fun WebdavSyncModeSelector(
    selectedMode: String,
    onModeChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .padding(AppSpace.md)
    ) {
        Text(
            text = stringResource(R.string.webdav_sync_mode_label),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.height(AppSpace.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            WebdavModePill(
                label = stringResource(R.string.webdav_sync_mode_auto),
                selected = selectedMode == "auto",
                onClick = { onModeChange("auto") },
                modifier = Modifier.weight(1f)
            )
            WebdavModePill(
                label = stringResource(R.string.webdav_sync_mode_manual),
                selected = selectedMode == "manual",
                onClick = { onModeChange("manual") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WebdavSyncContentSelector(
    config: WebdavConfig,
    onContentChange: (WebdavSyncContent, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .padding(vertical = AppSpace.sm)
    ) {
        Text(
            text = stringResource(R.string.webdav_sync_content_title),
            fontSize = AppType.BodySmall,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.xs)
        )
        Text(
            text = stringResource(R.string.webdav_sync_content_description),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(
                start = AppSpace.md,
                end = AppSpace.md,
                bottom = AppSpace.sm
            )
        )
        WebdavSyncContentRow(
            title = stringResource(R.string.webdav_sync_content_books),
            description = stringResource(R.string.webdav_sync_content_books_description),
            checked = config.syncBookFiles,
            onCheckedChange = { onContentChange(WebdavSyncContent.BOOK_FILES, it) }
        )
        WebdavSyncContentRow(
            title = stringResource(R.string.webdav_sync_content_profile_settings),
            description = stringResource(R.string.webdav_sync_content_profile_settings_description),
            checked = config.syncProfileAndSettings,
            onCheckedChange = { onContentChange(WebdavSyncContent.PROFILE_AND_SETTINGS, it) }
        )
        WebdavSyncContentRow(
            title = stringResource(R.string.webdav_sync_content_library),
            description = stringResource(R.string.webdav_sync_content_library_description),
            checked = config.syncLibraryOrganization,
            onCheckedChange = { onContentChange(WebdavSyncContent.LIBRARY_ORGANIZATION, it) }
        )
        WebdavSyncContentRow(
            title = stringResource(R.string.webdav_sync_content_reading_data),
            description = stringResource(R.string.webdav_sync_content_reading_data_description),
            checked = config.syncReadingData,
            onCheckedChange = { onContentChange(WebdavSyncContent.READING_DATA, it) }
        )
    }
}

@Composable
private fun WebdavSyncContentRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(
                start = AppSpace.md,
                end = AppSpace.sm,
                top = AppSpace.xs,
                bottom = AppSpace.xs
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) AppColors.Accent else Color.Transparent)
                .then(
                    if (checked) Modifier else Modifier.border(
                        width = 1.5.dp,
                        color = AppColors.TextSecondary,
                        shape = CircleShape
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(AppSpace.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = AppType.Body,
                color = AppColors.TextPrimary
            )
            Text(
                text = description,
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun WebdavModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(25.dp))
            .background(
                if (selected) AppColors.Accent else AppColors.BgGray
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = AppType.BodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else AppColors.TextSecondary
        )
    }
}
