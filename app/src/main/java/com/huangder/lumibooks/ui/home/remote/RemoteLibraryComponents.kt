package com.huangder.lumibooks.ui.home.remote

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.remote.RemoteBookFiles
import com.huangder.lumibooks.data.remote.RemoteDownloadState
import com.huangder.lumibooks.data.remote.RemoteErrorKind
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.home.ImportDestinationContainer
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType

/** Authenticated cover loader for the library being browsed; null falls back to Coil's default. */
val LocalRemoteImageLoader = androidx.compose.runtime.staticCompositionLocalOf<coil.ImageLoader?> { null }

@StringRes
fun RemoteErrorKind.messageRes(): Int = when (this) {
    RemoteErrorKind.AUTH -> R.string.remote_error_auth
    RemoteErrorKind.FORBIDDEN -> R.string.remote_error_forbidden
    RemoteErrorKind.NOT_FOUND -> R.string.remote_error_not_found
    RemoteErrorKind.SERVER -> R.string.remote_error_server
    RemoteErrorKind.NETWORK -> R.string.remote_error_network
    RemoteErrorKind.TIMEOUT -> R.string.remote_error_timeout
    RemoteErrorKind.TLS -> R.string.remote_error_tls
    RemoteErrorKind.INVALID_URL -> R.string.remote_error_invalid_url
    RemoteErrorKind.NOT_A_CATALOG -> R.string.remote_error_not_catalog
    RemoteErrorKind.OPDS2_UNSUPPORTED -> R.string.remote_error_opds2
    RemoteErrorKind.TOO_LARGE -> R.string.remote_error_too_large
    RemoteErrorKind.NO_SPACE -> R.string.remote_error_no_space
    RemoteErrorKind.LOCAL_NETWORK_PERMISSION -> R.string.remote_error_local_network
    RemoteErrorKind.UNSUPPORTED_FORMAT -> R.string.remote_error_unsupported_format
    RemoteErrorKind.INVALID_FILE -> R.string.remote_error_invalid_file
    RemoteErrorKind.DESTINATION_UNAVAILABLE -> R.string.remote_error_destination
    RemoteErrorKind.UNKNOWN -> R.string.remote_error_unknown
}

@Composable
fun formatRemoteSize(bytes: Long): String? {
    if (bytes <= 0L) return null
    return Formatter.formatShortFileSize(LocalContext.current, bytes)
}

/** Short status text for a book row; null when the book is idle. */
@Composable
fun remoteDownloadLabel(state: RemoteDownloadState?): String? = when (state) {
    null, is RemoteDownloadState.Completed -> null
    RemoteDownloadState.Queued -> stringResource(R.string.remote_library_queued)
    RemoteDownloadState.Importing -> stringResource(R.string.remote_library_importing)
    is RemoteDownloadState.Failed -> stringResource(R.string.remote_library_failed)
    is RemoteDownloadState.Downloading -> state.progress?.let {
        stringResource(R.string.remote_library_downloading, (it * 100).toInt())
    } ?: stringResource(R.string.remote_library_downloading_indeterminate)
}

/**
 * Remote cover with the same format-tinted placeholder as the import confirmation grid, so a
 * missing or slow cover never leaves an empty hole.
 */
@Composable
fun RemoteBookCover(
    model: Any?,
    extension: String?,
    title: String,
    shape: Shape,
    placeholderFontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val brush = when (extension?.lowercase()) {
        "epub" -> Brush.linearGradient(listOf(Color(0xFF9BB7D4), Color(0xFF657D9A)))
        "pdf" -> Brush.linearGradient(listOf(Color(0xFFDFA19C), Color(0xFFB95E5B)))
        "mobi" -> Brush.linearGradient(listOf(Color(0xFFA8C3A0), Color(0xFF5F8A5C)))
        "cbz" -> Brush.linearGradient(listOf(Color(0xFFE0B87C), Color(0xFFB07A3C)))
        else -> Brush.linearGradient(listOf(Color(0xFFB8B0D6), Color(0xFF8179A8)))
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        val placeholder: @Composable () -> Unit = {
            Text(
                text = RemoteBookFiles.displayFormat(extension).ifEmpty { "BOOK" },
                color = Color.White.copy(alpha = 0.88f),
                fontSize = placeholderFontSize,
                fontWeight = FontWeight.Bold
            )
        }
        if (model == null) {
            placeholder()
        } else {
            val imageLoader = LocalRemoteImageLoader.current
            SubcomposeAsyncImage(
                model = model,
                imageLoader = imageLoader ?: coil.Coil.imageLoader(LocalContext.current),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() } }
            )
        }
    }
}

data class DestinationChoice(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val primary: Boolean,
    val onClick: () -> Unit
)

/** "Where to download to" — the import sheet's container, so it matches every theme. */
@Composable
fun RemoteDestinationSheet(
    bookCount: Int,
    choices: List<DestinationChoice>,
    onDismiss: () -> Unit
) {
    ImportDestinationContainer(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.lg)
        ) {
            Text(
                text = stringResource(R.string.remote_destination_title),
                color = AppColors.TextPrimary,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold
            )
            if (bookCount > 1) {
                Spacer(Modifier.size(AppSpace.xs))
                Text(
                    text = stringResource(R.string.remote_destination_count, bookCount),
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Caption
                )
            }
            Spacer(Modifier.size(AppSpace.md))
            Column(verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                choices.forEach { choice -> DestinationButton(choice) }
            }
        }
    }
}

@Composable
private fun DestinationButton(choice: DestinationChoice) {
    val contentColor = if (choice.primary) AppColors.OnAccent else AppColors.TextPrimary
    LiquidGlassButton(
        onClick = choice.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = AppRoundedCornerShape(AppRadius.lg),
        tintedColor = if (choice.primary) AppColors.Accent else AppColors.BgGray,
        prominentShadow = choice.primary,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.xs, vertical = AppSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = choice.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = choice.title,
                    color = contentColor,
                    fontSize = AppType.Body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = choice.detail,
                    color = if (choice.primary) AppColors.OnAccent.copy(alpha = 0.78f) else AppColors.TextSecondary,
                    fontSize = AppType.Caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
