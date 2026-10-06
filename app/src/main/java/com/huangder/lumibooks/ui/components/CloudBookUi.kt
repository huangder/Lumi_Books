package com.huangder.lumibooks.ui.components
import com.huangder.lumibooks.ui.icons.AppIcons

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.sync.BookDownloadState
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookDeleteMode
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalCoverReadingProgressEnabled

@Composable
fun CloudBookDownloadDialog(
    book: Book,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    val size = if (book.remoteFileSize > 0L) {
        Formatter.formatShortFileSize(context, book.remoteFileSize)
    } else {
        stringResource(R.string.book_download_size_unknown)
    }
    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.book_download_title, book.title)) },
        text = { Text(stringResource(R.string.book_download_confirm, size)) },
        confirmButton = {
            LiquidGlassTextButton(
                text = stringResource(R.string.download_book_file),
                onClick = onDownload
            )
        },
        dismissButton = {
            LiquidGlassTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
fun CloudAwareBookDeleteDialog(
    bookCount: Int,
    hasRemoteBooks: Boolean,
    onDismiss: () -> Unit,
    onDelete: (BookDeleteMode) -> Unit
) {
    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (bookCount == 1) {
                    stringResource(R.string.delete_book)
                } else {
                    stringResource(R.string.delete_selected_books_title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (hasRemoteBooks) {
                    stringResource(R.string.delete_cloud_book_choice, bookCount)
                } else {
                    stringResource(R.string.delete_selected_books_confirm, bookCount)
                })
                Text(
                    text = stringResource(R.string.force_delete_hint),
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasRemoteBooks) {
                    LiquidGlassTextButton(
                        text = stringResource(R.string.delete_local_only),
                        tintedColor = Color(0xFFD92D3A),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDelete(BookDeleteMode.LOCAL_ONLY) }
                    )
                    LiquidGlassTextButton(
                        text = stringResource(R.string.delete_local_and_cloud),
                        tintedColor = Color(0xFFD92D3A),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDelete(BookDeleteMode.LOCAL_AND_CLOUD) }
                    )
                } else {
                    LiquidGlassTextButton(
                        text = stringResource(R.string.delete),
                        tintedColor = Color(0xFFD92D3A),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDelete(BookDeleteMode.LOCAL_ONLY) }
                    )
                }
                LiquidGlassTextButton(
                    text = stringResource(R.string.force_delete),
                    tintedColor = Color(0xFFB42318),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDelete(BookDeleteMode.FORCE_LOCAL_AND_CLOUD) }
                )
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                )
            }
        }
    )
}

/** Cover status follows the progress preference; cloud affordances remain visible in both modes. */
@Composable
fun BookCoverStatusBadge(
    book: Book,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (book.isCloudOnly) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(if (compact) 5.dp else 7.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 24.dp else 30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.CloudArrowDown,
                    contentDescription = stringResource(R.string.download_book_file),
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 15.dp else 18.dp)
                )
            }
        }
    } else {
        BookCoverReadingProgress(book, modifier, compact)
    }
}

@Composable
fun BookCoverReadingProgress(
    book: Book,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (!LocalCoverReadingProgressEnabled.current || book.isCloudOnly) return
    val cornerRadius = 8.dp
    Box(
        modifier = modifier.fillMaxSize().padding(if (compact) 5.dp else 7.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        if (book.isReadingFinished) {
            FinishedReadingIndicator(
                compact = compact,
                showContainer = true,
                tint = Color.White,
                containerCornerRadius = cornerRadius
            )
        } else {
            Text(
                text = formatReadingProgressPercent(book.readingProgress),
                modifier = Modifier
                    .clip(AppRoundedCornerShape(cornerRadius))
                    .background(Color.Black.copy(alpha = 0.64f))
                    .padding(horizontal = if (compact) 4.dp else 5.dp, vertical = 2.dp),
                fontSize = if (compact) 10.sp else 12.sp,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

/** Book status for metadata rows outside the cover. */
@Composable
fun BookProgressStatus(
    book: Book,
    downloadState: BookDownloadState? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    color: Color = AppColors.TextSecondary,
    progressColor: Color = AppColors.Accent
) {
    val downloading = downloadState as? BookDownloadState.Downloading
    if (LocalCoverReadingProgressEnabled.current && !book.isCloudOnly && downloading == null) return
    when {
        downloading != null -> Text(
            text = stringResource(
                R.string.book_download_progress,
                (downloading.progress * 100f).toInt().coerceIn(0, 100)
            ),
            modifier = modifier,
            fontSize = if (compact) 10.sp else 12.sp,
            color = color,
            maxLines = 1
        )
        book.isCloudOnly -> Text(
            text = stringResource(R.string.book_detail_cloud_only),
            modifier = modifier,
            fontSize = if (compact) 10.sp else 12.sp,
            color = color,
            maxLines = 1
        )
        book.isReadingFinished -> FinishedReadingIndicator(
            modifier = modifier,
            compact = compact,
            tint = color
        )
        else -> Text(
            text = formatReadingProgressPercent(book.readingProgress),
            modifier = modifier,
            fontSize = if (compact) 10.sp else 12.sp,
            color = progressColor,
            maxLines = 1
        )
    }
}

internal fun formatReadingProgressPercent(progress: Float): String {
    val pct = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f)?.times(100f) ?: 0f
    return if (pct < 10f) {
        String.format(Locale.ROOT, "%.1f%%", pct)
    } else {
        "${pct.toInt()}%"
    }
}

@Composable
fun FinishedReadingIndicator(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showContainer: Boolean = false,
    tint: Color? = null,
    containerCornerRadius: Dp = 4.dp
) {
    val indicatorColor = tint ?: AppColors.TextSecondary
    val circleColor = if (showContainer) Color(0xFF8E8E93) else indicatorColor
    val contentModifier = if (showContainer) {
        modifier
            .clip(AppRoundedCornerShape(containerCornerRadius))
            .background(Color.Black.copy(alpha = 0.64f))
            .padding(horizontal = if (compact) 3.dp else 4.dp, vertical = 1.dp)
    } else {
        modifier
    }

    Row(
        modifier = contentModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 11.dp else 13.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (compact) 7.dp else 9.dp)
            )
        }
        Spacer(Modifier.width(if (compact) 3.dp else 4.dp))
        Text(
            text = stringResource(R.string.book_read_finished),
            color = indicatorColor,
            fontSize = if (compact) 8.sp else 10.sp,
            maxLines = 1
        )
    }
}
