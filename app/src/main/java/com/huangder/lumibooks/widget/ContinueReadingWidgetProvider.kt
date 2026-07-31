package com.huangder.lumibooks.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.LocaleHelper
import kotlin.math.roundToInt

class ContinueReadingWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            updateAsync(context, widgetIds(context, ContinueReadingWidgetProvider::class.java))
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAsync(context, appWidgetIds)
    }

    private fun updateAsync(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        runWidgetUpdate(TAG) {
            val appContext = context.applicationContext
            val localizedContext = LocaleHelper.applyLanguage(appContext)
            val data = appContext.widgetDataEntryPoint().bookDao()
                .getContinueReadingWidgetData()
            val manager = AppWidgetManager.getInstance(appContext)
            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(
                    localizedContext.packageName,
                    R.layout.widget_continue_reading
                )
                if (data == null) {
                    views.setViewVisibility(R.id.widget_continue_content, View.GONE)
                    views.setViewVisibility(R.id.widget_continue_empty, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_continue_empty,
                        localizedContext.getString(R.string.widget_no_recent_book)
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_continue_root,
                        openBookshelfPendingIntent(appContext, EMPTY_REQUEST_CODE_BASE + appWidgetId)
                    )
                } else {
                    views.setViewVisibility(R.id.widget_continue_content, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_continue_empty, View.GONE)
                    views.setTextViewText(R.id.widget_continue_title, data.title)
                    views.setTextViewText(R.id.widget_continue_author, data.author)
                    views.setTextViewText(
                        R.id.widget_continue_progress,
                        localizedContext.getString(
                            R.string.widget_reading_progress_percent,
                            (data.readingProgress.coerceIn(0f, 1f) * 100f).roundToInt()
                        )
                    )
                    views.setTextViewText(
                        R.id.widget_continue_button,
                        localizedContext.getString(R.string.continue_reading)
                    )
                    views.setImageViewResource(
                        R.id.widget_continue_cover,
                        R.drawable.ic_widget_book
                    )
                    val cover = decodeWidgetCover(
                        data.coverPath,
                        (96 * appContext.resources.displayMetrics.density).roundToInt()
                    )
                    if (cover != null) {
                        views.setViewPadding(R.id.widget_continue_cover, 0, 0, 0, 0)
                        views.setImageViewBitmap(R.id.widget_continue_cover, cover)
                    }
                    views.setOnClickPendingIntent(
                        R.id.widget_continue_root,
                        openBookPendingIntent(appContext, appWidgetId, data.bookId)
                    )
                }
                manager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    companion object {
        private const val TAG = "ContinueReadingWidget"
        private const val ACTION_REFRESH =
            "com.huangder.lumibooks.widget.action.REFRESH_CONTINUE_READING"
        private const val EMPTY_REQUEST_CODE_BASE = 30_000

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ContinueReadingWidgetProvider::class.java)
            if (manager.getAppWidgetIds(component).isEmpty()) return
            context.sendBroadcast(
                Intent(ACTION_REFRESH).setComponent(component)
            )
        }
    }
}
