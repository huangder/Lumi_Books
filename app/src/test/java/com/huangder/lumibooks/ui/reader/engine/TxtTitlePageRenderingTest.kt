package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.DynamicLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.applyReaderTextAlignment
import com.huangder.lumibooks.ui.reader.chapterTitleParagraphEnd
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TxtTitlePageRenderingTest {
    @Test
    fun replacingTextInReusedLayoutDoesNotReuseBodyCoordinatesForTitle() {
        val context = RuntimeEnvironment.getApplication()
        val content = SpannableStringBuilder(
            applyReaderPunctuationCompression("\u6b63\u6587\uff0c\u7ee7\u7eed\u9605\u8bfb\u3002".repeat(20))
        )
        val paint = TextPaint().apply { textSize = 48f; density = 3f }
        val layout = DynamicLayout.Builder.obtain(content, paint, 1080).setIncludePad(false).build()
        fun newView() = JustifiedTextView(context).apply {
            setTextSize(48f)
            setSourceLayoutProvider { layout }
            text = content
        }
        val reused = newView()
        render(reused).recycle()
        val title = "\u7b2c5\u7ae0 \u4e0e\u65f6\u95f4\u8d5b\u8dd1"
        content.replace(0, content.length, title + "\n\u6b63\u6587")
        content.setSpan(AbsoluteSizeSpan(22, true), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        reused.text = content
        val actual = render(reused)
        val expected = render(newView())
        assertTrue("A reused Layout retained the previous page's character coordinates", expected.sameAs(actual))
        actual.recycle()
        expected.recycle()
    }

    @Test
    fun titleRemainsIdenticalAfterPageRoundTrip() {
        val page = PageContentView(RuntimeEnvironment.getApplication()).apply {
            configure(fontSizePx = 48f, textColor = Color.BLACK)
        }
        val title = "\u7b2c5\u7ae0 \u4e0e\u65f6\u95f4\u8d5b\u8dd1"
        val chapter = SpannableStringBuilder(title + "\n" + "\u6b63\u6587\uff0c\u7ee7\u7eed\u9605\u8bfb\u3002".repeat(160)).apply {
            setSpan(AbsoluteSizeSpan(22, true), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val text = applyReaderPunctuationCompression(chapter)
        page.setPageContent(text, 0, 160)
        val before = render(page)
        page.setPageContent(text, 160, 320)
        render(page).recycle()
        page.setPageContent(text, 0, 160)
        val after = render(page)
        assertTrue("Returning to the title page changed its pixels", before.sameAs(after))
        before.recycle()
        after.recycle()
    }

    private fun render(view: View): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1080, 1920)
        return Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }
    }

    /**
     * 用户反馈的标题重叠与「对齐模式」相关：章首标题必须始终按起始边（左）对齐，
     * 不受居中 / 右对齐 / 两端对齐设置影响。这里渲染只有标题的页面，
     * 逐像素比较四种模式下与「左对齐」的结果。
     */
    @Test
    fun chapterTitleStaysLeftAlignedInEveryAlignmentMode() {
        val title = "\u7b2c5\u7ae0 \u4e0e\u65f6\u95f4\u8d5b\u8dd1"
        val source = SpannableStringBuilder("$title\n").apply {
            setSpan(AbsoluteSizeSpan(22, true), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val titleEnd = chapterTitleParagraphEnd(source, title)
        assertEquals(title.length + 1, titleEnd)

        val left = renderChapterTitlePage(source, ReaderTextAlignment.LEFT, titleEnd)
        listOf(
            ReaderTextAlignment.NATURAL,
            ReaderTextAlignment.CENTER,
            ReaderTextAlignment.RIGHT,
            ReaderTextAlignment.JUSTIFY
        ).forEach { alignment ->
            val actual = renderChapterTitlePage(source, alignment, titleEnd)
            assertTrue("$alignment 下章首标题没有保持左对齐", left.sameAs(actual))
            actual.recycle()
        }
        // 标题墨迹要贴在正文列左边缘，而不是被居中后留下大段左边距。
        assertTrue("标题墨迹左缘应落在正文列左边缘附近", firstInkColumn(left) in 40..80)
        left.recycle()
    }

    private fun renderChapterTitlePage(
        source: Spanned,
        alignment: ReaderTextAlignment,
        titleParagraphEnd: Int
    ): Bitmap {
        val page = PageContentView(RuntimeEnvironment.getApplication()).apply {
            configure(fontSizePx = 48f, textColor = Color.BLACK, textAlignment = alignment)
        }
        val text = applyReaderTextAlignment(source, alignment, titleParagraphEnd)
        page.setPageContent(text, 0, text.length)
        return render(page)
    }

    /** 第一列出现文字墨迹的 x 坐标，找不到返回 -1。 */
    private fun firstInkColumn(bitmap: Bitmap): Int {
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0x40) return x
            }
        }
        return -1
    }

    /**
     * 可见文字层建 layout 用的画笔如果和绘制画笔共用，逐字改字号会污染布局坐标：标题的
     * RelativeSizeSpan 会被二次放大（1.4×1.4），字形按 1.4× 画、位置却按 2× 排开，
     * 标题就变成巨大字距。这里直接量像素：N 个同字标题的墨迹宽度必须等于
     * 「单字墨迹宽度 + (N-1) × 标题字号」，与字体无关。
     */
    @Test
    fun chapterTitleGlyphAdvanceMatchesTitleFontSize() {
        val fontSizePx = 65f
        val titleFontSizePx = fontSizePx * 1.4f
        val singleInk = renderTitleInkWidth("\u6392", fontSizePx)
        val stacked = renderTitleInkWidth("\u6392".repeat(5), fontSizePx)
        assertTrue("标题单字应有墨迹", singleInk > 0)

        val expected = singleInk + (5 - 1) * titleFontSizePx
        assertTrue(
            "标题字距应等于标题字号：期望墨迹宽 ${expected}px，实际 ${stacked}px",
            abs(stacked - expected) <= 6f
        )
    }

    private fun renderTitleInkWidth(title: String, fontSizePx: Float): Float {
        val source = SpannableStringBuilder("$title\n").apply {
            setSpan(RelativeSizeSpan(1.4f), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val page = PageContentView(RuntimeEnvironment.getApplication()).apply {
            configure(fontSizePx = fontSizePx, textColor = Color.BLACK)
        }
        page.setPageContent(source, 0, source.length)
        val bitmap = render(page)
        val width = firstTextBandInkWidth(bitmap)
        bitmap.recycle()
        return width
    }

    /** 第一行文字墨迹的总宽度（像素）。 */
    private fun firstTextBandInkWidth(bitmap: Bitmap): Float {
        fun inked(x: Int, y: Int): Boolean = Color.alpha(bitmap.getPixel(x, y)) > 0x40
        fun rowHasInk(y: Int): Boolean = (0 until bitmap.width).any { inked(it, y) }

        var bandStart = -1
        var bandEnd = -1
        var y = 0
        while (y < bitmap.height && bandEnd < 0) {
            if (rowHasInk(y)) {
                bandStart = y
                var gap = 0
                while (y < bitmap.height && gap <= 4) {
                    if (rowHasInk(y)) gap = 0 else gap++
                    y++
                }
                bandEnd = y - gap
            } else {
                y++
            }
        }
        if (bandStart < 0 || bandEnd <= bandStart) return 0f

        val left = (0 until bitmap.width).first { x -> (bandStart until bandEnd).any { inked(x, it) } }
        val right = (bitmap.width - 1 downTo 0).first { x -> (bandStart until bandEnd).any { inked(x, it) } }
        return (right - left + 1).toFloat()
    }
}

