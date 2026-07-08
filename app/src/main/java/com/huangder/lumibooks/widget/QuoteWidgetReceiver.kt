package com.huangder.lumibooks.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class QuoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: QuoteWidget = QuoteWidget()
}
