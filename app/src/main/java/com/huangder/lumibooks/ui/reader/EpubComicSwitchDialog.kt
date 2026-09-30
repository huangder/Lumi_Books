package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.theme.AppColors
import com.kyant.backdrop.Backdrop

@Composable
internal fun EpubComicSwitchDialog(viewModel: ReaderViewModel, backdrop: Backdrop? = null) {
    val state by viewModel.uiState.collectAsState()
    if (!state.epubComicPreparing && !state.epubComicConfirmation && state.epubComicError == null) return
    LiquidGlassAlertDialog(
        onDismissRequest = viewModel::cancelEpubComicSwitch,
        backdrop = backdrop,
        title = { Text(stringResource(R.string.epub_comic_switch), color = AppColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.epubComicPreparing) CircularProgressIndicator(color = AppColors.Accent)
                Text(state.epubComicError ?: stringResource(
                    if (state.epubComicPreparing) R.string.epub_comic_preparing else R.string.epub_comic_images_only),
                    color = AppColors.TextSecondary, lineHeight = 22.sp)
            }
        },
        confirmButton = {
            if (state.epubComicConfirmation) LiquidGlassTextButton(
                text = stringResource(R.string.epub_comic_continue),
                onClick = viewModel::confirmEpubComicSwitch,
                tintedColor = AppColors.Accent
            )
        },
        dismissButton = {
            LiquidGlassTextButton(
                text = stringResource(if (state.epubComicError != null) android.R.string.ok else android.R.string.cancel),
                onClick = viewModel::cancelEpubComicSwitch
            )
        }
    )
}
