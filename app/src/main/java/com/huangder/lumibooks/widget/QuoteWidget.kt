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
import com.huangder.lumibooks.data.local.dao.RandomNoteWithBook

class QuoteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 安全加载数据：Room 多实例可能失败，异常时降级为空状态
        val quote = try {
            WidgetDatabaseProvider.getRandomQuote(context)
        } catch (e: Exception) {
            null
        }

        provideContent {
            GlanceTheme {
                QuoteCard(context = context, quote = quote)
            }
        }
    }

    @Composable
    private fun QuoteCard(context: Context, quote: RandomNoteWithBook?) {
        val size = LocalSize.current
        // dp 值来自 Compose UI unit，在 Glance 中可直接比较
        val isWide = size.width > 220.dp

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(openAppIntent(context)))
                .background(ColorProvider(Color.WHITE))
                .cornerRadius(12.dp)
                .padding(all = if (isWide) 16.dp else 12.dp),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            // 顶部标题栏（仅宽版 4×2 显示）
            if (isWide) {
                TitleBar()
                // 用 Box 代替 Spacer，Glance 兼容性更好
                Box(modifier = GlanceModifier.height(12.dp)) {}
            }

            if (quote == null) {
                EmptyState()
            } else {
                QuoteContent(quote = quote, isWide = isWide)
            }
        }
    }

    @Composable
    private fun TitleBar() {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // 蓝色装饰竖线
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .height(18.dp)
                    .cornerRadius(2.dp)
                    .background(ColorProvider(Color.parseColor("#007AFF")))
            ) { /* 纯装饰，无内容 */ }
            Text(
                text = "阅读摘录",
                style = TextStyle(
                    color = ColorProvider(Color.parseColor("#8E8E93")),
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.padding(start = 8.dp)
            )
        }
    }

    @Composable
    private fun EmptyState() {
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
    }

    @Composable
    private fun QuoteContent(quote: RandomNoteWithBook, isWide: Boolean) {
        // 左引号装饰
        Text(
            text = "“",  // Unicode 左双引号 "
            style = TextStyle(
                color = ColorProvider(Color.parseColor("#007AFF")),
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.padding(bottom = 2.dp)
        )

        // 摘录正文 — 纯文本展示，不带高亮底色
        Text(
            text = quote.selectedText,
            style = TextStyle(
                color = ColorProvider(Color.parseColor("#1C1C1E")),
                fontWeight = FontWeight.Medium
            ),
            maxLines = if (isWide) 5 else 3
        )

        Box(modifier = GlanceModifier.height(8.dp)) {}

        // 出处行
        Text(
            text = buildAttribution(quote),
            style = TextStyle(
                color = ColorProvider(Color.parseColor("#8E8E93")),
                fontWeight = FontWeight.Normal
            ),
            maxLines = 1
        )
    }

    private fun buildAttribution(quote: RandomNoteWithBook): String {
        val sb = StringBuilder("——《${quote.bookTitle}》")
        if (quote.bookAuthor.isNotBlank() && quote.bookAuthor != "unknown author") {
            sb.append(" ${quote.bookAuthor}")
        }
        return sb.toString()
    }

    private fun openAppIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}
