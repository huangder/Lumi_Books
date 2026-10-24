package com.huangder.lumibooks.ui.home.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.remote.RemoteBookFiles
import com.huangder.lumibooks.data.remote.RemoteDownloadState
import com.huangder.lumibooks.data.remote.RemoteFormat
import com.huangder.lumibooks.data.remote.RemoteSourceType
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RemoteBookDetailsScreen(
    state: RemoteBookDetailsUiState,
    /** Liquid glass only: the scrolling content is captured so the destination sheet refracts it. */
    contentBackdrop: LayerBackdrop?,
    onBack: () -> Unit,
    onSelectFormat: (RemoteFormat) -> Unit,
    onReadMetadata: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit
) {
    val item = state.item ?: return
    val source = state.source
    val metadata = state.localMetadata
    val format = state.selectedFormat
    val size = state.sizeOf(format)
    val title = metadata?.title?.takeIf { item.fileName != null } ?: item.title
    val authors = item.authors.ifEmpty { metadata?.authors.orEmpty() }
    val summary = item.summary ?: metadata?.summary
    val cover = metadata?.coverPath ?: item.coverUrl ?: item.thumbnailUrl
    var summaryExpanded by rememberSaveable { mutableStateOf(false) }
    // The bottom blur samples the scrolling content only. In liquid glass the page capture is
    // shared with the destination sheet, so both read the same layer.
    val blurBackdrop = contentBackdrop ?: com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        RemotePageHeader(
            title = stringResource(R.string.remote_book_details_title),
            subtitle = source?.name,
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .layerBackdrop(blurBackdrop)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpace.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(AppSpace.md))
            RemoteBookCover(
                model = cover,
                extension = format?.extension,
                title = title,
                shape = AppRoundedCornerShape(AppRadius.lg),
                placeholderFontSize = AppType.Section,
                modifier = Modifier.width(150.dp).aspectRatio(0.70f)
            )
            Spacer(Modifier.height(AppSpace.md))
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppSpace.xs))
            Text(
                text = authors.joinToString(", ").ifBlank { stringResource(R.string.book_author_unknown) },
                color = AppColors.TextSecondary,
                fontSize = AppType.BodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val supported = item.supportedFormats
            if (supported.size > 1) {
                Spacer(Modifier.height(AppSpace.md))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.sm, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
                ) {
                    supported.forEach { option ->
                        FormatChip(
                            label = RemoteBookFiles.displayFormat(option.extension),
                            selected = option.url == format?.url,
                            onClick = { onSelectFormat(option) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppSpace.lg))
            val rows = buildList {
                format?.let { add(stringResource(R.string.remote_book_format) to RemoteBookFiles.displayFormat(it.extension)) }
                add(stringResource(R.string.remote_book_size) to (formatRemoteSize(size) ?: stringResource(R.string.book_download_size_unknown)))
                (item.publisher ?: metadata?.publisher)?.let { add(stringResource(R.string.remote_book_publisher) to it) }
                (item.language ?: metadata?.language)?.let { add(stringResource(R.string.remote_book_language) to it) }
                (item.published ?: metadata?.published)?.let { add(stringResource(R.string.remote_book_published) to it.take(10)) }
                item.categories.ifEmpty { metadata?.categories.orEmpty() }.takeIf { it.isNotEmpty() }?.let {
                    add(stringResource(R.string.remote_book_categories) to it.take(6).joinToString(" · "))
                }
                item.remotePath?.let { add(stringResource(R.string.remote_book_path) to it) }
                item.lastModified.takeIf { it > 0L }?.let {
                    add(stringResource(R.string.remote_book_modified) to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)))
                }
                source?.let { add(stringResource(R.string.remote_book_source) to it.name) }
            }
            InfoCard(rows)

            val unsupported = item.formats.filterNot { it.isSupported }.mapNotNull { it.label }.distinct()
            if (unsupported.isNotEmpty()) {
                Spacer(Modifier.height(AppSpace.sm))
                Text(
                    text = stringResource(R.string.remote_book_unsupported_formats, unsupported.joinToString(", ")),
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Caption,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(AppSpace.md))
                SectionCard(title = stringResource(R.string.remote_book_summary)) {
                    Text(
                        text = summary,
                        color = AppColors.TextPrimary,
                        fontSize = AppType.BodySmall,
                        lineHeight = AppType.BodySmall * 1.55f,
                        maxLines = if (summaryExpanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { summaryExpanded = !summaryExpanded }
                    )
                }
            } else if (source?.type == RemoteSourceType.WEBDAV &&
                (format?.extension == "epub" || format?.extension == "cbz") && metadata == null
            ) {
                Spacer(Modifier.height(AppSpace.md))
                SectionCard(title = stringResource(R.string.remote_book_summary)) {
                    if (state.readingMetadata) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(AppSpace.sm))
                            Text(stringResource(R.string.remote_book_reading_info), color = AppColors.TextSecondary, fontSize = AppType.Caption)
                        }
                    } else {
                        Text(
                            text = state.metadataError?.let { stringResource(it.messageRes()) }
                                ?: stringResource(R.string.remote_book_read_info_hint),
                            color = AppColors.TextSecondary,
                            fontSize = AppType.Caption
                        )
                        Spacer(Modifier.height(AppSpace.sm))
                        RemoteHeaderPill(stringResource(R.string.remote_book_read_info), onReadMetadata)
                    }
                }
            }
            Spacer(Modifier.height(RemoteBottomBarContentPadding + 48.dp))
        }
    }

    RemoteBottomBar(blurBackdrop = blurBackdrop) {
        DownloadBar(
            state = state,
            size = size,
            hasFormat = format != null,
            onDownload = onDownload,
            onCancel = onCancel,
            onRetry = onRetry,
            onOpen = onOpen
        )
    }
    }
}

