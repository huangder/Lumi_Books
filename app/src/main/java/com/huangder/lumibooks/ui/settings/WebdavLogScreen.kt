package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.sync.WebdavLogLevel
import com.huangder.lumibooks.data.sync.WebdavSyncLogStore
import com.huangder.lumibooks.data.sync.WebdavSyncRun
import com.huangder.lumibooks.data.sync.WebdavSyncTrigger
import com.huangder.lumibooks.ui.components.GroupedCard
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

internal const val WEBDAV_LOGS_CATEGORY = "webdav_logs"

@Composable
internal fun WebdavLogRoute(logStore: WebdavSyncLogStore, onBack: () -> Unit) {
    var runs by remember { mutableStateOf<List<WebdavSyncRun>?>(null) }
    LaunchedEffect(logStore) { runs = logStore.recentRuns() }
    WebdavLogScreen(runs, onBack)
}

@Composable
internal fun WebdavLogScreen(runs: List<WebdavSyncRun>?, onBack: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
    }
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm:ss", locale) }
    val shareLabel = stringResource(R.string.webdav_logs_share)
    val logTitle = stringResource(R.string.webdav_logs_title)
    val manualLabel = stringResource(R.string.webdav_logs_trigger_manual)
    val autoLabel = stringResource(R.string.webdav_logs_trigger_auto)

    CollapsingSettingsScaffold(
        title = logTitle,
        onBack = onBack,
        lazyListState = rememberLazyListState(),
        actions = if (runs.isNullOrEmpty()) null else {
            {
                LiquidGlassIconButton(
                    imageVector = AppIcons.ShareNetwork,
                    contentDescription = shareLabel,
                    onClick = {
                        val text = runs.joinToString("\n\n") { run ->
                            formatRunForSharing(run, dateFormat, timeFormat,
                                if (run.trigger == WebdavSyncTrigger.AUTO) autoLabel else manualLabel)
                        }
                        shareText(context, logTitle, shareLabel, text)
                    },
                    size = 44.dp,
                    iconSize = 20.dp
                )
            }
        },
        lazyContent = {
            item {
                // Aligned with the large title and the cards' outer edge.
                Text(
                    stringResource(R.string.webdav_logs_subtitle, WebdavSyncLogStore.MAX_RUNS),
                    fontSize = AppType.Footnote,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(start = AppSpace.page, end = AppSpace.page, bottom = AppSpace.md)
                )
            }
            if (runs.isNullOrEmpty()) {
                item {
                    Text(
                        text = stringResource(if (runs == null) R.string.loading else R.string.webdav_logs_empty),
                        modifier = Modifier.padding(horizontal = AppSpace.page, vertical = AppSpace.lg),
                        fontSize = AppType.BodySmall,
                        color = AppColors.TextSecondary
                    )
                }
            } else {
                items(runs, key = { it.startedAt }) { run ->
                    WebdavLogRunCard(
                        run = run,
                        title = dateFormat.format(Date(run.startedAt)),
                        triggerLabel = if (run.trigger == WebdavSyncTrigger.AUTO) autoLabel else manualLabel,
                        timeFormat = timeFormat
                    )
                }
            }
        }
    )
}

@Composable
private fun WebdavLogRunCard(
    run: WebdavSyncRun,
    title: String,
    triggerLabel: String,
    timeFormat: SimpleDateFormat
) {
    val (statusLabel, statusColor, statusIcon) = when (run.success) {
        true -> Triple(stringResource(R.string.webdav_logs_result_success), AppColors.Accent, AppIcons.CheckCircle)
        false -> Triple(stringResource(R.string.webdav_logs_result_failed), AppColors.Destructive, AppIcons.Warning)
        null -> Triple(stringResource(R.string.webdav_logs_result_unfinished), AppColors.TextSecondary, AppIcons.Info)
    }
    GroupedCard(modifier = Modifier.padding(bottom = AppSpace.md)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AppSpace.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Spacer(Modifier.size(2.dp))
                val duration = run.finishedAt?.let { formatDuration(it - run.startedAt) }
                Text(
                    listOfNotNull(triggerLabel, duration).joinToString(" · "),
                    fontSize = AppType.Footnote,
                    color = AppColors.TextSecondary
                )
            }
            Row(
                Modifier.background(statusColor.copy(alpha = 0.12f), AppRoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(statusLabel, fontSize = AppType.Caption, color = statusColor, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = AppColors.TextSecondary.copy(alpha = 0.14f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = AppSpace.md, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            run.entries.forEach { entry ->
                val color = when (entry.level) {
                    WebdavLogLevel.ERROR -> AppColors.Destructive
                    WebdavLogLevel.WARN -> Color(0xFFD9822B)
                    WebdavLogLevel.INFO -> AppColors.TextPrimary
                }
                Row {
                    Text(
                        timeFormat.format(Date(entry.time)),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        Text(
                            entry.message,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0L) + 500L) / 1000L
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}

private fun formatRunForSharing(
    run: WebdavSyncRun,
    dateFormat: DateFormat,
    timeFormat: SimpleDateFormat,
    triggerLabel: String
): String = buildString {
    append("== ").append(dateFormat.format(Date(run.startedAt))).append(" · ").append(triggerLabel)
    append(" · ").append(
        when (run.success) {
            true -> "OK"
            false -> "FAILED"
            null -> "UNFINISHED"
        }
    ).append('\n')
    run.entries.forEach { entry ->
        append(timeFormat.format(Date(entry.time))).append(' ')
            .append(entry.level.name.padEnd(5)).append(' ')
            .append(entry.message).append('\n')
    }
}

private fun shareText(context: Context, subject: String, chooserTitle: String, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
}

