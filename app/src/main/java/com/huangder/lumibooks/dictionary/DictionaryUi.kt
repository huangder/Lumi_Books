package com.huangder.lumibooks.dictionary

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.settings.DetailActivity
import com.huangder.lumibooks.ui.settings.FeedbackActivity
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

fun openDictionarySettings(context: Context) {
    context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "local_dictionaries"))
}
fun openDictionaryFeedback(context: Context) { context.startActivity(Intent(context, FeedbackActivity::class.java)) }

@Composable
fun DictionaryDisclaimerFooter(onSources: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.dictionary_disclaimer_short), fontSize = AppType.Caption, color = AppColors.TextSecondary)
        Text(stringResource(R.string.dictionary_sources), modifier = Modifier.clickable(onClick = onSources).padding(vertical = 8.dp),
            fontSize = AppType.Caption, color = AppColors.TextSecondary)
        Text(stringResource(R.string.dictionary_feedback), modifier = Modifier.clickable { openDictionaryFeedback(context) }.padding(vertical = 8.dp),
            fontSize = AppType.Caption, color = AppColors.TextSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalDictionarySettings(viewModel: DictionaryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<DictionaryCatalogItem?>(null) }
    val dialogHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp.coerceAtMost(380.dp)
    var information by remember { mutableStateOf<DictionaryCatalogItem?>(null) }
    var remove by remember { mutableStateOf<DictionaryCatalogItem?>(null) }
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.dictionary_management_intro), fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
        LiquidGlassTextButton(stringResource(R.string.translation_settings), {
            com.huangder.lumibooks.translation.openTranslationSettings(context)
        })
        LiquidGlassTextButton(stringResource(R.string.dictionary_refresh), { viewModel.action { refreshCatalog() } }, enabled = !state.refreshing)
        if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        (error ?: state.error)?.let { Text(it, color = AppColors.TextSecondary, fontSize = AppType.BodySmall) }
        for ((index, item) in state.items.withIndex()) {
            val d = item.descriptor
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name, modifier = Modifier.weight(1f), fontSize = AppType.Section, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    item.installed?.let { installed -> LiquidGlassSwitch(installed.enabled, { enabled -> viewModel.action { setEnabled(d.id, enabled) } }) }
                }
                Text(d.description, fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
                val sizes = if (d.available) "${Formatter.formatFileSize(context, d.sizeBytes)} / ${Formatter.formatFileSize(context, d.installedBytes)}" else stringResource(R.string.dictionary_not_published)
                Text("${d.license} · ${d.version}\n${stringResource(R.string.dictionary_download_install_size)} $sizes", fontSize = AppType.Caption, color = AppColors.TextSecondary)
                item.installed?.let { Text(stringResource(R.string.dictionary_installed_version, it.descriptor.version), fontSize = AppType.Caption, color = AppColors.TextSecondary) }
                item.download?.let { download ->
                    when (download.phase) {
                        DictionaryDownloadPhase.DOWNLOADING, DictionaryDownloadPhase.PAUSED -> {
                            LinearProgressIndicator(progress = { if (download.total > 0) (download.bytes.toFloat() / download.total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                            Text(if (download.phase == DictionaryDownloadPhase.PAUSED) stringResource(R.string.dictionary_download_paused) else Formatter.formatFileSize(context, download.bytes), fontSize = AppType.Caption, color = AppColors.TextSecondary)
                        }
                        DictionaryDownloadPhase.INSTALLING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.dictionary_installing), color = AppColors.TextSecondary) }
                        DictionaryDownloadPhase.FAILED -> Text(download.error.orEmpty(), fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val task = item.download
                    if (task != null && task.phase != DictionaryDownloadPhase.FAILED) {
                        LiquidGlassTextButton(stringResource(R.string.cancel), { viewModel.action { cancel(d.id) } })
                    } else if (task?.canRetry == true) {
                        LiquidGlassTextButton(stringResource(R.string.dictionary_retry), { viewModel.action { retry(d.id) } })
                        LiquidGlassTextButton(stringResource(R.string.cancel), { viewModel.action { cancel(d.id) } })
                    } else if (item.installed == null || item.hasUpdate) {
                        LiquidGlassTextButton(stringResource(if (item.installed == null) R.string.dictionary_download else R.string.dictionary_update), { confirm = item }, enabled = d.available)
                    }
                    LiquidGlassTextButton(stringResource(R.string.dictionary_sources), { information = item })
                    if (item.installed != null) {
                        LiquidGlassTextButton(stringResource(R.string.dictionary_move_up), { viewModel.action { move(d.id, -1) } }, enabled = index > 0)
                        LiquidGlassTextButton(stringResource(R.string.dictionary_move_down), { viewModel.action { move(d.id, 1) } }, enabled = index < state.items.count { it.installed != null } - 1)
                        LiquidGlassTextButton(stringResource(R.string.dictionary_delete), { remove = item })
                    }
                }
            }
            HorizontalDivider(color = AppColors.Divider)
        }
        Text(stringResource(R.string.dictionary_disclaimer_title), fontSize = AppType.Section, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.dictionary_disclaimer_full), fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
        LiquidGlassTextButton(stringResource(R.string.dictionary_feedback), { openDictionaryFeedback(context) })
        Spacer(Modifier.height(24.dp))
    }
    confirm?.let { item ->
        val d = item.descriptor
        LiquidGlassAlertDialog(onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.dictionary_disclaimer_title)) },
            text = { Column(Modifier.heightIn(max = dialogHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${d.name}\n${d.source}\n${d.license} · ${d.version}\n${Formatter.formatFileSize(context, d.sizeBytes)}", fontWeight = FontWeight.SemiBold)
                Text(d.attribution, fontSize = AppType.Caption)
                Text(d.modifications, fontSize = AppType.BodySmall)
                Text(stringResource(R.string.dictionary_disclaimer_full), fontSize = AppType.BodySmall)
                Text(stringResource(R.string.dictionary_enable_after_download), fontSize = AppType.BodySmall)
            } },
            confirmButton = { LiquidGlassTextButton(stringResource(if (item.installed == null) R.string.dictionary_confirm_download else R.string.dictionary_confirm_update), {
                confirm = null; viewModel.action { download(d, d.confirmationKey) }
            }) }, dismissButton = { LiquidGlassTextButton(stringResource(R.string.cancel), { confirm = null }) })
    }
    information?.let { item -> DictionarySourceDialog(listOf(item.installed?.descriptor ?: item.descriptor), viewModel.repository) { information = null } }
    remove?.let { item ->
        LiquidGlassAlertDialog(onDismissRequest = { remove = null }, title = { Text(stringResource(R.string.dictionary_delete)) },
            text = { Text(stringResource(R.string.dictionary_delete_message, item.descriptor.name)) },
            confirmButton = { LiquidGlassTextButton(stringResource(R.string.dictionary_delete), { remove = null; viewModel.action { delete(item.descriptor.id) } }) },
            dismissButton = { LiquidGlassTextButton(stringResource(R.string.cancel), { remove = null }) })
    }
}

