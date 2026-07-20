package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.TtsPlaybackState
import java.util.Locale

@Composable
fun TtsPlayerPanel(
    playbackState: TtsPlaybackState,
    speechRate: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onRateChange: (Float) -> Unit,
    readerBackgroundColor: Color,
    readerContentColor: Color,
    modifier: Modifier = Modifier
) {
    val rateOptions = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f) }
    var showRateMenu by remember { mutableStateOf(false) }
    val capsuleShape = RoundedCornerShape(28.dp)
    val rateMenuShape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = 8.dp,
                shape = capsuleShape,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.14f)
            )
            .clip(capsuleShape)
            .background(readerBackgroundColor)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSkipBackward, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.tts_previous_sentence),
                tint = readerContentColor
            )
        }

        IconButton(
            onClick = onPlayPause,
            enabled = playbackState != TtsPlaybackState.INITIALIZING,
            modifier = Modifier.size(40.dp)
        ) {
            if (playbackState == TtsPlaybackState.INITIALIZING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = readerContentColor
                )
            } else {
                Icon(
                    if (playbackState == TtsPlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (playbackState == TtsPlaybackState.PLAYING) R.string.tts_pause else R.string.tts_play
                    ),
                    tint = readerContentColor
                )
            }
        }

        IconButton(onClick = onSkipForward, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.tts_next_sentence),
                tint = readerContentColor
            )
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showRateMenu = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatSpeechRate(speechRate),
                    color = readerContentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            DropdownMenu(
                expanded = showRateMenu,
                onDismissRequest = { showRateMenu = false },
                modifier = Modifier.width(112.dp),
                shape = rateMenuShape,
                containerColor = readerBackgroundColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.24f))
            ) {
                rateOptions.forEach { rate ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = formatSpeechRate(rate),
                                color = readerContentColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        onClick = {
                            showRateMenu = false
                            onRateChange(rate)
                        }
                    )
                }
            }
        }

        IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.tts_stop),
                tint = readerContentColor
            )
        }
    }
}

private fun formatSpeechRate(rate: Float): String {
    val formatted = String.format(Locale.US, "%.2f", rate)
        .trimEnd('0')
        .trimEnd('.')
    return "${formatted}x"
}
