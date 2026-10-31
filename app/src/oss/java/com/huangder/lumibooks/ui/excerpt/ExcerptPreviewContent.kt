package com.huangder.lumibooks.ui.excerpt

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.LumiMotion
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExcerptPreview(
    request: ExcerptRequest,
    onClose: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    handleBack: Boolean = true
) {
    val viewModel: ExcerptViewModel = hiltViewModel()
    val selection by viewModel.selection.collectAsState()
    LaunchedEffect(Unit) { viewModel.openSession() }
    ExcerptPreviewContent(request, selection, viewModel::select, onClose, modifier, handleBack)
}

private data class ExcerptRenderState(val document: ExcerptDocument? = null, val failed: Boolean = false)
private data class PendingExcerptSave(val request: ExcerptRequest, val selection: ExcerptSelection, val document: ExcerptDocument)

@Composable
internal fun ExcerptPreviewContent(
    request: ExcerptRequest,
    selection: ExcerptSelection?,
    onSelection: (ExcerptSelection) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    handleBack: Boolean = true,
    render: (suspend (ExcerptRequest, ExcerptSelection) -> ExcerptDocument)? = null,
    write: (suspend (ExcerptDocument, Boolean) -> Uri)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentSelection by rememberUpdatedState(selection)
    val currentRequest by rememberUpdatedState(request)
    var busy by remember { mutableStateOf(false) }
    val motionEnabled = LocalMotionEnabled.current && !LocalEInkMode.current
    var pendingSave by remember { mutableStateOf<PendingExcerptSave?>(null) }
    val renderState = key(request, selection) {
        produceState(ExcerptRenderState(), request, selection) {
            val selected = selection ?: return@produceState
            value = try {
                val document = withContext(Dispatchers.Default) {
                    (render?.invoke(request, selected)
                        ?: ExcerptRenderer.prepare(context, request, selected.style, selected.options)).also { it.previewFirstTile }
                }
                ExcerptRenderState(document)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ExcerptRenderState(failed = true)
            } catch (_: OutOfMemoryError) {
                ExcerptRenderState(failed = true)
            }
        }
    }
    var tileFailed by remember(request, selection) { mutableStateOf(false) }
    val document = renderState.value.document.takeUnless { tileFailed }
    val latestDocument by rememberUpdatedState(document)
    var retainedDocument by remember(request) { mutableStateOf<ExcerptDocument?>(null) }
    LaunchedEffect(document) { if (document != null) retainedDocument = document }
    val shownDocument = if (renderState.value.failed || tileFailed) null else document ?: retainedDocument

    fun export(target: PendingExcerptSave, share: Boolean) {
        if (busy || currentRequest != target.request || currentSelection != target.selection || latestDocument !== target.document) return
        busy = true
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    write?.invoke(target.document, share) ?: ExcerptImages.write(context, target.document, share)
                }
                if (write == null) {
                    if (share) context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(context.contentResolver, "excerpt", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, context.getString(R.string.excerpt_share)))
                    else Toast.makeText(context, R.string.excerpt_saved, Toast.LENGTH_SHORT).show()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, if (share) R.string.excerpt_share_failed else R.string.excerpt_save_failed, Toast.LENGTH_LONG).show()
            } catch (_: OutOfMemoryError) {
                Toast.makeText(context, R.string.excerpt_failed, Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingSave
        pendingSave = null
        if (granted && target != null) export(target, false)
        else if (!granted) Toast.makeText(context, R.string.excerpt_save_failed, Toast.LENGTH_SHORT).show()
    }
    fun requestExport(share: Boolean) {
        val selected = selection ?: return
        val ready = document ?: return
        if (busy) return
        val target = PendingExcerptSave(request, selected, ready)
        run {
            if (!share && Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingSave = target
                permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else export(target, share)
        }
    }
    BackHandler(enabled = handleBack, onBack = onClose)

    BoxWithConstraints(modifier.imePadding()) {
        val controlsMaxHeight = maxHeight * 0.38f
        Column(Modifier.fillMaxSize().padding(horizontal = AppSpace.md)) {
            AppSheetHeader(stringResource(R.string.excerpt_title), onClose,
                modifier = Modifier.padding(top = AppSpace.md, bottom = AppSpace.sm))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("excerpt_preview"), contentAlignment = Alignment.Center) {
                if (shownDocument != null) {
                    val needsScroll = maxWidth * shownDocument.height.toFloat() / shownDocument.width > maxHeight
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val height = minOf(maxHeight, maxWidth * shownDocument.height.toFloat() / shownDocument.width)
                            val animatedHeight by animateDpAsState(height,
                                tween(if (motionEnabled) LumiMotion.SheetEnterMillis else 0, easing = AppEasing.Smooth),
                                label = "excerptPreviewHeight")
                            AnimatedContent(shownDocument,
                                modifier = Modifier.fillMaxWidth().height(animatedHeight.coerceAtMost(maxHeight))
                                    .clip(AppRoundedCornerShape(AppRadius.sm)),
                                transitionSpec = {
                                    ((fadeIn(tween(if (motionEnabled) LumiMotion.PageEnterMillis else 0)) +
                                        scaleIn(tween(if (motionEnabled) LumiMotion.PageEnterMillis else 0, easing = AppEasing.Smooth), initialScale = if (motionEnabled) 0.97f else 1f))
                                        togetherWith (fadeOut(tween(if (motionEnabled) LumiMotion.MenuExitMillis else 0)) +
                                        scaleOut(tween(if (motionEnabled) LumiMotion.MenuExitMillis else 0), targetScale = if (motionEnabled) 1.015f else 1f)))
                                        .using(null)
                                }, label = "excerptCardSwitch") { shown ->
                                LazyColumn(Modifier.fillMaxSize().testTag("excerpt_card")) {
                                    items((shown.height + 1199) / 1200) { index ->
                                        ExcerptPreviewTile(shown, index, onFailure = {
                                            if (latestDocument === shown) tileFailed = true
                                        })
                                    }
                                }
                            }
                        }
                        if (needsScroll) {
                            Text(stringResource(R.string.excerpt_scroll_hint), color = AppColors.TextSecondary,
                                fontSize = AppType.Caption, modifier = Modifier.padding(top = AppSpace.xs))
                        }
                    }
                } else if (renderState.value.failed || tileFailed) {
                    Text(stringResource(R.string.excerpt_failed), color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
                } else CircularProgressIndicator(color = AppColors.TextPrimary, modifier = Modifier.size(AppSpace.xl))
            }
            if (document?.isLongImageFallback == true) {
                Text(stringResource(R.string.excerpt_long_image), color = AppColors.TextSecondary, fontSize = AppType.Caption,
                    modifier = Modifier.padding(top = AppSpace.xs))
            }
            Column(Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight).verticalScroll(rememberScrollState())
                .padding(vertical = AppSpace.sm), verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
                    contentPadding = PaddingValues(AppSpace.xs),
                    modifier = Modifier.fillMaxWidth().clip(AppRoundedCornerShape(AppRadius.md))
                        .background(AppColors.BgGray).testTag("excerpt_styles")) {
                    items(ExcerptEdition.styles, key = { it.name }) { style ->
                        ExcerptChoice(stringResource(style.labelResource()), selection?.style == style,
                            enabled = selection != null && !busy,
                            tag = "excerpt_style_${style.name}") { selection?.let { onSelection(it.copy(style = style)) } }
                    }
                }

            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = AppSpace.sm, bottom = AppSpace.lg),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.lg, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                ExcerptAction(AppIcons.DownloadSimple, stringResource(R.string.excerpt_save), document != null && !busy) { requestExport(false) }
                ExcerptAction(AppIcons.ShareNetwork, stringResource(R.string.excerpt_share), document != null && !busy) { requestExport(true) }
                if (busy) CircularProgressIndicator(Modifier.size(AppSpace.md), color = AppColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun ExcerptPreviewTile(document: ExcerptDocument, index: Int, onFailure: () -> Unit) {
    val top = index * 1200
    val tileHeight = minOf(1200, document.height - top)
    val tile by produceState<Bitmap?>(if (index == 0) document.previewFirstTile else null, document, index) {
        if (index != 0) try {
            value = withContext(Dispatchers.Default) { document.render(top, tileHeight) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            onFailure()
        } catch (_: OutOfMemoryError) {
            onFailure()
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(document.width.toFloat() / tileHeight).background(Color(document.backgroundColor))) {
        tile?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun ExcerptChoice(label: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val motionEnabled = LocalMotionEnabled.current && !LocalEInkMode.current
    val background by animateColorAsState(if (selected) AppColors.CardBg else Color.Transparent,
        tween(if (motionEnabled) LumiMotion.MenuEnterMillis else 0), label = "excerptChoiceBackground")
    val ink by animateColorAsState(if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
        tween(if (motionEnabled) LumiMotion.MenuEnterMillis else 0), label = "excerptChoiceInk")
    Row(Modifier.clip(AppRoundedCornerShape(AppRadius.sm))
        .lumiCardSurface(background, AppRoundedCornerShape(AppRadius.sm), controlEdge = selected)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = AppPresentation.ActionMinHeight)
        .padding(horizontal = AppSpace.md, vertical = AppSpace.sm).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
        Text(label, color = ink,
            fontSize = AppType.BodySmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExcerptAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        LiquidGlassIconButton(icon, label, onClick, enabled = enabled,
            size = AppSpace.xl + AppSpace.md, iconSize = AppSpace.lg, normalContainerColor = AppColors.BgGray)
    }
}

private fun ExcerptStyle.labelResource(): Int = when (this) {
    ExcerptStyle.READER -> R.string.excerpt_style_reader
    ExcerptStyle.CALENDAR -> R.string.excerpt_style_calendar
    ExcerptStyle.CLASSIC -> R.string.excerpt_style_classic
    ExcerptStyle.ILLUSTRATION -> R.string.excerpt_style_illustration
    ExcerptStyle.ILLUSTRATION_EDGE -> R.string.excerpt_style_illustration_edge
    ExcerptStyle.COVER -> R.string.excerpt_style_cover
    else -> R.string.excerpt_style_reader
}