@Composable
fun DictionarySourceDialog(descriptors: List<DictionaryDescriptor>, repository: DictionaryRepository,
    backdrop: Backdrop? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dialogHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp.coerceAtMost(420.dp)
    val available = repository.state.value.items.map { it.descriptor }
    val listed = (if (descriptors.isEmpty()) available else descriptors).distinctBy { it.id }
    var licenses by remember(listed.map { it.id }) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(listed.map { it.id }) {
        licenses = listed.associate { descriptor ->
            descriptor.id to try { repository.licenseText(descriptor.id) }
            catch (error: Exception) { if (error is CancellationException) throw error; "" }
        }
    }
    LiquidGlassAlertDialog(onDismissRequest = onDismiss, backdrop = backdrop,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.72f),
        title = { Text(stringResource(R.string.dictionary_sources)) },
        text = { Column(Modifier.heightIn(max = dialogHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listed.forEach { d ->
                Text(d.name, fontSize = AppType.Section, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("${d.source}\n${d.attribution}\n${d.license}\n${d.upstreamVersion}", fontSize = AppType.BodySmall)
                Text(d.modifications, fontSize = AppType.BodySmall)
                Text(d.sourceUrl, color = AppColors.TextSecondary, fontSize = AppType.Caption, modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.sourceUrl))) }
                })
                licenses[d.id]?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = AppType.Caption) }
                if (listed.size > 1) HorizontalDivider(color = AppColors.Divider)
            }
            Text(stringResource(R.string.dictionary_disclaimer_full), fontSize = AppType.BodySmall)
        } }, confirmButton = { LiquidGlassTextButton(stringResource(R.string.close), onDismiss) })
}
