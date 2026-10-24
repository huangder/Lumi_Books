package com.huangder.lumibooks.ui.home.remote

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.remote.RemoteErrorKind
import com.huangder.lumibooks.data.remote.RemoteSource
import com.huangder.lumibooks.data.remote.RemoteSourceType
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.SwipeRevealItem
import com.huangder.lumibooks.ui.components.RequestLocalNetworkPermission
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun RemoteSourceListScreen(
    type: RemoteSourceType,
    sources: List<RemoteSource>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (RemoteSource) -> Unit,
    onEdit: (RemoteSource) -> Unit,
    onDelete: (RemoteSource) -> Unit,
    onFeedback: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<RemoteSource?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize()) {
        RemotePageHeader(
            title = stringResource(
                if (type == RemoteSourceType.WEBDAV) R.string.remote_library_webdav_title else R.string.remote_library_opds_title
            ),
            subtitle = null,
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AppSpace.lg,
                end = AppSpace.lg,
                top = AppSpace.sm,
                bottom = 128.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            if (sources.isEmpty()) {
                item(key = "empty") { RemoteSourcesEmptyCard(type = type) }
            }
            items(sources, key = { it.id }) { source ->
                RemoteSourceRow(
                    source = source,
                    onOpen = { onOpen(source) },
                    onEdit = { onEdit(source) },
                    onDelete = { pendingDelete = source }
                )
            }
            if (type == RemoteSourceType.OPDS) {
                item(key = "feedback_hint") {
                    Text(
                        text = stringResource(R.string.remote_library_feedback_hint),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = AppSpace.xs).padding(horizontal = AppSpace.xs)
                    )
                }
            }
        }
      }
      Row(
          modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = AppSpace.lg, bottom = AppSpace.sm),
          horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
          verticalAlignment = Alignment.Top
      ) {
          if (type == RemoteSourceType.OPDS) {
              Column(
                  modifier = Modifier.widthIn(max = 180.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
              ) {
                  LiquidGlassButton(
                      onClick = onFeedback,
                      modifier = Modifier.fillMaxWidth().height(56.dp),
                      shape = AppRoundedCornerShape(AppRadius.full),
                      tintedColor = AppColors.Accent,
                      contentColor = AppColors.OnAccent
                  ) {
                      Icon(AppIcons.Bug, contentDescription = null, tint = AppColors.OnAccent, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(AppSpace.xs))
                      Text(
                          text = stringResource(R.string.remote_library_feedback),
                          color = AppColors.OnAccent,
                          fontSize = AppType.BodySmall,
                          fontWeight = FontWeight.SemiBold,
                          maxLines = 1,
                          softWrap = false,
                          overflow = TextOverflow.Ellipsis
                      )
                  }
              }
          }
          LiquidGlassIconButton(
              imageVector = AppIcons.Plus,
              contentDescription = stringResource(R.string.remote_library_add),
              onClick = onAdd,
              size = 56.dp,
              iconSize = 24.dp,
              contentColor = AppColors.OnAccent,
              normalContainerColor = AppColors.Accent,
              liquidContainerColor = AppColors.Accent,
              liquidScrimColor = AppColors.Accent.copy(alpha = 0.58f)
          )
      }
    }
    pendingDelete?.let { source ->
        LiquidGlassAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.remote_library_delete)) },
            text = { Text(stringResource(R.string.remote_library_delete_confirm, source.name)) },
            confirmButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.delete),
                    tintedColor = androidx.compose.ui.graphics.Color(0xFFD92D3A),
                    onClick = {
                        pendingDelete = null
                        onDelete(source)
                    }
                )
            },
            dismissButton = {
                LiquidGlassTextButton(text = stringResource(R.string.cancel), onClick = { pendingDelete = null })
            }
        )
    }
}

