package com.huangder.lumibooks.ui.reader

import android.view.WindowManager
import android.app.Activity
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
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
    val readerView = LocalView.current
    DisposableEffect(readerView) {
        var context = readerView.context
        while (context is ContextWrapper && context !is Activity && context.baseContext !== context) {
            context = context.baseContext
        }
        val lease = (context as? Activity)?.window?.let(ReaderImeWindowLease::acquire)
        onDispose { lease?.close() }
    }
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
        com.huangder.lumibooks.ui.components.LiquidGlassDialogHost(
            modifier = Modifier.fillMaxSize(),
            backdrop = com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop.current,
            content = content
        )
    }
}

/** Android can dispatch IME resize to the covered Activity as well as the dialog. */
internal object ReaderImeWindowLease {
    private class Entry(val originalMode: Int, var users: Int = 0, var restore: Runnable? = null)
    private val windows = java.util.WeakHashMap<Window, Entry>()
    private val handler = Handler(Looper.getMainLooper())

    fun acquire(window: Window): AutoCloseable {
        val entry = windows.getOrPut(window) { Entry(window.attributes.softInputMode) }
        entry.restore?.let(handler::removeCallbacks)
        entry.restore = null
        entry.users++
        window.setSoftInputMode(readerWindowImeMode(entry.originalMode))
        var closed = false
        return AutoCloseable {
            if (!closed) {
                closed = true
                if (--entry.users == 0) {
                    // The note hides its keyboard on close. Let that animation finish before
                    // restoring resize, and cancel this restoration if another note opens.
                    val restore = Runnable {
                        if (entry.users == 0 && windows[window] === entry) {
                            window.setSoftInputMode(entry.originalMode)
                            windows.remove(window)
                        }
                    }
                    entry.restore = restore
                    handler.postDelayed(restore, 350L)
                }
            }
        }
    }
}

internal fun readerWindowImeMode(original: Int): Int =
    (original and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
