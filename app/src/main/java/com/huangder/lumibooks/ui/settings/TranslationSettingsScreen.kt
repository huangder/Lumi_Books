package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.translation.*
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppType

@Composable
fun TranslationSettingsDetail(viewModel: TranslationSettingsViewModel = hiltViewModel()) {
    val configuration by viewModel.configuration.collectAsState()
    val action by viewModel.action.collectAsState()
    val models by viewModel.models.collectAsState()
    val config = configuration ?: run { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    TranslationSettingsContent(config, action, viewModel::save, viewModel::test, viewModel::edited,
        models, viewModel::fetchModels, viewModel::connectionEdited)
}

@Composable
internal fun TranslationSettingsContent(
    config: TranslationConfiguration,
    action: TranslationSettingsAction,
    onSave: (TranslationSettings, String) -> Unit,
    onTest: (TranslationSettings, String) -> Unit,
    onEdited: () -> Unit,
    models: TranslationModelCatalog = TranslationModelCatalog.Idle,
    onFetchModels: (TranslationSettings, String) -> Unit = { _, _ -> },
    onConnectionEdited: () -> Unit = onEdited
) {
    var draftJson by rememberSaveable { mutableStateOf(config.settings.copy(enabled = config.enabled).toJson()) }
    val draft = TranslationSettings.fromJson(draftJson)
    // Never put the API key into saved instance state or a composable's saveable state.
    var token by remember { mutableStateOf("") }
    val languages = listOf("简体中文", "繁體中文", "English", "日本語", "한국어")
    var customLanguage by rememberSaveable { mutableStateOf(draft.targetLanguage !in languages) }
    fun update(value: TranslationSettings) {
        val connectionChanged = value.credentialScope != draft.credentialScope || value.allowHttp != draft.allowHttp || value.provider != draft.provider
        draftJson = value.toJson()
        if (connectionChanged) onConnectionEdited() else onEdited()
    }
    LaunchedEffect(action.saved) { if (action.saved) token = "" }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.translation_description), color = AppColors.TextSecondary, fontSize = AppType.Body)
        TranslationChoice(stringResource(R.string.translation_provider), when (draft.provider) {
            TranslationProvider.MIMO -> "MiMo"
            TranslationProvider.DEEPSEEK -> "DeepSeek"
            TranslationProvider.OPENAI -> "OpenAI"
            TranslationProvider.CUSTOM -> stringResource(R.string.translation_custom)
        }, listOf("MiMo", "DeepSeek", "OpenAI", stringResource(R.string.translation_custom)), !action.busy) { index ->
            val provider = TranslationProvider.entries[index]
            if (provider != draft.provider) {
                token = ""
                update(draft.copy(provider = provider, baseUrl = provider.baseUrl, auth = provider.auth, model = ""))
            }
        }
        TranslationInput("Base URL", draft.baseUrl, { update(draft.copy(baseUrl = it)) },
            enabled = !action.busy, keyboardType = KeyboardType.Uri,
            hint = stringResource(R.string.translation_url_hint), tag = "translation-base-url")
        if (draft.provider == TranslationProvider.CUSTOM) {
            TranslationChoice(stringResource(R.string.translation_auth),
                if (draft.auth == TranslationAuth.BEARER) "Bearer" else "api-key",
                listOf("Bearer", "api-key"), !action.busy) { update(draft.copy(auth = TranslationAuth.entries[it])) }
        }
        TranslationInput("API Key", token, { token = it; onConnectionEdited() }, enabled = !action.busy,
            keyboardType = KeyboardType.Password, secret = true, tag = "translation-api-key",
            hint = stringResource(if (config.hasToken && config.settings.credentialScope == draft.credentialScope)
                R.string.translation_key_saved else R.string.translation_key_hint))
        TranslationModelInput(draft.model, { update(draft.copy(model = it)) }, !action.busy, models,
            onFetch = { onFetchModels(draft, token) })
        val custom = stringResource(R.string.translation_custom)
        TranslationChoice(stringResource(R.string.translation_language), if (customLanguage) custom else draft.targetLanguage,
            languages + custom, !action.busy) { index ->
            val useCustom = index == languages.size
            if (!useCustom || !customLanguage) update(draft.copy(targetLanguage = if (useCustom) "" else languages[index]))
            customLanguage = useCustom
        }
        if (customLanguage) TranslationInput(stringResource(R.string.translation_language), draft.targetLanguage,
            { update(draft.copy(targetLanguage = it)) }, enabled = !action.busy, tag = "translation-custom-language",
            keyboardType = KeyboardType.Text)
        TranslationToggle(stringResource(R.string.translation_allow_http), draft.allowHttp, !action.busy) {
            update(draft.copy(allowHttp = it))
        }
        Text(stringResource(R.string.translation_consent), color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
        TranslationToggle(stringResource(R.string.translation_enable), draft.enabled, !action.busy) {
            update(draft.copy(enabled = it))
        }
        LiquidGlassTextButton(stringResource(R.string.translation_save), { onSave(draft, token) }, enabled = !action.busy)
        LiquidGlassTextButton(stringResource(R.string.translation_test), { onTest(draft, token) }, enabled = !action.busy)
        if (action.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (action.saved) Text(stringResource(R.string.translation_saved), color = AppColors.TextSecondary)
        action.error?.let { Text(stringResource(it.messageResource()), color = AppColors.TextSecondary) }
        action.testTranslation?.let { Text(it, color = AppColors.TextPrimary, fontSize = AppType.Body) }
    }
}

