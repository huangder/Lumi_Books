package com.huangder.lumibooks.ui.privacy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * Keeps hidden pages out of the recents thumbnail. API 33+ only blanks the thumbnail, so
 * screenshots keep working; older versions can only use FLAG_SECURE. Requests are counted per
 * activity because the hidden library and a hidden book's reader overlap during navigation.
 */
@Composable
internal fun SecureWindowEffect(enabled: Boolean = true) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        SecureWindowRegistry.acquire(activity)
        onDispose { SecureWindowRegistry.release(activity) }
    }
}

private object SecureWindowRegistry {
    private val counts = WeakHashMap<Activity, Int>()

    fun acquire(activity: Activity) {
        val count = counts[activity] ?: 0
        counts[activity] = count + 1
        if (count == 0) apply(activity, secure = true)
    }

    fun release(activity: Activity) {
        val remaining = (counts[activity] ?: return) - 1
        if (remaining > 0) {
            counts[activity] = remaining
        } else {
            counts.remove(activity)
            apply(activity, secure = false)
        }
    }

    private fun apply(activity: Activity, secure: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!secure)
        } else if (secure) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
