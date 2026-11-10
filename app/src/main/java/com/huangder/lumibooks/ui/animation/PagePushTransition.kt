package com.huangder.lumibooks.ui.animation

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.huangder.lumibooks.util.LaunchThemeController
import java.util.WeakHashMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal enum class PagePushSpeed { STANDARD, FAST, NONE }
internal data class PagePushSpec(val speed: PagePushSpeed, val animateOpen: Boolean)

/** Per-launch policy keeps cold starts, task roots and process restoration still. */
internal fun pagePushSpec(
    page: Boolean,
    internalLaunch: Boolean,
    taskRoot: Boolean,
    restored: Boolean,
    motion: MotionPreference,
    eInk: Boolean
): PagePushSpec? {
    if (!page || !internalLaunch || taskRoot) return null
    val speed = when {
        eInk || !motion.motionEnabled -> PagePushSpeed.NONE
        motion == MotionPreference.FAST -> PagePushSpeed.FAST
        else -> PagePushSpeed.STANDARD
    }
    return PagePushSpec(speed, animateOpen = !restored)
}

/** One shared progress drives the entering page, the covered page and both toolbar overlays. */
@androidx.compose.runtime.Stable
internal class PageScene(val activity: Activity, val page: Boolean) {
    var corners: PageCorners? = null
    var progress by androidx.compose.runtime.mutableFloatStateOf(1f)
    var moving by androidx.compose.runtime.mutableStateOf(false)
    var toolbarReady by androidx.compose.runtime.mutableStateOf(false)
    var started by androidx.compose.runtime.mutableStateOf(false)
    var coveredBy by androidx.compose.runtime.mutableStateOf<PageScene?>(null)
    var opener: java.lang.ref.WeakReference<PageScene>? = null
    var close: (() -> Unit)? = null
    var gestureStart: (() -> Unit)? = null
    var gestureProgress: ((Float) -> Unit)? = null
    var gestureCancel: (() -> Unit)? = null
    var predictiveEnabled = true
    var pageCoordinates: androidx.compose.ui.layout.LayoutCoordinates? = null
    val controls = androidx.compose.runtime.mutableStateMapOf<Any, ToolbarLayer>()
    var speed = PagePushSpeed.STANDARD
}

object PagePushTransitions : Application.ActivityLifecycleCallbacks {
    private val scenes = WeakHashMap<Activity, PageScene>()
    private var foreground: java.lang.ref.WeakReference<Activity>? = null
    private val speeds = WeakHashMap<Activity, PagePushSpeed>()

