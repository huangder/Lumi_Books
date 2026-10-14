package com.huangder.lumibooks.ui.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.TtsListeningPosition
import com.huangder.lumibooks.tts.TtsStartChoice
import com.kyant.backdrop.Backdrop

@Composable
internal fun TtsStartDialog(
    position: TtsListeningPosition?,
    chapterTitle: String,
    paperColor: Color,
    contentColor: Color,
    eInkMode: Boolean,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    onChoose: (TtsStartChoice, Boolean) -> Unit
) {
    val target = position ?: return
    var rememberChoice by remember(target) { mutableStateOf(false) }
    ReaderListeningDialog(
        titleText = stringResource(R.string.tts_start_title),
        confirmText = stringResource(R.string.tts_start_current_page),
        dismissText = stringResource(R.string.tts_start_saved_position),
        paperColor = paperColor, contentColor = contentColor, eInkMode = eInkMode, backdrop = backdrop,
        onDismissRequest = onDismiss,
        onConfirm = { onChoose(TtsStartChoice.CURRENT_PAGE, rememberChoice) },
        onDismiss = { onChoose(TtsStartChoice.SAVED_POSITION, rememberChoice) }
    ) { ink ->
        Column {
            Text(stringResource(R.string.tts_start_message, chapterTitle), color = ink, lineHeight = 22.sp)
            ReaderRememberChoice(rememberChoice, ink,
                if (eInkMode) Color.White else paperColor.copy(alpha = 1f),
                onCheckedChange = { rememberChoice = it })
        }
    }
}

/** Keep the chosen start location through the notification permission result. */
@Composable
internal fun rememberTtsStartWithNotificationPermission(
    bookId: String,
    onStart: (Boolean) -> Unit
): (Boolean) -> Unit {
    val context = LocalContext.current
    val latestStart by rememberUpdatedState(onStart)
    var pendingRestore by rememberSaveable(bookId) { mutableStateOf<Boolean?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Media playback remains available when notification permission is declined.
        pendingRestore?.let { restore ->
            pendingRestore = null
            latestStart(restore)
        }
    }
    return { restore ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingRestore = restore
            runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }.onFailure {
                pendingRestore = null
                latestStart(restore)
            }
        } else latestStart(restore)
    }
}
