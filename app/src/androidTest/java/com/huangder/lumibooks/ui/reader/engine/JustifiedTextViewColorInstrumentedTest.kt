package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.URLSpan
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 覆盖阅读器排版横排可见文字层的两个历史缺陷：
 * - 改文字颜色只改 paint 不 invalidate，硬件渲染复用旧显示列表 → 需退出重进才更新。
 * - URLSpan 按 `linkColor` 绘制，而 linkColor 默认 0（透明）→ 链接文字可见性缺失。
 */
@RunWith(AndroidJUnit4::class)
class JustifiedTextViewColorInstrumentedTest {

    @Test
    fun urlSpanTextIsPaintedWithTheReaderTextColor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val textColor = 0xFF4A3728.toInt()
            val value = SpannableString("〔1〕").apply {
                setSpan(URLSpan("https://example.com"), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val view = JustifiedTextView(context).apply {
                setTextSize(24f * density)
                setDefaultTextColor(textColor)
                setLineSpacing(0f, 1.4f)
                setPadding(0, 0, 0, 0)
                text = value
            }
            val width = (240f * density).roundToInt()
            val height = (120f * density).roundToInt()
            val bitmap = renderToBitmap(view, width, height)

            var visiblePixels = 0
            var textColorPixels = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) == 0) continue
                    visiblePixels++
                    if (pixel == textColor) textColorPixels++
                }
            }
            assertTrue(
                "链接文字必须被绘制出来（linkColor 未同步时会整段透明）",
                visiblePixels > 0
            )
            assertTrue(
                "链接文字必须使用阅读正文颜色绘制",
                textColorPixels > 0
            )
        }
    }

    @Test
    fun bodyGlyphsFollowTheNewTextColorAfterColorOnlyChange() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val firstColor = 0xFF4A3728.toInt()
            val secondColor = 0xFFF4F4F5.toInt()
            val width = (240f * density).roundToInt()
            val height = (120f * density).roundToInt()
            val view = JustifiedTextView(context).apply {
                setTextSize(20f * density)
                setDefaultTextColor(firstColor)
                setLineSpacing(0f, 1.4f)
                setPadding(0, 0, 0, 0)
                text = SpannableString("正文颜色")
            }

            val before = renderToBitmap(view, width, height)
            assertTrue("首次绘制应使用初始颜色", countPixels(before, firstColor) > 0)

            // 仅改颜色（字号/行距/内边距都不变），模拟弹窗里调整文字颜色。
            view.setDefaultTextColor(secondColor)
            val after = renderToBitmap(view, width, height)

            assertTrue("颜色变化后必须按新颜色绘制", countPixels(after, secondColor) > 0)
            assertEquals("旧颜色不应残留", 0, countPixels(after, firstColor))
        }
    }

    @Test
    fun textColorChangeRequestsRedrawExactlyOncePerChange() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val view = JustifiedTextView(context).apply {
                setTextSize(20f)
                setPadding(0, 0, 0, 0)
                text = SpannableString("正文颜色")
            }
            val baseline = view.redrawRequestCount

            view.setDefaultTextColor(0xFF4A3728.toInt())
            assertEquals(baseline + 1, view.redrawRequestCount)

            view.setDefaultTextColor(0xFF4A3728.toInt())
            assertEquals(
                "同色重复设置不应触发多余重绘",
                baseline + 1,
                view.redrawRequestCount
            )

            view.setDefaultTextColor(0xFFF4F4F5.toInt())
            assertEquals(baseline + 2, view.redrawRequestCount)
        }
    }

    private fun renderToBitmap(view: JustifiedTextView, width: Int, height: Int): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun countPixels(bitmap: Bitmap, color: Int): Int {
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == color) count++
            }
        }
        return count
    }
}
