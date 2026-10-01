package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R

@Composable
internal fun EpubNavigationLoadingOverlay(
    chapterTitle: String,
    stage: EpubNavigationStage,
    backgroundColor: Color,
    contentColor: Color,
    onCancel: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val status = when (stage) {
        EpubNavigationStage.STARTING -> stringResource(R.string.epub_navigation_preparing)
        EpubNavigationStage.LOADING_DOCUMENT -> stringResource(R.string.epub_navigation_loading)
        EpubNavigationStage.CONFIGURING -> stringResource(R.string.epub_navigation_configuring)
        EpubNavigationStage.WAITING_FOR_FRAME,
        EpubNavigationStage.ANIMATING -> stringResource(R.string.epub_navigation_rendering)
    }
    Surface(
        modifier = modifier
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .testTag("epubNavigationLoading")
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Final).changes.forEach {
                            if (!it.isConsumed) it.consume()
                        }
                    }
                }
            },
        color = backgroundColor,
        contentColor = contentColor
    ) {
        if (compact) {
            Row(
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = contentColor, strokeWidth = 2.dp)
                Text(status, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), color = contentColor)
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), color = contentColor) }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
            Text(
                text = chapterTitle,
                modifier = Modifier.padding(top = 24.dp),
                color = contentColor,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = status,
                modifier = Modifier.padding(top = 8.dp),
                color = contentColor.copy(alpha = 0.68f),
                fontSize = 14.sp
            )
            TextButton(
                onClick = onCancel,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.cancel), color = contentColor)
            }
        }
        }
    }
}

@Composable
internal fun EpubNavigationFailureBar(
    reason: EpubNavigationFailureReason,
    backgroundColor: Color,
    contentColor: Color,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSwitchLayout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val message = when (reason) {
        EpubNavigationFailureReason.TIMEOUT -> stringResource(R.string.epub_navigation_timeout)
        else -> stringResource(R.string.epub_navigation_failed)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("epubNavigationFailure"),
        color = backgroundColor,
        contentColor = contentColor,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = message,
                color = contentColor,
                fontSize = 14.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry), color = contentColor)
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = contentColor.copy(alpha = 0.75f))
            }
            TextButton(onClick = onSwitchLayout) {
                Text(stringResource(R.string.epub_switch_reader_layout), color = contentColor)
            }
            }
        }
    }
}

@Composable
internal fun EpubRenderFailureOverlay(
    failure: EpubRenderFailure,
    backgroundColor: Color,
    contentColor: Color,
    onRetry: () -> Unit,
    onSwitchLayout: () -> Unit,
    onClose: () -> Unit
) {
    val message = when (failure.reason) {
        EpubInitialLoadFailureReason.PAGE_READY_TIMEOUT -> R.string.epub_load_timeout_kept
        EpubInitialLoadFailureReason.RENDERER_GONE -> R.string.epub_renderer_stopped_kept
        EpubInitialLoadFailureReason.READER_SCRIPT_MISSING -> R.string.epub_script_missing_kept
        EpubInitialLoadFailureReason.SCRIPT_EXECUTION_ERROR -> R.string.epub_script_error_kept
        else -> R.string.epub_document_error_kept
    }
    Surface(Modifier.fillMaxSize().testTag("epubRenderFailure"), color = backgroundColor) {
        Column(Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(message), color = contentColor, textAlign = TextAlign.Center)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            TextButton(onClick = onSwitchLayout) { Text(stringResource(R.string.epub_switch_reader_layout)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.epub_close_book)) }
        }
    }
}
