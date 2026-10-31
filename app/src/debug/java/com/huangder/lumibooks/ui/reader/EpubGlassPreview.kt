package com.huangder.lumibooks.ui.reader

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.theme.*

/** Optional glass mode in the existing EPUB fixture; no writes to the user's library/settings. */
@Composable
internal fun EpubGlassPreview(
    chapter: Int, page: Int, ready: Boolean,
    reader: @Composable (((() -> GlassBitmapBackdrop?)?) -> Unit, () -> Unit) -> Unit
) {
    var provider by remember { mutableStateOf<(() -> GlassBitmapBackdrop?)?>(null) }
    var dialog by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var controls by remember { mutableStateOf(true) }
    val view = LocalView.current
    val snapshot = rememberGlassSnapshot(ready, listOf(chapter, page, provider), dialog || note) {
        provider?.invoke() ?: if (!dialog && !note) captureWindowGlassBackdrop(view) else null
    }
    LaunchedEffect(snapshot.backdrop) {
        Log.d("EpubGlassPreview", "page=$chapter/$page snapshot=${snapshot.backdrop != null}")
    }
    EBookReaderTheme(appTheme = "liquid_glass") {
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides snapshot.backdrop) {
            LiquidGlassDialogHost(Modifier.fillMaxSize(), backdrop = snapshot.backdrop) {
                Box(Modifier.fillMaxSize()) {
                    reader({ provider = it }, { controls = !controls })
                    if (controls && snapshot.ready) {
                        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LiquidGlassTextButton("Dialog", onClick = { dialog = true })
                            LiquidGlassTextButton("Note", onClick = { note = true })
                        }
                    }
                    if (dialog) LiquidGlassAlertDialog(
                        onDismissRequest = { dialog = false },
                        title = { Text("EPUB glass", color = AppColors.TextPrimary) },
                        text = { Text("Independent page pixels · $chapter/$page", color = AppColors.TextPrimary) },
                        confirmButton = { LiquidGlassTextButton("Close", onClick = { dialog = false }) }
                    )
                    NoteInputSheet(visible = note, glassBackdrop = snapshot.backdrop,
                        keepReaderViewport = true, initialText = text, onTextChange = { text = it },
                        onConfirm = { note = false }, onDismiss = { note = false })
                }
            }
        }
    }
}