@Composable
private fun TranslationToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = AppColors.TextPrimary, fontSize = AppType.Body)
        LiquidGlassSwitch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun TranslationChoice(label: String, value: String, choices: List<String>, enabled: Boolean, onSelect: (Int) -> Unit) {
    val host = LocalLiquidGlassMenuHost.current
    val sourceId = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val width = with(LocalDensity.current) { bounds.width.toDp() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val shape = AppRoundedCornerShape(AppRadius.lg)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .liquidGlassMenuAnchor(sourceId, cornerRadius = AppRadius.lg)
            .clip(shape).lumiCardSurface(color = AppColors.BgGray, shape = shape, controlEdge = true)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clickable(enabled = enabled && host != null, role = Role.Button,
                interactionSource = remember { MutableInteractionSource() }, indication = null) {
                keyboard?.hide()
                focus.clearFocus()
                host?.toggle(LiquidGlassMenuSpec(anchorBounds = bounds, width = width,
                    sourceId = sourceId, anchorCornerRadius = AppRadius.lg,
                    items = choices.mapIndexed { index, choice ->
                        LiquidGlassMenuItem(label = choice, selected = choice == value, onClick = { onSelect(index) })
                    }))
                }
            .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(value, Modifier.weight(1f), color = AppColors.TextPrimary, fontSize = AppType.Body,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(AppIcons.CaretDown, null, Modifier.size(18.dp), tint = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun TranslationInput(
    label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean,
    tag: String, hint: String? = null, keyboardType: KeyboardType = KeyboardType.Ascii, secret: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
        TranslationEditableField(label, value, onValueChange, enabled, tag, Modifier.fillMaxWidth(), keyboardType, secret)
        hint?.let { Text(it, color = AppColors.TextSecondary, fontSize = AppType.Caption) }
    }
}

@Composable
private fun TranslationEditableField(
    label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean,
    tag: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Ascii,
    secret: Boolean = false, trailingContent: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = AppRoundedCornerShape(AppRadius.lg)
    BasicTextField(value, onValueChange, enabled = enabled, singleLine = true,
            modifier = modifier.testTag(tag).semantics { contentDescription = label }
                .onFocusChanged { focused = it.isFocused }
                .clip(shape).lumiCardSurface(color = AppColors.BgGray, shape = shape, controlEdge = true)
                .border(1.dp, if (focused) AppColors.Accent.copy(alpha = 0.45f) else Color.Transparent, shape),
            textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary, fontSize = AppType.Body),
            cursorBrush = SolidColor(AppColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { innerTextField ->
                Row(Modifier.heightIn(min = 54.dp).padding(start = 16.dp, end = if (trailingContent == null) 16.dp else 4.dp)
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) { innerTextField() }
                    trailingContent?.invoke()
                }
            })
}

@Composable
private fun TranslationModelInput(value: String, onChange: (String) -> Unit, enabled: Boolean,
    catalog: TranslationModelCatalog, onFetch: () -> Unit) {
    val host = LocalLiquidGlassMenuHost.current
    val sourceId = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val width = with(LocalDensity.current) { bounds.width.toDp() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val modelNames = (catalog as? TranslationModelCatalog.Ready)?.models.orEmpty()
    val label = stringResource(R.string.translation_model)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TranslationEditableField(label, value, onChange, enabled, "translation-model",
                Modifier.weight(1f).liquidGlassMenuAnchor(sourceId, cornerRadius = AppRadius.lg)
                    .onGloballyPositioned { bounds = it.boundsInRoot() },
                trailingContent = if (modelNames.isEmpty()) null else {
                    {
                        LiquidGlassIconButton(AppIcons.CaretDown, stringResource(R.string.translation_choose_model), {
                            keyboard?.hide(); focus.clearFocus()
                            host?.toggle(LiquidGlassMenuSpec(anchorBounds = bounds, width = width,
                                sourceId = sourceId, anchorCornerRadius = AppRadius.lg,
                                items = modelNames.map { name -> LiquidGlassMenuItem(name,
                                    selected = name == value, onClick = { onChange(name) }) }))
                        }, size = 36.dp, iconSize = 18.dp, enabled = enabled && host != null)
                    }
                })
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                if (catalog == TranslationModelCatalog.Loading) {
                    val loading = stringResource(R.string.translation_fetching_models)
                    CircularProgressIndicator(Modifier.size(18.dp).semantics { contentDescription = loading },
                        strokeWidth = 1.5.dp, color = AppColors.TextSecondary)
                } else LiquidGlassIconButton(AppIcons.DownloadSimple, stringResource(R.string.translation_fetch_models),
                    onFetch, size = 52.dp, iconSize = 22.dp, enabled = enabled, normalContainerColor = AppColors.BgGray)
            }
        }
        if (catalog is TranslationModelCatalog.Failure) {
            Text(stringResource(catalog.error.messageResource()), color = AppColors.TextSecondary, fontSize = AppType.Caption)
            Text(stringResource(R.string.translation_models_manual), color = AppColors.TextSecondary, fontSize = AppType.Caption)
        }
    }
}