@Composable
private fun RemoteSourceRow(
    source: RemoteSource,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = AppRoundedCornerShape(AppRadius.lg)
    SwipeRevealItem(onEdit = onEdit, onDelete = onDelete, onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).lumiCardSurface(shape = shape)
                .padding(start = AppSpace.md, top = AppSpace.sm, bottom = AppSpace.sm, end = AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (source.type == RemoteSourceType.WEBDAV) AppIcons.Cloud else AppIcons.Globe,
                contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(source.name, color = AppColors.TextPrimary, fontSize = AppType.Body, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(source.url, color = AppColors.TextSecondary, fontSize = AppType.Caption,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(AppIcons.CaretRight, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** First-run card: what this page is for, so an empty list never looks broken. */
@Composable
private fun RemoteSourcesEmptyCard(type: RemoteSourceType) {
    val shape = AppRoundedCornerShape(AppRadius.lg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .lumiCardSurface(shape = shape)
            .padding(horizontal = AppSpace.md, vertical = AppSpace.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(AppColors.Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (type == RemoteSourceType.WEBDAV) AppIcons.Cloud else AppIcons.Globe,
                contentDescription = null,
                tint = AppColors.Accent,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = stringResource(R.string.remote_library_empty),
            color = AppColors.TextPrimary,
            fontSize = AppType.Body,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(
                if (type == RemoteSourceType.WEBDAV) R.string.remote_library_empty_detail_webdav
                else R.string.remote_library_empty_detail_opds
            ),
            color = AppColors.TextSecondary,
            fontSize = AppType.Caption,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
internal fun RemoteEmptyState(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpace.xl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        Text(
            text = message,
            color = AppColors.TextSecondary,
            fontSize = AppType.BodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            LiquidGlassButton(
                onClick = onAction,
                shape = AppRoundedCornerShape(AppRadius.full),
                tintedColor = AppColors.Accent,
                contentColor = AppColors.OnAccent
            ) {
                Text(text = actionLabel, color = AppColors.OnAccent, fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Add / edit a source. The connection is tested before saving so a broken address never lands
 * in the list. Field styling matches the WebDAV sync settings form.
 */
@Composable
internal fun RemoteSourceFormScreen(
    type: RemoteSourceType,
    existing: RemoteSource?,
    hasSavedPassword: Boolean,
    requestLocalNetworkPermission: RequestLocalNetworkPermission,
    loadSyncAccount: suspend () -> Triple<String, String, String?>?,
    onTestAndSave: suspend (name: String, url: String, username: String, password: String) -> RemoteErrorKind?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(existing?.url.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var showLegalNotice by rememberSaveable { mutableStateOf(false) }
    var confirmationSeconds by rememberSaveable { mutableStateOf(5) }
    var error by remember { mutableStateOf<RemoteErrorKind?>(null) }
    var urlError by remember { mutableStateOf(false) }
    val isHttp = url.trim().startsWith("http://", ignoreCase = true)

    LaunchedEffect(showLegalNotice) {
        if (showLegalNotice) {
            confirmationSeconds = 5
            while (confirmationSeconds > 0) {
                delay(1_000)
                confirmationSeconds -= 1
            }
        }
    }

    fun submit() {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            urlError = true
            return
        }
        showLegalNotice = true
    }

    fun confirmLegalNotice() {
        if (confirmationSeconds > 0 || testing) return
        val trimmed = url.trim()
        showLegalNotice = false
        error = null
        requestLocalNetworkPermission(
            trimmed,
            {
                testing = true
                scope.launch {
                    val result = onTestAndSave(name, trimmed, username, password)
                    testing = false
                    if (result == null) onSaved() else error = result
                }
            },
            { error = RemoteErrorKind.LOCAL_NETWORK_PERMISSION }
        )
    }

    val blurBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val urlPlaceholder = stringResource(
        if (type == RemoteSourceType.WEBDAV) R.string.remote_library_url_hint_webdav else R.string.remote_library_url_hint_opds
    )
    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            RemotePageHeader(
                title = stringResource(if (existing == null) R.string.remote_library_add else R.string.remote_library_edit),
                subtitle = stringResource(
                    if (type == RemoteSourceType.WEBDAV) R.string.import_webdav else R.string.import_opds
                ),
                onBack = onBack
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .layerBackdrop(blurBackdrop)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpace.md)
            ) {
                val cardShape = AppRoundedCornerShape(AppRadius.md)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .lumiCardSurface(shape = cardShape)
                        .padding(AppSpace.md),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (type == RemoteSourceType.WEBDAV && existing == null) {
                        LiquidGlassButton(
                            onClick = {
                                scope.launch {
                                    loadSyncAccount()?.let { (syncUrl, syncUser, syncPassword) ->
                                        url = syncUrl
                                        username = syncUser
                                        if (syncPassword != null) password = syncPassword
                                        urlError = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = AppRoundedCornerShape(AppRadius.lg),
                            tintedColor = AppColors.BgGray,
                            contentColor = AppColors.TextPrimary
                        ) {
                            Icon(AppIcons.Cloud, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpace.sm))
                            Text(
                                text = stringResource(R.string.remote_library_use_sync_account),
                                color = AppColors.TextPrimary,
                                fontSize = AppType.BodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    RemoteTextField(
                        label = stringResource(R.string.remote_library_name_label),
                        value = name,
                        onValueChange = { name = it },
                        placeholder = stringResource(R.string.remote_library_name_hint)
                    )
                    RemoteTextField(
                        label = stringResource(R.string.remote_library_url_label),
                        value = url,
                        onValueChange = { url = it; urlError = false },
                        placeholder = urlPlaceholder,
                        isError = urlError,
                        supportingText = if (urlError) stringResource(R.string.remote_error_invalid_url) else null,
                        keyboardType = KeyboardType.Uri
                    )
                    RemoteTextField(
                        label = stringResource(R.string.remote_library_username_label),
                        value = username,
                        onValueChange = { username = it },
                        keyboardType = KeyboardType.Email
                    )
                    RemoteTextField(
                        label = stringResource(R.string.remote_library_password_label),
                        value = password,
                        onValueChange = { password = it },
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onImeAction = ::submit,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        supportingText = if (hasSavedPassword && password.isEmpty()) {
                            stringResource(R.string.remote_library_password_saved)
                        } else null,
                        trailingContent = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) AppIcons.EyeSlash else AppIcons.Eye,
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.webdav_hide_password else R.string.webdav_show_password
                                    ),
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                    if (isHttp && (password.isNotEmpty() || hasSavedPassword)) {
                        RemoteInlineNotice(stringResource(R.string.remote_library_http_warning))
                    }
                }
                error?.let { kind -> RemoteInlineNotice(stringResource(kind.messageRes()), isError = true) }
                Text(
                    text = stringResource(R.string.remote_library_legal_notice),
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Caption,
                    modifier = Modifier.padding(horizontal = AppSpace.xs)
                )
                Spacer(Modifier.height(RemoteBottomBarContentPadding))
            }
        }
        RemoteBottomBar(blurBackdrop = blurBackdrop) {
            RemotePrimaryButton(
                text = stringResource(if (testing) R.string.remote_library_testing else R.string.remote_library_test_and_save),
                enabled = !testing && url.isNotBlank(),
                floating = true,
                onClick = ::submit,
                leading = if (testing) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AppColors.TextSecondary,
                            strokeWidth = 2.dp
                        )
                    }
                } else null
            )
        }
    }
    if (showLegalNotice) RemoteLibraryLegalSheet(
        confirmationSeconds = confirmationSeconds,
        enabled = confirmationSeconds == 0 && !testing,
        onDismiss = { showLegalNotice = false },
        onConfirm = ::confirmLegalNotice
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteLibraryLegalSheet(
    confirmationSeconds: Int,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md)
        ) {
            Text(
                text = stringResource(R.string.remote_library_legal_title),
                color = AppColors.TextPrimary,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.remote_library_legal_message),
                color = AppColors.TextSecondary,
                fontSize = AppType.BodySmall
            )
            LiquidGlassButton(
                onClick = onConfirm,
                enabled = enabled,
                tintedColor = AppColors.Accent,
                shape = AppRoundedCornerShape(25.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) {
                Text(
                    text = if (confirmationSeconds > 0) {
                        stringResource(R.string.remote_library_legal_confirm_wait, confirmationSeconds)
                    } else {
                        stringResource(R.string.remote_library_legal_confirm)
                    },
                    color = if (enabled) Color.White else AppColors.TextSecondary,
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LiquidGlassButton(
                onClick = onDismiss,
                shape = AppRoundedCornerShape(25.dp),
                tintedColor = if (isLiquidGlass) null else AppColors.BgGray,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = AppColors.TextPrimary,
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.BottomCenter,
            shape = AppRoundedCornerShape(28.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.12f)
        ) {
            content()
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppColors.CardBg,
            contentColor = AppColors.TextPrimary,
            scrimColor = Color.Black.copy(alpha = 0.12f),
            shape = AppRoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = null
        ) {
            content()
        }
    }
}

@Composable
internal fun RemoteInlineNotice(text: String, isError: Boolean = false) {
    val shape = AppRoundedCornerShape(AppRadius.sm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                (if (isError) androidx.compose.ui.graphics.Color(0xFFD92D3A) else AppColors.Accent).copy(alpha = 0.10f),
                shape
            )
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isError) AppIcons.Warning else AppIcons.Info,
            contentDescription = null,
            tint = if (isError) androidx.compose.ui.graphics.Color(0xFFD92D3A) else AppColors.Accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(AppSpace.sm))
        Text(text = text, color = AppColors.TextPrimary, fontSize = AppType.Caption)
    }
}
