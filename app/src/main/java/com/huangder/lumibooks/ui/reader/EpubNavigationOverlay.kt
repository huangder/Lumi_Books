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
    onCancel: () -> Unit
) {
    val status = when (stage) {
        EpubNavigationStage.STARTING -> stringResource(R.string.epub_navigation_preparing)
        EpubNavigationStage.LOADING_DOCUMENT -> stringResource(R.string.epub_navigation_loading)
        EpubNavigationStage.CONFIGURING -> stringResource(R.string.epub_navigation_configuring)
        EpubNavigationStage.WAITING_FOR_FRAME -> stringResource(R.string.epub_navigation_rendering)
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
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

@Composable
internal fun EpubNavigationFailureBar(
    reason: EpubNavigationFailureReason,
    backgroundColor: Color,
    contentColor: Color,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
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
        Row(
            modifier = Modifier
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = contentColor,
                fontSize = 14.sp
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry), color = contentColor)
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = contentColor.copy(alpha = 0.75f))
            }
        }
    }
}
