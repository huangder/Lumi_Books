package com.huangder.lumibooks.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.huangder.lumibooks.MainActivity

class QuoteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quote = WidgetDatabaseProvider.getRandomQuote(context)

        provideContent {
            GlanceTheme {
                QuoteCard(context = context, quote = quote)
            }
        }
    }

    @Composable
    private fun QuoteCard(context: Context, quote: com.huangder.lumibooks.data.local.dao.RandomNoteWithBook?) {
        val size = LocalSize.current
        val isWide = size.width > 220.dp

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(intent))
                .background(ColorProvider(Color.WHITE))
                .cornerRadius(12.dp)
                .padding(all = if (isWide) 16.dp else 12.dp),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            // 顶部装饰线 + 标题（仅4×2显示）
            if (isWide) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    // 装饰色块
                    Box(
                        modifier = GlanceModifier
                            .width(4.dp)
                            .height(18.dp)
                            .cornerRadius(2.dp)
                            .background(ColorProvider(Color.parseColor("#007AFF")))
                    ) { /* empty content, purely decorative */ }
                    Text(
                        text = "阅读摘录",
                        style = TextStyle(
                            color = ColorProvider(Color.parseColor("#8E8E93")),
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = GlanceModifier.padding(start = 8.dp)
                    )
                }
                Spacer(modifier = GlanceModifier.height(12.dp))
            }

            if (quote == null) {
                // 空状态
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "还没有摘录，\n去读书划线吧~",
                        style = TextStyle(
                            color = ColorProvider(Color.parseColor("#8E8E93")),
                            textAlign = TextAlign.Center
                        )
                    )
                }
            } else {
                // 引号装饰
                Text(
                    text = "“",
                    style = TextStyle(
                        color = ColorProvider(Color.parseColor("#007AFF")),
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(bottom = 2.dp)
                )

                // 摘录正文
                Text(
                    text = quote.selectedText,
                    style = TextStyle(
                        color = ColorProvider(Color.parseColor("#1C1C1E")),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = if (isWide) 5 else 3
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                // 出处
                val attribution = buildString {
                    append("——《${quote.bookTitle}》")
                    if (quote.bookAuthor.isNotBlank() && quote.bookAuthor != "unknown author") {
                        append(" ${quote.bookAuthor}")
                    }
                }
                Text(
                    text = attribution,
                    style = TextStyle(
                        color = ColorProvider(Color.parseColor("#8E8E93")),
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
