package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.sync.WebdavSyncPhase
import com.huangder.lumibooks.data.sync.WebdavSyncStatus
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLumiBackgroundBackdrop
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import kotlinx.coroutines.delay

private const val SUCCESS_LINGER_MILLIS = 2_200L

/**
 * Floating cloud-sync capsule under the bookshelf header. Running syncs show the phase and the
 * book being processed; success lingers briefly; failures stay until closed. Tapping opens the
 * sync status sheet.
 */
@Composable
internal fun WebdavSyncBanner(
    status: WebdavSyncStatus,
    dismissedFailureAt: Long,
    onDismissFailure: (Long) -> Unit,
    onOpenStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var successExpired by remember(status) { mutableStateOf(false) }
    LaunchedEffect(status) {
        if (status is WebdavSyncStatus.Succeeded) {
            val remaining = SUCCESS_LINGER_MILLIS - (System.currentTimeMillis() - status.finishedAt)
            if (remaining > 0) delay(remaining)
            successExpired = true
        }
    }
    val visible = when (status) {
        is WebdavSyncStatus.Running -> true
        is WebdavSyncStatus.Succeeded -> !successExpired
        is WebdavSyncStatus.Failed -> status.finishedAt != dismissedFailureAt
        WebdavSyncStatus.Idle -> false
    }
    // Keep the last shown content while the capsule animates out.
    var shown by remember { mutableStateOf<WebdavSyncStatus>(WebdavSyncStatus.Idle) }
    if (visible) shown = status
    val motion = LocalMotionEnabled.current

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motion) fadeIn() + expandHorizontally(expandFrom = Alignment.Start) else fadeIn(),
        exit = if (motion) fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start) else fadeOut()
    ) {
        WebdavSyncCapsule(
            status = shown,
            onClick = onOpenStatus,
            onClose = (shown as? WebdavSyncStatus.Failed)?.let { failed -> { onDismissFailure(failed.finishedAt) } }
        )
    }
}

@Composable
private fun WebdavSyncCapsule(
    status: WebdavSyncStatus,
    onClick: () -> Unit,
    onClose: (() -> Unit)?
) {
    val failed = status is WebdavSyncStatus.Failed
    val tone = if (failed) AppColors.Destructive else AppColors.Accent
    val title: String
    val subtitle: String?
    val icon: ImageVector
    when (status) {
        is WebdavSyncStatus.Running -> {
            title = stringResource(webdavPhaseTitle(status.phase))
            subtitle = status.bookTitle?.let { book ->
                if (status.total > 0) {
                    stringResource(R.string.webdav_banner_progress, book, status.done, status.total)
                } else book
            }
            icon = when (status.phase) {
                WebdavSyncPhase.UPLOADING_BOOKS, WebdavSyncPhase.COMMIT -> AppIcons.CloudArrowUp
                WebdavSyncPhase.REMOTE_CHANGES, WebdavSyncPhase.MANIFEST -> AppIcons.CloudArrowDown
                else -> AppIcons.Cloud
            }
        }
        is WebdavSyncStatus.Succeeded -> {
            title = stringResource(R.string.webdav_banner_done)
            subtitle = null
            icon = AppIcons.CheckCircle
        }
        is WebdavSyncStatus.Failed -> {
            title = stringResource(R.string.webdav_banner_failed)
            subtitle = stringResource(R.string.webdav_banner_failed_hint)
            icon = AppIcons.Warning
        }
        WebdavSyncStatus.Idle -> {
            title = ""
            subtitle = null
            icon = AppIcons.Cloud
        }
    }
    LiquidGlassSurface(
        shape = CircleShape,
        controlEdge = true,
        backdrop = LocalLiquidGlassBackdrop.current ?: LocalLumiBackgroundBackdrop.current,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = if (LocalIsDarkTheme.current) 0.62f else 0.74f),
        onClick = onClick,
        modifier = Modifier.wrapContentWidth().height(36.dp).widthIn(max = 210.dp)
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = if (onClose != null) 2.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SyncProgressBadge(status, icon, tone)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    title,
                    color = if (failed) tone else AppColors.TextPrimary,
                    fontSize = AppType.Caption,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption2,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onClose != null) {
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.X,
                        contentDescription = stringResource(R.string.webdav_banner_dismiss),
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncProgressBadge(status: WebdavSyncStatus, icon: ImageVector, tone: Color) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(28.dp).background(tone.copy(alpha = 0.12f), CircleShape)
        )
        if (status is WebdavSyncStatus.Running) {
            val fraction = status.fraction
            if (fraction != null) {
                val animated by animateFloatAsState(fraction, spring(stiffness = 300f), label = "syncRing")
                CircularProgressIndicator(
                    progress = { animated },
                    color = tone,
                    trackColor = tone.copy(alpha = 0.18f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                CircularProgressIndicator(color = tone, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
    }
}

internal fun webdavPhaseTitle(phase: WebdavSyncPhase): Int = when (phase) {
    WebdavSyncPhase.PREPARING -> R.string.webdav_phase_preparing
    WebdavSyncPhase.MANIFEST -> R.string.webdav_phase_manifest
    WebdavSyncPhase.REMOTE_CHANGES -> R.string.webdav_phase_remote_changes
    WebdavSyncPhase.UPLOADING_BOOKS -> R.string.webdav_phase_uploading_books
    WebdavSyncPhase.BOOK_DATA -> R.string.webdav_phase_book_data
    WebdavSyncPhase.COMMIT -> R.string.webdav_phase_commit
    WebdavSyncPhase.PORTABLE_STATE -> R.string.webdav_phase_portable_state
}
