package com.huangder.lumibooks.util.diagnostics

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.LauncherComponentNames
import com.huangder.lumibooks.util.launcherComponentStates
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Local-only observations. Never use this state to make a routing decision. */
internal class StartupTraceRecorder(
    private val enabled: Boolean,
    private val sink: (String, Map<String, Any?>) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val processId: String = UUID.randomUUID().toString()
) {
    private val sequence = AtomicLong()
    fun record(event: String, fields: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        runCatching {
            sink(event, fields + mapOf("processId" to processId, "sequence" to sequence.incrementAndGet(), "elapsedMs" to clock()))
        }
    }
}

internal object StartupTrace {
    private val recorder = StartupTraceRecorder(BuildConfig.STARTUP_TRACE_ENABLED, { event, fields ->
        DiagnosticLoggerRegistry.logger?.log("startup", event, attributes = fields)
    })

    fun event(name: String, fields: Map<String, Any?> = emptyMap()) = recorder.record(name, fields)

    fun activity(activity: Activity, event: String, restored: Boolean? = null) {
        if (!BuildConfig.STARTUP_TRACE_ENABLED) return
        runCatching {
            event(event, intentFields(activity.intent) + mapOf(
                "activity" to activity.javaClass.simpleName,
                "instance" to System.identityHashCode(activity), "taskId" to activity.taskId,
                "taskRoot" to activity.isTaskRoot, "restored" to restored,
                "finishing" to activity.isFinishing,
                "themeResource" to runCatching { activity.packageManager.getActivityInfo(activity.componentName, 0).themeResource }.getOrNull(),
                "windowBackground" to android.util.TypedValue().let {
                    activity.theme.resolveAttribute(android.R.attr.windowBackground, it, true)
                    if (it.resourceId != 0) activity.resources.getResourceName(it.resourceId) else it.data.toString()
                },
                "windowDisablePreview" to android.util.TypedValue().let {
                    activity.theme.resolveAttribute(android.R.attr.windowDisablePreview, it, true) && it.data != 0
                }
            ))
        }
    }

    fun observeFirstDraw(activity: Activity) {
        if (!BuildConfig.STARTUP_TRACE_ENABLED) return
        runCatching {
            val view = activity.window.decorView
            var fired = false
            val listener = object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    if (fired) return
                    fired = true
                    activity(activity, "activity_first_draw_callback")
                    view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this) }
                }
            }
            view.viewTreeObserver.addOnDrawListener(listener)
        }
    }

    // Explicit allowlist: never stringify Intent, extras, data, ClipData or book identifiers.
    fun intentFields(intent: Intent?): Map<String, Any?> = runCatching { mapOf(
        "component" to intent?.component?.className?.takeIf { it.startsWith("com.huangder.lumibooks.") },
        "action" to intent?.action?.takeIf { it in setOf(Intent.ACTION_MAIN, Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE) },
        "launcherCategory" to intent?.hasCategory(Intent.CATEGORY_LAUNCHER),
        "flags" to intent?.flags,
        "splashExtraPresent" to intent?.hasExtra(LaunchThemeController.EXTRA_SPLASH_ENABLED),
        "splashExtra" to intent?.getBooleanExtra(LaunchThemeController.EXTRA_SPLASH_ENABLED, false)
    ) }.getOrElse { mapOf("intentReadFailed" to true) }

    fun aliases(context: Context, stage: String, result: Boolean? = null) {
        if (!BuildConfig.STARTUP_TRACE_ENABLED) return
        runCatching {
            val fields = launcherComponentStates(null, true).keys.associate { name ->
                val state = context.packageManager.getComponentEnabledSetting(ComponentName(context, name))
                name.substringAfterLast('.') to "$state/effective=${if (state == 0) name == LauncherComponentNames.LUMI_2_SPLASH else state == 1}"
            }
            event("launcher_components", fields + mapOf("stage" to stage, "result" to result,
                "splashSnapshot" to LaunchThemeController.splashEnabledSnapshot(context),
                "iconSnapshot" to LaunchThemeController.iconStyleSnapshot(context)))
        }
    }
}

/** A draw callback is evidence of app drawing, not proof the compositor presented that frame. */
@Composable
internal fun startupTraceModifier(page: String): Modifier {
    if (!BuildConfig.STARTUP_TRACE_ENABLED) return Modifier
    val drawn = remember(page) { AtomicBoolean() }
    DisposableEffect(page) {
        StartupTrace.event("composition_enter", mapOf("page" to page))
        onDispose { StartupTrace.event("composition_exit", mapOf("page" to page)) }
    }
    return Modifier.drawWithContent {
        drawContent()
        if (drawn.compareAndSet(false, true)) StartupTrace.event("first_draw_callback", mapOf("page" to page))
    }
}

internal fun startupSummary(events: List<DiagnosticEvent>): String = buildString {
    appendLine("# 启动闪屏诊断")
    appendLine("仅包含保留窗口内的记录，缺失事件不等于没有显示。draw_callback 表示应用执行绘制回调，不保证系统已呈现该帧。")
    appendLine("系统启动窗口/任务快照是否显示无法由应用日志证明；需结合用户录屏确认。")
    events.filter { it.category == "startup" }.groupBy { it.attributes["processId"] ?: "unknown" }.forEach { (process, records) ->
        appendLine("\n## 进程启动 $process")
        records.sortedBy { it.attributes["sequence"]?.toLongOrNull() ?: Long.MAX_VALUE }.forEach {
            appendLine("- #${it.attributes["sequence"]} @${it.attributes["elapsedMs"]}ms ${it.event}: " +
                it.attributes.filterKeys { key -> key !in setOf("processId", "sequence", "elapsedMs") })
        }
    }
}

/** Deliberately independent of manual capture sessions and their issue-category filters. */
internal fun selectStartupEvents(events: List<DiagnosticEvent>, now: Long, limit: Int): List<DiagnosticEvent> =
    events.filter { it.timestamp in (now - 3_600_000L)..now && it.category == "startup" }
        .sortedWith(compareBy<DiagnosticEvent> { it.timestamp }.thenBy { it.attributes["sequence"]?.toLongOrNull() ?: 0L })
        .takeLast(limit)