@Composable
private fun DownloadBar(
    state: RemoteBookDetailsUiState,
    size: Long,
    hasFormat: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        val download = state.downloadState
        when {
            download is RemoteDownloadState.Queued || download is RemoteDownloadState.Downloading -> {
                val progress = (download as? RemoteDownloadState.Downloading)?.progress
                Text(
                    text = remoteDownloadLabel(download).orEmpty(),
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Caption
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = AppColors.Accent,
                        trackColor = AppColors.BgGray
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = AppColors.Accent,
                        trackColor = AppColors.BgGray
                    )
                }
                RemoteSecondaryButton(stringResource(R.string.remote_book_cancel_download), onCancel)
            }
            download is RemoteDownloadState.Importing -> RemotePrimaryButton(
                text = stringResource(R.string.remote_library_importing),
                enabled = false,
                floating = false,
                onClick = {},
                leading = {
                    CircularProgressIndicator(color = AppColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
            )
            download is RemoteDownloadState.Failed -> {
                RemoteInlineNotice(stringResource(download.kind.messageRes()), isError = true)
                RemotePrimaryButton(stringResource(R.string.retry), enabled = true, floating = false, onClick = onRetry)
            }
            state.importedBookId != null -> {
                if (download is RemoteDownloadState.Completed) {
                    Text(
                        text = stringResource(
                            if (download.alreadyInLibrary) R.string.remote_book_already_in_library else R.string.remote_book_imported
                        ),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                RemotePrimaryButton(
                    text = stringResource(R.string.remote_book_open),
                    enabled = true,
                    floating = false,
                    onClick = { onOpen(state.importedBookId) }
                )
            }
            !hasFormat -> RemotePrimaryButton(
                text = stringResource(R.string.remote_book_no_downloadable_format),
                enabled = false,
                floating = false,
                onClick = {}
            )
            else -> RemotePrimaryButton(
                text = formatRemoteSize(size)?.let { stringResource(R.string.remote_book_download_with_size, it) }
                    ?: stringResource(R.string.remote_book_download),
                enabled = true,
                floating = false,
                onClick = onDownload
            )
        }
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    val shape = AppRoundedCornerShape(AppRadius.md)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .lumiCardSurface(shape = shape)
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm)
    ) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(vertical = AppSpace.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = AppColors.TextSecondary, fontSize = AppType.BodySmall, modifier = Modifier.width(88.dp))
                Spacer(Modifier.width(AppSpace.sm))
                Text(
                    text = value,
                    color = AppColors.TextPrimary,
                    fontSize = AppType.BodySmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val shape = AppRoundedCornerShape(AppRadius.md)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .lumiCardSurface(shape = shape)
            .padding(AppSpace.md)
    ) {
        Text(title, color = AppColors.TextPrimary, fontSize = AppType.Body, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(AppSpace.sm))
        content()
    }
}

@Composable
private fun FormatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = AppRoundedCornerShape(AppRadius.full)
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            // Plain fill: lumiCardSurface swaps colour for the scene glass, which would hide the selection.
            .background(if (selected) AppColors.Accent else AppColors.BgGray, shape)
            .border(BorderStroke(1.dp, if (selected) AppColors.Accent else AppColors.Divider), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.OnAccent else AppColors.TextPrimary,
            fontSize = AppType.BodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RemoteSecondaryButton(text: String, onClick: () -> Unit) {
    com.huangder.lumibooks.ui.components.LiquidGlassButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = AppRoundedCornerShape(AppRadius.lg),
        tintedColor = AppColors.BgGray,
        contentColor = AppColors.TextPrimary
    ) {
        Text(text, color = AppColors.TextPrimary, fontSize = AppType.Body, fontWeight = FontWeight.SemiBold)
    }
}
