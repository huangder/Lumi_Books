package com.huangder.lumibooks.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 每 15 分钟刷新摘录组件，随机轮换摘录内容
 */
class WidgetUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(QuoteWidget::class.java)
            ids.forEach { id ->
                QuoteWidget().update(context, id)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "quote_widget_update"
        private const val INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
