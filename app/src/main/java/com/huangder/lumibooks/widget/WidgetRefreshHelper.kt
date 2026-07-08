package com.huangder.lumibooks.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用内即时刷新（新建笔记后调用）
 */
object WidgetRefreshHelper {
    fun refreshQuoteWidget(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(QuoteWidget::class.java)
                ids.forEach { id ->
                    QuoteWidget().update(context, id)
                }
            } catch (_: Exception) {
                // 小组件刷新失败不影响主流程
            }
        }
    }
}
