package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.Selection
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.res.ResourcesCompat
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.ContinuousSelectableTextView
import com.huangder.lumibooks.ui.reader.ContinuousSelectionMagnifierShadow
import com.huangder.lumibooks.ui.reader.applyReaderTextAlignment
import com.huangder.lumibooks.ui.reader.readerBreakStrategy
import com.huangder.lumibooks.ui.reader.readerJustificationMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderEnglishLayoutTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sample = "The office, suddenly silent, felt different. \"Hello, world!\" she said. " +
        "Don't forget the coffee: a fine afternoon awaits. “Really?” he asked. " +
        "An efficient reader can select every word, even near the right edge."

    @Test fun paintedEnglishWordsMatchSelectionAndTouchCoordinates() {
        for (font in listOf(Typeface.DEFAULT, ResourcesCompat.getFont(context, R.font.lxgw_wenkai)!!)) {
            for (spacing in listOf(0f, 0.08f, -0.03f)) for (alignment in ReaderTextAlignment.entries) {
                for (width in listOf(320, 480)) {
                    // A color span isolates each word's ink without changing its advances.
                    for (word in listOf("office", "silent", "different", "Hello", "coffee", "Really", "efficient", "edge")) {
                        val start = sample.indexOf(word)
                        val end = start + word.length
                        val source = SpannableString(applyReaderTextAlignment(sample, alignment)).apply {
                            setSpan(LeadingMarginSpan.Standard(56, 0), 0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                            setSpan(ForegroundColorSpan(Color.RED), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        val view = ContinuousSelectableTextView(context).apply {
                            setPadding(24, 12, 24, 12)
                            setTextColor(Color.BLACK)
                            typeface = font
                            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28f)
                            letterSpacing = spacing
                            justificationMode = alignment.readerJustificationMode()
                            readerJustificationMode = justificationMode
                            breakStrategy = alignment.readerBreakStrategy()
                            setReaderText(applyReaderPunctuationCompression(source, true))
                        }
                        val bitmap = render(view, width, 1300)
                        val sl = view.layout
                        val geometry = ReaderLineGeometry(sl, view.text, view.readerJustificationMode)
                        for (line in sl.getLineForOffset(start)..sl.getLineForOffset(end - 1)) {
                            val a = maxOf(start, sl.getLineStart(line))
                            val b = minOf(end, sl.getLineEnd(line))
                            val range = geometry.horizontalRange(line, a, b)!!
                            val redXs = (0 until width).filter { x ->
                                (sl.getLineTop(line) + 12 until sl.getLineBottom(line) + 12).any { y ->
                                    val pixel = bitmap.getPixel(x, y)
                                    Color.alpha(pixel) > 128 && Color.red(pixel) > 200 && Color.green(pixel) < 50
                                }
                            }
                            val description = "$word alignment=$alignment spacing=$spacing width=$width line=$line"
                            assertTrue("Missing word: $description", redXs.isNotEmpty())
                            assertTrue("Word starts before its selection: $description ink=${redXs.first()} range=$range",
                                redXs.first() >= range.left + 24 - 2f)
                            assertTrue("Word ends after its selection: $description ink=${redXs.last()} range=$range",
                                redXs.last() <= range.right + 24 + 2f)
                            // Touch the visible middle of the word, rather than deriving
                            // the test coordinate from the mapper under test.
                            val x = (redXs.first() + redXs.last()) / 2f
                            val y = 12 + (sl.getLineTop(line) + sl.getLineBottom(line)) / 2f
                            val offset = view.getOffsetForPosition(x, y)
                            assertTrue("Visible word maps to a different word: $description offset=$offset expected=$a..$b",
                                offset in a until b)
                        }
                        if (font == Typeface.DEFAULT && spacing == 0f && width == 480 &&
                            alignment == ReaderTextAlignment.JUSTIFY && word == "office") {
                            val output = File("build/reports/english/layout-${android.os.Build.VERSION.SDK_INT}.png")
                            output.parentFile!!.mkdirs()
                            val preview = Bitmap.createBitmap(width, 1300, Bitmap.Config.ARGB_8888)
                            preview.eraseColor(Color.WHITE)
                            Canvas(preview).drawBitmap(bitmap, 0f, 0f, null)
                            output.outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            preview.recycle()
                        }
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test fun unexpandedLatinLigaturesAndCombiningMarksKeepPlatformShaping() {
        for (raw in listOf("office affinity AV", "Cafe\u0301, nai\u0308ve!", "Don't co-operate? It's fine.")) {
            val text = SpannableString(raw)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f; color = Color.BLACK }
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, paint, 600).setIncludePad(false).build()
            val expected = Bitmap.createBitmap(600, sl.height, Bitmap.Config.ARGB_8888)
            val actual = Bitmap.createBitmap(600, sl.height, Bitmap.Config.ARGB_8888)
            sl.draw(Canvas(expected))
            ReaderTextPainter().draw(Canvas(actual), sl, text, ReaderLineGeometry(sl, text, 0))
            assertTrue("Latin shaping changed: $raw", expected.sameAs(actual))
            expected.recycle(); actual.recycle()
        }
    }

    @Test fun englishStyleTransitionsKeepTheirOwnShapingContext() {
        val text = SpannableString("An office, affinity; Cafe\u0301 and fine print.").apply {
            setSpan(StyleSpan(Typeface.BOLD_ITALIC), 3, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(24), 11, 19, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(Color.RED), 11, 19, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f; color = Color.BLACK }
        val sl = StaticLayout.Builder.obtain(text, 0, text.length, paint, 800).setIncludePad(false).build()
        val expected = Bitmap.createBitmap(800, sl.height, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(800, sl.height, Bitmap.Config.ARGB_8888)
        sl.draw(Canvas(expected))
        ReaderTextPainter().draw(Canvas(actual), sl, text, ReaderLineGeometry(sl, text, 0))
        assertTrue("English style boundary changed shaping", expected.sameAs(actual))
        expected.recycle(); actual.recycle()
    }

    @Test
    @Config(shadows = [ContinuousSelectionMagnifierShadow::class])
    fun longPressSelectsThePaintedEnglishWordInBothModes() {
        for (paged in listOf(false, true)) for (word in listOf("office", "silent", "coffee")) {
            val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
            try {
                val activity = controller.get()
                val start = sample.indexOf(word)
                val source = SpannableString(sample).apply {
                    setSpan(ForegroundColorSpan(Color.RED), start, start + word.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(LeadingMarginSpan.Standard(56, 0), 0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                }
                val text = applyReaderPunctuationCompression(source)
                val root: View
                val view: RoundedHighlightTextView
                if (paged) {
                    val page = PageContentView(activity).apply {
                        configure(28f, Color.BLACK, lineHeightMult = 1f, typeface = Typeface.DEFAULT,
                            marginLeftPx = 24f, marginRightPx = 24f, marginTopPx = 12f, marginBottomPx = 12f,
                            textAlignment = ReaderTextAlignment.JUSTIFY)
                        setPageContent(text, 0, text.length)
                    }
                    view = page.textView as RoundedHighlightTextView
                    root = page
                } else {
                    view = ContinuousSelectableTextView(activity).apply {
                        setPadding(24, 12, 24, 12)
                        setTextColor(Color.BLACK)
                        typeface = Typeface.DEFAULT
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28f)
                        justificationMode = ReaderTextAlignment.JUSTIFY.readerJustificationMode()
                        readerJustificationMode = justificationMode
                        breakStrategy = ReaderTextAlignment.JUSTIFY.readerBreakStrategy()
                        setReaderText(text)
                    }
                    root = view
                }
                activity.setContentView(root)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                view.requestFocus()
                val bitmap = render(root, 320, 1000)
                val redPixels = mutableListOf<Pair<Int, Int>>()
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) > 128 && Color.red(pixel) > 200 && Color.green(pixel) < 50) {
                        redPixels.add(x to y)
                    }
                }
                assertTrue("Missing fixture ink", redPixels.isNotEmpty())
                val x = (redPixels.minOf { it.first } + redPixels.maxOf { it.first }) / 2f
                val y = (redPixels.minOf { it.second } + redPixels.maxOf { it.second }) / 2f
                bitmap.recycle()
                val downTime = SystemClock.uptimeMillis()
                MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).also {
                    view.dispatchTouchEvent(it); it.recycle()
                }
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                    Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 100L))
                MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0).also {
                    view.dispatchTouchEvent(it); it.recycle()
                }
                val a = Selection.getSelectionStart(view.text)
                val b = Selection.getSelectionEnd(view.text)
                assertTrue("Long press did not select text: paged=$paged word=$word selection=$a..$b", a >= 0 && b > a)
                assertEquals("Long press selected the wrong word: paged=$paged x=$x y=$y", word,
                    view.text.subSequence(a, b).toString())
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    private fun render(view: View, width: Int, height: Int): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
}
