package com.huangder.lumibooks.ui.reader

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.R
import com.kyant.backdrop.Backdrop

@Composable
internal fun TtsExitDialog(
    visible: Boolean,
    paperColor: Color,
    contentColor: Color,
    eInkMode: Boolean,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    onExit: (Boolean, Boolean) -> Unit,
    floatingSubtitlesEnabled: Boolean = true
) {
    val context = LocalContext.current
    var rememberChoice by rememberSaveable(visible) { mutableStateOf(false) }
    val latestExit by rememberUpdatedState(onExit)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (visible && (!floatingSubtitlesEnabled || Settings.canDrawOverlays(context))) latestExit(true, rememberChoice)
        else Toast.makeText(context, R.string.tts_overlay_permission_needed, Toast.LENGTH_LONG).show()
    }
    if (!visible) return
    ReaderListeningDialog(
        titleText = stringResource(R.string.tts_exit_title),
        confirmText = stringResource(R.string.tts_exit_continue),
        dismissText = stringResource(R.string.tts_exit_stop),
        paperColor = paperColor, contentColor = contentColor, eInkMode = eInkMode,
        backdrop = backdrop, onDismissRequest = onDismiss,
        onConfirm = {
            if (!floatingSubtitlesEnabled || Settings.canDrawOverlays(context)) onExit(true, rememberChoice)
            else runCatching {
                launcher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")))
            }.onFailure {
                Toast.makeText(context, R.string.tts_overlay_permission_needed, Toast.LENGTH_LONG).show()
            }
        },
        onDismiss = { onExit(false, rememberChoice) }
    ) { ink ->
        Column {
            Text(stringResource(if (floatingSubtitlesEnabled) R.string.tts_exit_message
                else R.string.tts_exit_message_without_subtitles), color = ink)
            ReaderRememberChoice(rememberChoice, ink, paperColor) { rememberChoice = it }
        }
    }
}
