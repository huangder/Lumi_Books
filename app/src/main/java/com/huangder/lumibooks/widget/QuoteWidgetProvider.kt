package com.huangder.lumibooks.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.bookshelf.BookNotesActivity
import com.huangder.lumibooks.util.LocaleHelper
import kotlin.math.roundToInt

class QuoteWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            updateAsync(context, widgetIds(context, QuoteWidgetProvider::class.java))
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateAsync(context, intArrayOf(appWidgetId))
    }

    private fun updateAsync(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        runWidgetUpdate(TAG) {
            val appContext = context.applicationContext
            val localizedContext = LocaleHelper.applyLanguage(appContext)
            val entryPoint = appContext.widgetDataEntryPoint()
            val manager = AppWidgetManager.getInstance(appContext)
            val density = appContext.resources.displayMetrics.density
            val scaledDensity = appContext.resources.displayMetrics.scaledDensity
            val typeface = ResourcesCompat.getFont(
                appContext,
                R.font.gen_ryu_min2_light
            ) ?: Typeface.SERIF

            appWidgetIds.forEach { appWidgetId ->
                val options = manager.getAppWidgetOptions(appWidgetId)
                val widthDp = options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    DEFAULT_SQUARE_WIDTH_DP
                )
                val heightDp = options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    DEFAULT_HEIGHT_DP
                )
                val isWide = widthDp >= WIDE_LAYOUT_THRESHOLD_DP
                val layout = if (isWide) {
                    R.layout.widget_quote_wide
                } else {
                    R.layout.widget_quote_square
                }
                val views = RemoteViews(localizedContext.packageName, layout)
                val quote = entryPoint.noteDao().getRandomWidgetQuote()
                if (quote == null) {
                    views.setViewVisibility(R.id.widget_quote_content, View.GONE)
                    views.setViewVisibility(R.id.widget_quote_empty, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_quote_empty,
                        localizedContext.getString(R.string.widget_no_quotes)
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_quote_root,
                        openBookshelfPendingIntent(appContext, EMPTY_REQUEST_CODE_BASE + appWidgetId)
                    )
                } else {
                    views.setViewVisibility(R.id.widget_quote_content, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_quote_empty, View.GONE)
                    val horizontalPaddingDp = if (isWide) 44 else 36
                    val reservedVerticalSpaceDp = if (isWide) 68 else 70
                    val renderedQuote = QuoteExcerptFormatter.render(
                        text = quote.selectedText,
                        typeface = typeface,
                        textSizePx = QUOTE_TEXT_SIZE_SP * scaledDensity,
                        textColor = ContextCompat.getColor(
                            localizedContext,
                            R.color.widget_text_primary
                        ),
                        widthPx = ((widthDp - horizontalPaddingDp).coerceAtLeast(32) * density)
                            .roundToInt(),
                        heightPx = ((heightDp - reservedVerticalSpaceDp).coerceAtLeast(24) * density)
                            .roundToInt(),
                        maxLines = if (isWide) WIDE_MAX_LINES else SQUARE_MAX_LINES,
                        lineSpacingMultiplier = QUOTE_LINE_SPACING_MULTIPLIER
                    )
                    views.setViewVisibility(R.id.widget_quote_text, View.GONE)
                    views.setViewVisibility(R.id.widget_quote_bitmap, View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_quote_bitmap, renderedQuote.bitmap)
                    views.setContentDescription(R.id.widget_quote_bitmap, renderedQuote.text)
                    views.setTextViewText(
                        R.id.widget_quote_attribution,
                        localizedContext.getString(
                            R.string.widget_quote_attribution,
                            quote.bookTitle,
                            quote.bookAuthor
                        )
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_quote_root,
                        openQuotePendingIntent(
                            context = appContext,
                            appWidgetId = appWidgetId,
                            bookId = quote.bookId,
                            noteId = quote.noteId,
                            initialTab = if (quote.isNote) {
                                BookNotesActivity.TAB_NOTES
                            } else {
                                BookNotesActivity.TAB_HIGHLIGHTS
                            }
                        )
                    )
                }
                manager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    companion object {
        private const val TAG = "QuoteWidget"
        private const val ACTION_REFRESH =
            "com.huangder.lumibooks.widget.action.REFRESH_QUOTE"
        private const val EMPTY_REQUEST_CODE_BASE = 40_000
        private const val DEFAULT_SQUARE_WIDTH_DP = 150
        private const val DEFAULT_HEIGHT_DP = 150
        private const val WIDE_LAYOUT_THRESHOLD_DP = 220
        private const val SQUARE_MAX_LINES = 5
        private const val WIDE_MAX_LINES = 4
        private const val QUOTE_TEXT_SIZE_SP = 18f
        private const val QUOTE_LINE_SPACING_MULTIPLIER = 1.24f

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, QuoteWidgetProvider::class.java)
            if (manager.getAppWidgetIds(component).isEmpty()) return
            context.sendBroadcast(
                Intent(ACTION_REFRESH).setComponent(component)
            )
        }
    }
}
