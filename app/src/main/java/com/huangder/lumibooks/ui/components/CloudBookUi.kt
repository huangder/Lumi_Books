package com.huangder.lumibooks.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.sync.BookDownloadState
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookDeleteMode
import com.huangder.lumibooks.ui.theme.AppColors

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
            }
        },
        dismissButton = {
            LiquidGlassTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

/** Drawn inside a cover box so download progress occupies the existing reading-progress slot. */
@Composable
fun BookCoverProgressOverlay(
    book: Book,
    downloadState: BookDownloadState?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val downloading = downloadState as? BookDownloadState.Downloading
    val progress = downloading?.progress ?: book.readingProgress
        .takeIf { it.isFinite() }
        ?.coerceIn(0f, 1f)
        .orEmptyProgress()
    val showProgress = downloading != null || (progress > 0f && !book.isCloudOnly)

    Box(modifier = modifier.fillMaxSize()) {
        if (book.isCloudOnly && downloading == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (compact) 5.dp else 7.dp)
                    .size(if (compact) 24.dp else 30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = stringResource(R.string.download_book_file),
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 15.dp else 18.dp)
                )
            }
        }

        if (showProgress) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (compact) 5.dp else 6.dp, bottom = if (compact) 6.dp else 7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.64f))
                    .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${(progress * 100f).toInt().coerceIn(0, 100)}%",
                    color = Color.White,
                    fontSize = if (compact) 9.sp else 10.sp
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Black.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(AppColors.Accent)
                )
            }
        }
    }
}

private fun Float?.orEmptyProgress(): Float = this ?: 0f
