package com.huangder.lumibooks.ui.reader

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View

/** Wait for the window to submit a frame without the detached WebView render nodes. */
internal fun afterEpubDetachedFrame(window: View, completion: () -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    if (Build.VERSION.SDK_INT < 29 || !window.isAttachedToWindow || !window.isHardwareAccelerated) {
        handler.post(completion)
        return
    }
    val observer = window.viewTreeObserver
    var completed = false
    lateinit var finish: Runnable
    val committed = Runnable { handler.post(finish) }
    val detached = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) { handler.post(finish) }
    }
    // A background window does not submit frames. Its render tree is no longer
    // being drawn, so it need not hold a released reader until the next resume.
    val visibilityCheck = object : Runnable {
        override fun run() {
            if (completed) return
            if (!window.isAttachedToWindow || window.windowVisibility != View.VISIBLE) finish.run()
            else { window.invalidate(); handler.postDelayed(this, 250L) }
        }
    }
    finish = Runnable {
        if (!completed) {
            completed = true
            if (observer.isAlive) observer.unregisterFrameCommitCallback(committed)
            window.removeOnAttachStateChangeListener(detached)
            handler.removeCallbacks(visibilityCheck)
            completion()
        }
    }
    observer.registerFrameCommitCallback(committed)
    window.addOnAttachStateChangeListener(detached)
    window.invalidate()
    handler.postDelayed(visibilityCheck, 250L)
}