    internal fun scene(activity: Activity) = scenes[activity]

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is androidx.activity.ComponentActivity) return
        val attrs = activity.theme.obtainStyledAttributes(intArrayOf(R.attr.lumiPagePush))
        val page = try { attrs.getBoolean(0, false) } finally { attrs.recycle() }
        val windowAttrs = activity.theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
        val translucent = try { windowAttrs.getBoolean(0, false) } finally { windowAttrs.recycle() }
        // Overlay activities such as the listening player own their transitions.
        if (translucent && !page) return
        val state = PageScene(activity, page)
        scenes[activity] = state
        val parent = foreground?.get()?.takeIf {
            it !== activity && !it.isFinishing && it.taskId == activity.taskId
        }?.let(scenes::get)
        val snapshot = LaunchThemeController.themeSnapshot(activity)
        state.predictiveEnabled = snapshot.predictiveBackEnabled
        val spec = pagePushSpec(page,
            parent != null && activity.intent.action != AppWidgetManager.ACTION_APPWIDGET_CONFIGURE &&
                activity.intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0,
            activity.isTaskRoot, savedInstanceState != null,
            MotionPreference.fromStoredValue(snapshot.motionPreference), snapshot.eInkModeEnabled)
        if (spec == null) {
            if (page && Build.VERSION.SDK_INT >= 30) suppress(activity)
            return
        }
        speeds[activity] = spec.speed
        state.speed = spec.speed
        if (Build.VERSION.SDK_INT >= 30) {
            suppress(activity)
            if (spec.animateOpen && spec.speed != PagePushSpeed.NONE) {
                state.progress = 0f
                state.moving = true
                state.opener = java.lang.ref.WeakReference(parent)
                parent?.coveredBy = state
            }
            // Registered before screen handlers: dialogs/search get priority over page back.
            activity.onBackPressedDispatcher.addCallback(activity,
                object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() = finish(activity)
                    override fun handleOnBackStarted(backEvent: androidx.activity.BackEventCompat) {
                        if (state.predictiveEnabled) state.gestureStart?.invoke()
                    }
                    override fun handleOnBackProgressed(backEvent: androidx.activity.BackEventCompat) {
                        if (state.predictiveEnabled) state.gestureProgress?.invoke(backEvent.progress)
                    }
                    override fun handleOnBackCancelled() { state.gestureCancel?.invoke() }
                })
        } else {
            activity.window.setWindowAnimations(style(spec.speed))
            val (enter, exit) = if (spec.animateOpen) openAnimations(spec.speed) else 0 to 0
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(enter, exit)
        }
    }

    override fun onActivityStarted(activity: Activity) { scenes[activity]?.started = true }
    override fun onActivityStopped(activity: Activity) { scenes[activity]?.started = false }
    override fun onActivityResumed(activity: Activity) {
        if (scenes.containsKey(activity)) foreground = java.lang.ref.WeakReference(activity)
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        val state = scenes.remove(activity)
        state?.opener?.get()?.let { if (it.coveredBy === state) it.coveredBy = null }
        speeds.remove(activity)
        if (foreground?.get() === activity) foreground = null
    }

    internal fun updateMotion(activity: Activity, motion: MotionPreference, eInk: Boolean) {
        val speed = pagePushSpec(true, true, false, false, motion, eInk)!!.speed
        scenes[activity]?.speed = speed
        if (speeds.containsKey(activity)) {
            speeds[activity] = speed
            if (Build.VERSION.SDK_INT < 30) activity.window.setWindowAnimations(style(speed))
        }
    }

    internal fun finish(activity: Activity) {
        val state = scenes[activity]
        if (Build.VERSION.SDK_INT >= 30 && state?.close != null) {
            state.close?.invoke()
        } else {
            activity.finish()
            if (Build.VERSION.SDK_INT < 30) speeds[activity]?.let { speed ->
                val (enter, exit) = closeAnimations(speed)
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(enter, exit)
            }
        }
    }

    internal fun suppress(activity: Activity) {
        activity.window.setWindowAnimations(R.style.Animation_Lumi_Push_None)
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }

    private fun style(speed: PagePushSpeed) = when (speed) {
        PagePushSpeed.STANDARD -> R.style.Animation_Lumi_Push
        PagePushSpeed.FAST -> R.style.Animation_Lumi_Push_Fast
        PagePushSpeed.NONE -> R.style.Animation_Lumi_Push_None
    }
    private fun openAnimations(speed: PagePushSpeed): Pair<Int, Int> = when (speed) {
        PagePushSpeed.STANDARD -> R.anim.lumi_push_open_enter to R.anim.lumi_push_open_exit
        PagePushSpeed.FAST -> R.anim.lumi_push_open_enter_fast to R.anim.lumi_push_open_exit_fast
        PagePushSpeed.NONE -> 0 to 0
    }
    private fun closeAnimations(speed: PagePushSpeed): Pair<Int, Int> = when (speed) {
        PagePushSpeed.STANDARD -> R.anim.lumi_push_close_enter to R.anim.lumi_push_close_exit
        PagePushSpeed.FAST -> R.anim.lumi_push_close_enter_fast to R.anim.lumi_push_close_exit_fast
        PagePushSpeed.NONE -> 0 to 0
    }
}

fun Activity.finishWithPush() = PagePushTransitions.finish(this)
