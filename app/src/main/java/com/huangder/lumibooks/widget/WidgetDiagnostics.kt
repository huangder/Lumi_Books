package com.huangder.lumibooks.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry

/** Only geometry, state and counts: never book text, titles, locators or full intent URIs. */
object WidgetDiagnostics {
    fun log(event: String, attributes: Map<String, Any?> = emptyMap(),
        bookId: String? = null, throwable: Throwable? = null, operationId: String? = null) {
        // Diagnostics must not change widget or navigation behavior.
        runCatching {
            DiagnosticLoggerRegistry.logger?.log("widget", event,
                level = if (throwable == null) DiagnosticLevel.INFO else DiagnosticLevel.ERROR,
                attributes = attributes, bookId = bookId, throwable = throwable,
                operationId = operationId)
        }
    }

    fun host(context: Context, id: Int, options: Bundle, reason: String) {
        runCatching {
            val metrics = context.resources.displayMetrics
            val config = context.resources.configuration
            log("host_options", mapOf(
                "widgetId" to id, "reason" to reason,
                "minWidthDp" to options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1),
                "maxWidthDp" to options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, -1),
                "minHeightDp" to options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, -1),
                "maxHeightDp" to options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, -1),
                "hostCategory" to options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1),
                "density" to metrics.density, "densityDpi" to metrics.densityDpi,
                "scaledDensity" to metrics.scaledDensity, "fontScale" to config.fontScale,
                "screenWidthPx" to metrics.widthPixels, "screenHeightPx" to metrics.heightPixels,
                "screenWidthDp" to config.screenWidthDp, "screenHeightDp" to config.screenHeightDp,
                "orientation" to config.orientation, "api" to Build.VERSION.SDK_INT))
            if (Build.VERSION.SDK_INT >= 31) {
                @Suppress("DEPRECATION")
                val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
                log("host_responsive_sizes", mapOf("widgetId" to id, "count" to sizes.size))
                sizes.take(32).forEachIndexed { index, size ->
                    log("host_responsive_size", mapOf("widgetId" to id, "index" to index,
                        "widthDp" to size.width, "heightDp" to size.height))
                }
            }
        }
    }

    fun openIntent(intent: Intent?, stage: String) {
        runCatching {
            val uri = intent?.data ?: return
            if (uri.scheme != "lumibooks" || uri.host != "widget") return
            val kind = uri.pathSegments.firstOrNull().let {
                when (it) { "desktop-reading", "read", "bookshelf", "notes" -> it; else -> "unknown" }
            }
            log("open_intent", mapOf("stage" to stage, "kind" to kind,
                "widgetId" to intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
                "flags" to intent.flags,
                "hasBook" to intent.hasExtra(MainActivity.EXTRA_OPEN_BOOK_ID),
                "reloadPosition" to intent.getBooleanExtra(MainActivity.EXTRA_RELOAD_READING_POSITION, false)),
                bookId = intent.getStringExtra(MainActivity.EXTRA_OPEN_BOOK_ID))
        }
    }
}
