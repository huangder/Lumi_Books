package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.sync.WebdavLibraryOverview
import com.huangder.lumibooks.data.sync.WebdavSyncStatus
import com.huangder.lumibooks.ui.components.AppBottomSheet
import com.huangder.lumibooks.ui.components.AppSheetHeader
import com.huangder.lumibooks.ui.components.GroupedCard
import com.huangder.lumibooks.ui.components.GroupedRowDivider
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LumiActionRow
import com.huangder.lumibooks.ui.components.LumiInlineBanner
import com.huangder.lumibooks.ui.components.LumiReveal
import com.huangder.lumibooks.ui.components.LumiRow
import com.huangder.lumibooks.ui.components.appSheetMaxHeight
import com.huangder.lumibooks.ui.components.appSheetPadding
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.home.WebdavOverviewState
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import java.text.DateFormat
import java.util.Date

/**
 * Cloud sync status: what the sync is doing (or why it failed), where the shelf's book files
 * live, and the actions that resolve each case.
 */
@Composable
internal fun WebdavSyncStatusSheet(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val status by viewModel.webdavSyncStatus.collectAsStateWithLifecycle()
    val config by viewModel.webdavConfig.collectAsStateWithLifecycle(initialValue = null)
    val overview by viewModel.webdavOverview.collectAsStateWithLifecycle()
    val downloadingAll by viewModel.isDownloadingAllCloudBooks.collectAsStateWithLifecycle()
    val enabled = config?.enabled == true

    LaunchedEffect(enabled) { if (enabled) viewModel.loadWebdavOverview() }
    DisposableEffect(Unit) { onDispose { viewModel.resetWebdavOverview() } }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = appSheetMaxHeight())
                .verticalScroll(rememberScrollState())
                .padding(appSheetPadding(horizontal = AppSpace.md)),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md)
        ) {
            AppSheetHeader(title = stringResource(R.string.webdav_status_title), onClose = onDismiss)

            SyncStatusHero(status, config?.lastSyncTime ?: 0L, enabled)

            if (config != null && !enabled) {
                LumiInlineBanner(
                    message = stringResource(R.string.webdav_status_disabled_body),
                    destructive = false,
                    actionLabel = stringResource(R.string.webdav_status_configure),
                    onAction = onOpenSettings
                )
            }

            if (enabled) {
                Text(
                    stringResource(R.string.webdav_status_books_section),
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Footnote,
                    modifier = Modifier.padding(start = AppSpace.md)
                )
                when (val state = overview) {
                    WebdavOverviewState.Idle, WebdavOverviewState.Loading -> GroupedCard(horizontalPadding = 0.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(AppSpace.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp), color = AppColors.TextSecondary, strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(AppSpace.sm))
                            Text(
                                stringResource(R.string.webdav_status_overview_loading),
                                color = AppColors.TextSecondary,
                                fontSize = AppType.Footnote
                            )
                        }
                    }
                    is WebdavOverviewState.Failed -> LumiInlineBanner(
                        message = stringResource(R.string.webdav_status_overview_failed, state.message),
                        actionLabel = stringResource(R.string.webdav_status_retry),
                        onAction = viewModel::loadWebdavOverview
                    )
                    is WebdavOverviewState.Loaded -> BookPlacementCard(state.overview)
                }

                val isRunning = status is WebdavSyncStatus.Running
                LiquidGlassButton(
                    onClick = { if (!isRunning) viewModel.syncWebdavNow() },
                    enabled = !isRunning,
                    tintedColor = AppColors.Accent,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) {
                    Icon(AppIcons.ArrowsClockwise, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpace.sm))
                    Text(
                        stringResource(if (isRunning) R.string.webdav_syncing else R.string.webdav_sync_now),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            GroupedCard(horizontalPadding = 0.dp) {
                val remoteOnly = (overview as? WebdavOverviewState.Loaded)?.overview?.remoteOnlyBookIds.orEmpty()
                if (enabled && (remoteOnly.isNotEmpty() || downloadingAll)) {
                    LumiActionRow(
                        title = if (downloadingAll) {
                            stringResource(R.string.webdav_status_downloading_all)
                        } else {
                            stringResource(R.string.webdav_status_download_all, remoteOnly.size)
                        },
                        icon = AppIcons.CloudArrowDown,
                        external = true,
                        enabled = !downloadingAll && status !is WebdavSyncStatus.Running,
                        onClick = viewModel::downloadAllCloudBooks
                    )
                    GroupedRowDivider()
                }
                LumiActionRow(
                    title = stringResource(R.string.webdav_status_view_logs),
                    icon = AppIcons.FileText,
                    onClick = onOpenLogs
                )
                GroupedRowDivider()
                LumiActionRow(
                    title = stringResource(R.string.title_webdav),
                    icon = AppIcons.Cloud,
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun SyncStatusHero(status: WebdavSyncStatus, lastSyncTime: Long, enabled: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    val lastSync = if (lastSyncTime > 0) {
        stringResource(R.string.webdav_status_last_sync, dateFormat.format(Date(lastSyncTime)))
    } else {
        stringResource(R.string.webdav_status_never_synced)
    }
    val tone: Color
    val icon: ImageVector
    val headline: String
    val detail: String
    when {
        !enabled && status !is WebdavSyncStatus.Running -> {
            tone = AppColors.TextSecondary
            icon = AppIcons.CloudSlash
            headline = stringResource(R.string.webdav_status_disabled_title)
            detail = lastSync
        }
        status is WebdavSyncStatus.Running -> {
            tone = AppColors.Accent
            icon = AppIcons.ArrowsClockwise
            headline = stringResource(webdavPhaseTitle(status.phase))
            detail = status.bookTitle?.let { book ->
                if (status.total > 0) stringResource(R.string.webdav_banner_progress, book, status.done, status.total)
                else book
            } ?: stringResource(R.string.webdav_status_syncing)
        }
        status is WebdavSyncStatus.Failed -> {
            tone = AppColors.Destructive
            icon = AppIcons.Warning
            headline = stringResource(R.string.webdav_status_failed)
            detail = status.message
        }
        status is WebdavSyncStatus.Succeeded -> {
            tone = AppColors.Accent
            icon = AppIcons.CheckCircle
            headline = stringResource(R.string.webdav_status_succeeded)
            detail = lastSync
        }
        else -> {
            tone = AppColors.Accent
            icon = AppIcons.Cloud
            headline = stringResource(R.string.webdav_status_idle)
            detail = lastSync
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AppSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).background(tone.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (status is WebdavSyncStatus.Running) {
                val fraction = status.fraction
                if (fraction != null) {
                    CircularProgressIndicator(
                        progress = { fraction }, color = tone, trackColor = tone.copy(alpha = 0.16f),
                        strokeWidth = 2.5.dp, modifier = Modifier.size(52.dp)
                    )
                } else {
                    CircularProgressIndicator(color = tone, strokeWidth = 2.5.dp, modifier = Modifier.size(52.dp))
                }
            }
            Icon(icon, null, tint = tone, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(AppSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                headline,
                color = AppColors.TextPrimary,
                fontSize = AppType.Headline,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(2.dp))
            Text(
                detail,
                color = if (status is WebdavSyncStatus.Failed) AppColors.Destructive else AppColors.TextSecondary,
                fontSize = AppType.Footnote,
                maxLines = 6
            )
            if (status is WebdavSyncStatus.Failed && lastSyncTime > 0) {
                Text(lastSync, color = AppColors.TextSecondary, fontSize = AppType.Caption)
            }
        }
    }
}

@Composable
private fun BookPlacementCard(overview: WebdavLibraryOverview) {
    var showMissingHelp by remember { mutableStateOf(false) }
    GroupedCard(horizontalPadding = 0.dp) {
        LumiRow(
            title = stringResource(R.string.webdav_status_both),
            icon = AppIcons.CheckCircle,
            iconTint = Color(0xFF34C759),
            value = stringResource(R.string.webdav_status_book_count, overview.both)
        )
        GroupedRowDivider(startInset = 60.dp)
        LumiRow(
            title = stringResource(R.string.webdav_status_local_only),
            icon = AppIcons.DeviceMobile,
            iconTint = Color(0xFFFF9F0A),
            value = stringResource(R.string.webdav_status_book_count, overview.localOnly)
        )
        GroupedRowDivider(startInset = 60.dp)
        LumiRow(
            title = stringResource(R.string.webdav_status_remote_only),
            icon = AppIcons.Cloud,
            iconTint = Color(0xFF0A84FF),
            value = stringResource(R.string.webdav_status_book_count, overview.remoteOnly)
        )
        GroupedRowDivider(startInset = 60.dp)
        LumiRow(
            title = stringResource(R.string.webdav_status_missing),
            icon = AppIcons.Warning,
            iconTint = Color(0xFFFF453A),
            value = stringResource(R.string.webdav_status_book_count, overview.missing),
            onClick = { showMissingHelp = !showMissingHelp }
        ) {
            Icon(
                AppIcons.Info,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(18.dp).clip(CircleShape)
            )
        }
        LumiReveal(showMissingHelp) {
            Text(
                stringResource(R.string.webdav_status_missing_help),
                color = AppColors.TextSecondary,
                fontSize = AppType.Footnote,
                modifier = Modifier.padding(start = 60.dp, end = AppSpace.md, bottom = 12.dp)
            )
        }
    }
}
