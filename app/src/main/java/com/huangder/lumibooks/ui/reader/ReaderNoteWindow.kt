package com.huangder.lumibooks.ui.reader

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The IME belongs to this window, so opening a note never resizes the EPUB window. */
@Composable
internal fun ReaderNoteWindow(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        val view = LocalView.current
        val window = (view.parent as DialogWindowProvider).window
        SideEffect {
            // The sheet draws its own scrim and animation. Only its window may react to IME.
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(0)
            window.setSoftInputMode(
                (window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            WindowCompat.getInsetsController(window, view).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        content()
    }
}
