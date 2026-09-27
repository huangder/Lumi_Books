package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.Spannable
import android.text.Selection
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.ContinuousSelectableTextView
import com.huangder.lumibooks.ui.reader.applyReaderTextAlignment
import com.huangder.lumibooks.ui.reader.readerBreakStrategy
import com.huangder.lumibooks.ui.reader.readerJustificationMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPunctuationLayoutTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sample = "他说：“你好。”随后离开。她问：“真的！”他答：“好吗？”《（故事）》结束。"
    private val opening = "“‘（［｛《〈「『【〔〖〘〚"
    private val closing = "。，、；：！？）］｝》〉」』】〕〗〙〛”’"
    private fun fonts() = listOf(Typeface.DEFAULT, ResourcesCompat.getFont(context, R.font.lxgw_wenkai)!!)
    private fun paint(font: Typeface, size: Float, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font
        textSize = size
        color = Color.BLACK
        letterSpacing = spacing
        density = context.resources.displayMetrics.density
    }
    private fun layout(text: CharSequence, paint: TextPaint, width: Int,
                       alignment: ReaderTextAlignment = ReaderTextAlignment.LEFT): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setIncludePad(false).setBreakStrategy(alignment.readerBreakStrategy())
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setJustificationMode(alignment.readerJustificationMode()).build()

    @Test fun bothModesPreserveCharactersStylesAndPunctuationClasses() {
        val source = SpannableString(sample).apply {
            setSpan(URLSpan("https://example.test"), 0, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        var text: CharSequence = source
        repeat(4) { pass ->
            text = applyReaderPunctuationCompression(text, frameworkDrawsText = pass % 2 == 0)
            val spanned = text as Spanned
            assertEquals(source.toString(), text.toString())
            assertEquals(0, spanned.getSpans(0, text.length, ReplacementSpan::class.java).size)
            assertEquals(sample.count(::isReaderCompressiblePunctuation),
                spanned.getSpans(0, text.length, ReaderPunctuationCompressionSpan::class.java).size)
            assertEquals(1, spanned.getSpans(0, text.length, URLSpan::class.java).size)
            assertEquals(1, spanned.getSpans(0, text.length, StyleSpan::class.java).size)
        }
    }

    @Test fun wrappingPreservesOpeningAndClosingPunctuationAcrossFontsAndWidths() {
        for (font in fonts()) for (size in listOf(24f, 48f)) for (spacing in listOf(-0.05f, 0f, 0.1f)) {
            for (alignment in ReaderTextAlignment.entries) {
                val text = applyReaderPunctuationCompression(applyReaderTextAlignment(sample.repeat(2), alignment), true)
                for (width in (size * 5).toInt()..(size * 9).toInt() step 7) {
                    val sl = layout(text, paint(font, size, spacing), width, alignment)
                    for (line in 0 until sl.lineCount) {
                        val start = sl.getLineStart(line)
                        val end = readerLineContentEnd(text, start, sl.getLineEnd(line))
                        if (end <= start) continue
                        val description = "size=$size spacing=$spacing alignment=$alignment width=$width line=${text.subSequence(start, end)}"
                        assertFalse("Prohibited line start: $description", text[start] in closing)
                        assertFalse("Prohibited line end: $description", text[end - 1] in opening)
                        val geometry = ReaderLineGeometry(sl, text, alignment.readerJustificationMode())
                        val bounds = geometry.lineRange(line)!!
                        assertTrue("Line overflows: $description right=${bounds.right}", bounds.right <= width + 0.5f)
                        assertTrue("Line crosses left margin: $description left=${bounds.left}", bounds.left >= -0.5f)
                    }
                }
            }
        }
    }

    @Test fun measuredSlotsContainInkAndMatchCaretAdvances() {
        for (font in fonts()) for (size in listOf(24f, 48f, 72f)) for (bold in listOf(false, true)) {
            val raw = SpannableString("。”！”？”（）“”").apply {
                if (bold) setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val text = applyReaderPunctuationCompression(raw) as Spanned
            val base = paint(font, size)
            val sl = layout(text, base, 2000)
            val geometry = ReaderLineGeometry(sl, text, Layout.JUSTIFICATION_MODE_NONE)
            var lastInkRight = Float.NEGATIVE_INFINITY
            for (i in 0 until text.length) {
                val glyphPaint = readerGlyphPaint(base, text, i, i + 1)
                val ink = readerGlyphInk(glyphPaint, text, i, i + 1)
                val slot = readerPunctuationSlotWidth(glyphPaint, text, i, i + 1)
                val range = geometry.horizontalRange(0, i, i + 1)!!
                assertEquals("Geometry and metrics disagree", slot, range.right - range.left, 0.15f)
                val nativeWidth = sl.getPrimaryHorizontal(i + 1) - sl.getPrimaryHorizontal(i)
                assertEquals("Native caret and measured slot disagree font=$font size=$size bold=$bold index=$i glyph=${text[i]} scale=${glyphPaint.textScaleX}", slot,
                    nativeWidth, 0.25f)
                val left = range.left + readerPunctuationDrawShift(ink.left, ink.width, slot) + ink.left
                val right = left + ink.width
                val bearing = maxOf(1f, glyphPaint.textSize * 0.025f)
                assertTrue("Missing left bearing at $i", left >= range.left + bearing - 0.1f)
                assertTrue("Missing right bearing at $i", right <= range.right - bearing + 0.1f)
                assertTrue("Adjacent punctuation overlaps at $i", left - lastInkRight >= 2f * bearing - 0.1f)
                assertEquals(i, geometry.offsetForHorizontal(0, range.left + 0.1f))
                lastInkRight = right
            }
        }
    }

    @Test fun continuousAndPagedInkAreIdenticalWithOneLayout() {
        for (font in fonts()) for (alignment in ReaderTextAlignment.entries) {
            val text = applyReaderPunctuationCompression(applyReaderTextAlignment(sample.repeat(3), alignment), true)
            val scroll = ContinuousSelectableTextView(context).apply {
                setPadding(0, 0, 0, 0)
                setTextColor(Color.BLACK)
                typeface = font
                textSize = 24f
                setLineSpacing(0f, 1f)
                breakStrategy = alignment.readerBreakStrategy()
                justificationMode = alignment.readerJustificationMode()
                readerJustificationMode = justificationMode
                setReaderText(text)
            }
            val scrollImage = render(scroll, 320, 1000)
            val page = JustifiedTextView(context).apply {
                setSourceLayoutProvider { scroll.layout }
                this.text = scroll.text
                readerJustificationMode = scroll.justificationMode
            }
            val pageImage = render(page, 320, 1000)
            assertTrue("Mode switch changes ink for $alignment", scrollImage.sameAs(pageImage))
            scrollImage.recycle(); pageImage.recycle()
        }
    }

    @Test fun indentedParagraphsKeepBothModeInkInsideReaderMargins() {
        val raw = "女娲后人满意的点了点头，能分析到这种程度，把敌人的底细摸得一清二楚。\n" +
            "他说：“你好。”随后离开。她问：“真的！”他答：“好吗？”《（故事）》结束。"
        for (font in fonts()) for (spacing in listOf(-0.05f, 0f, 0.1f)) {
            for (alignment in ReaderTextAlignment.entries) for (width in listOf(320, 440)) {
                val source = SpannableString(applyReaderTextAlignment(raw, alignment)).apply {
                    setSpan(LeadingMarginSpan.Standard(64, 0), 0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                }
                val scroll = ContinuousSelectableTextView(context).apply {
                    setPadding(40, 10, 40, 10)
                    setTextColor(Color.BLACK)
                    typeface = font
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 32f)
                    letterSpacing = spacing
                    breakStrategy = alignment.readerBreakStrategy()
                    justificationMode = alignment.readerJustificationMode()
                    readerJustificationMode = justificationMode
                    setReaderText(applyReaderPunctuationCompression(source, true))
                }
                val scrollImage = render(scroll, width, 1200)
                val sl = scroll.layout
                val geometry = ReaderLineGeometry(sl, scroll.text, scroll.readerJustificationMode)
                for (line in 0 until sl.lineCount) {
                    val bounds = geometry.lineRange(line) ?: continue
                    val description = "alignment=$alignment spacing=$spacing width=$width line=$line"
                    assertTrue("Ink geometry crosses right margin: $description right=${bounds.right}",
                        bounds.right <= sl.width + 0.5f)
                    assertTrue("Ink geometry crosses left margin: $description left=${bounds.left}",
                        bounds.left >= -0.5f)
                    val end = readerLineContentEnd(scroll.text, sl.getLineStart(line), sl.getLineEnd(line))
                    if ((sl.getLineStart(line) until end).any { readerIsCompressedPunctuation(scroll.text, it) }) {
                        assertEquals("Trailing caret differs from ink: $description", bounds.right,
                            geometry.horizontalPosition(end, true)!!, 0.5f)
                    }
                }
                val page = JustifiedTextView(context).apply {
                    setPadding(40, 10, 40, 10)
                    setSourceLayoutProvider { scroll.layout }
                    text = scroll.text
                    readerJustificationMode = scroll.readerJustificationMode
                }
                val pageImage = render(page, width, 1200)
                assertTrue("Margins differ between reading modes", scrollImage.sameAs(pageImage))
                if (font == fonts().first() && spacing == 0f &&
                    alignment == ReaderTextAlignment.JUSTIFY && width == 440) {
                    val preview = Bitmap.createBitmap(width, 1200, Bitmap.Config.ARGB_8888)
                    preview.eraseColor(Color.WHITE)
                    Canvas(preview).drawBitmap(scrollImage, 0f, 0f, null)
                    val output = File("build/reports/punctuation/margin-${android.os.Build.VERSION.SDK_INT}.png")
                    output.parentFile!!.mkdirs()
                    output.outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    preview.recycle()
                }
                for (x in width - 40 until width) {
                    assertFalse("Painted text inside right padding at x=$x, alignment=$alignment",
                        (0 until scrollImage.height).any { y -> Color.alpha(scrollImage.getPixel(x, y)) > 0 })
                }
                scrollImage.recycle(); pageImage.recycle()
            }
        }
    }

    @Test fun paragraphIndentNeverBecomesExtraTrackingOnContinuationOrFinalLines() {
        for (font in fonts()) for (size in listOf(24f, 48f)) for (spacing in listOf(-0.05f, 0f, 0.1f)) {
            val source = SpannableString((sample + "清二楚。").repeat(3)).apply {
                setSpan(LeadingMarginSpan.Standard((size * 2).toInt(), (size / 2).toInt()),
                    0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            }
            val text = applyReaderPunctuationCompression(source) as Spanned
            val sl = layout(text, paint(font, size, spacing), (size * 10).toInt())
            val geometry = ReaderLineGeometry(sl, text, Layout.JUSTIFICATION_MODE_NONE)
            assertTrue(sl.lineCount > 2)
            for (line in 0 until sl.lineCount) {
                val start = sl.getLineStart(line)
                val end = readerLineContentEnd(text, start, sl.getLineEnd(line))
                val unindented = SpannableString(text.subSequence(start, end)).apply {
                    getSpans(0, length, LeadingMarginSpan::class.java).forEach(::removeSpan)
                }
                val expected = Layout.getDesiredWidth(unindented, sl.paint)
                val bounds = geometry.lineRange(line)!!
                assertEquals("Indent inflated text advance on line $line", expected,
                    bounds.right - bounds.left, 0.5f)
                assertEquals("Indent changed the first caret on line $line", sl.getPrimaryHorizontal(start),
                    bounds.left, 0.5f)
            }
        }
    }

    @Test fun preservesExplicitNewlinesAndDrawsOnlyVisibleChapterLines() {
        val text = applyReaderPunctuationCompression("第一行。\n。原文显式行首\n" + sample.repeat(300)) as Spanned
        val sl = layout(text, paint(fonts().last(), 32f), 320)
        assertEquals('。', text[sl.getLineStart(1)])
        val bitmap = Bitmap.createBitmap(320, 150, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(0f, -sl.getLineTop(500).toFloat())
        val visible = readerVisibleLines(canvas, sl)
        assertEquals(500, visible.first)
        assertTrue(visible.count() < 10)
        ReaderTextPainter().draw(canvas, sl, text, ReaderLineGeometry(sl, text, 0))
        assertTrue((0 until bitmap.width).any { x -> (0 until bitmap.height).any { y -> Color.alpha(bitmap.getPixel(x,y)) > 0 } })
        bitmap.recycle()
    }

    @Test fun rendersStyledComplexTextAndInlineImagesWithoutChangingOffsets() {
        val source = SpannableString("“e\u0301 👩🏽‍💻 العربية”正文。\n前\uFFFC后。”").apply {
            setSpan(ForegroundColorSpan(Color.RED), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(38), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val image = indexOf('\uFFFC')
            setSpan(ImageSpan(ColorDrawable(Color.BLUE).apply { setBounds(0,0,40,32) }), image, image+1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val text = applyReaderPunctuationCompression(source) as Spanned
        val sl = layout(text, paint(fonts().last(), 32f), 480)
        val bitmap = Bitmap.createBitmap(480, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        ReaderTextPainter().draw(Canvas(bitmap), sl, text, ReaderLineGeometry(sl, text, 0))
        assertEquals(source.toString(), text.toString())
        assertTrue((0 until bitmap.width).any { x -> (0 until bitmap.height).any { y -> bitmap.getPixel(x,y) == Color.BLUE } })
        val output = File("build/reports/punctuation/complex-${android.os.Build.VERSION.SDK_INT}.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun shapedRunsMatchPlatformInkForComplexScripts() {
        for (raw in listOf("e\u0301👩🏽‍💻", "office affinity AV", "العربية", "किताब", "ภาษาไทย")) {
            val text = SpannableString(raw)
            val sl = layout(text, paint(Typeface.DEFAULT, 42f), 480)
            val expected = Bitmap.createBitmap(480, 100, Bitmap.Config.ARGB_8888)
            val actual = Bitmap.createBitmap(480, 100, Bitmap.Config.ARGB_8888)
            sl.draw(Canvas(expected))
            ReaderTextPainter().draw(Canvas(actual), sl, text, ReaderLineGeometry(sl, text, 0))
            assertTrue("Complex shaping differs from Android: $raw", expected.sameAs(actual))
            expected.recycle(); actual.recycle()
        }
    }

    @Test fun fontAndSizeChangesRebuildContinuousGeometryAndRestoreOriginalPixels() {
        val text = applyReaderPunctuationCompression(sample, true)
        val view = ContinuousSelectableTextView(context).apply {
            typeface = Typeface.DEFAULT
            textSize = 24f
            setTextColor(Color.BLACK)
            setReaderText(text)
        }
        val original = render(view, 320, 600)
        val oldCount = view.layout.lineCount
        view.typeface = fonts().last()
        view.textSize = 38f
        render(view, 320, 600).recycle()
        assertTrue(view.layout.lineCount > oldCount)
        view.typeface = Typeface.DEFAULT
        view.textSize = 24f
        val restored = render(view, 320, 600)
        assertTrue("Font/size round trip retained stale geometry", original.sameAs(restored))
        original.recycle(); restored.recycle()
    }

    @Test fun renderPunctuationMatrixForVisualReview() {
        val bitmap = Bitmap.createBitmap(760, 1000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        var y = 0f
        for (font in fonts()) for (spacing in listOf(-0.05f, 0f, 0.1f)) {
            val text = applyReaderPunctuationCompression(sample) as Spanned
            val sl = layout(text, paint(font, 38f, spacing), 720, ReaderTextAlignment.JUSTIFY)
            val save = canvas.save()
            canvas.translate(20f, y + 10f)
            ReaderTextPainter().draw(canvas, sl, text,
                ReaderLineGeometry(sl, text, ReaderTextAlignment.JUSTIFY.readerJustificationMode()))
            canvas.restoreToCount(save)
            y += sl.height + 22f
        }
        val output = File("build/reports/punctuation/matrix-${android.os.Build.VERSION.SDK_INT}.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun paginatedPagesPreserveTextAndLegalPunctuationBreaks() = runBlocking {
        for (font in fonts()) for (alignment in listOf(ReaderTextAlignment.LEFT, ReaderTextAlignment.JUSTIFY)) {
            val text = applyReaderPunctuationCompression(sample.repeat(8))
            val page = PageContentView(context).apply {
                configure(32f, Color.BLACK, lineHeightMult = 1f,
                    typeface = font, marginLeftPx = 0f, marginRightPx = 0f,
                    marginTopPx = 0f, marginBottomPx = 0f, textAlignment = alignment)
            }
            val engine = PageLayoutEngine().apply {
                configure(280, 220, 32f, lineSpacingPx = 0f, lineSpacingMult = 1f,
                    typeface = font, marginLeftPx = 0f, marginRightPx = 0f,
                    marginTopPx = 0f, marginBottomPx = 0f, textAlignment = alignment, chapterCount = 1)
                sharedTextPaint = page.textView.paint
            }
            val chapter = engine.layout(0, text)
            assertTrue(chapter.totalPages > 1)
            for (slice in chapter.pages) {
                page.setPageContent(text, slice.startCharOffset, slice.endCharOffset)
                render(page, 280, 220).recycle()
                val visible = page.textView.layout
                assertEquals(slice.endLine - slice.startLine, visible.lineCount)
                for (line in 0 until visible.lineCount) {
                    // HIGH_QUALITY optimizes a whole paragraph, so a sliced page
                    // can redistribute its interior lines. SIMPLE must be exact.
                    if (alignment == ReaderTextAlignment.LEFT) assertEquals(
                        "Page wrapped differently from paginator",
                        chapter.staticLayout.getLineStart(slice.startLine + line),
                        slice.startCharOffset + visible.getLineStart(line))
                    val start = visible.getLineStart(line)
                    val end = readerLineContentEnd(visible.text, start, visible.getLineEnd(line))
                    assertFalse(visible.text[start] in closing)
                    assertFalse(visible.text[end - 1] in opening)
                    assertTrue(visible.getLineBottom(line) <= 220)
                }
            }
            val joined = chapter.pages.joinToString("") {
                text.subSequence(it.startCharOffset, it.endCharOffset).toString()
            }
            assertEquals(text.toString(), joined)
        }
    }

    @Test fun continuousSelectionAndHighlightsUseTheVisiblePunctuationCoordinates() {
        val source = applyReaderPunctuationCompression("他说：“你好。”随后离开。", true)
        val view = ContinuousSelectableTextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(600, 200)
            textSize = 36f
            typeface = fonts().last()
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 0)
            readerSelectionColor = Color.GREEN
            setReaderText(source)
        }
        render(view, 600, 200).recycle()
        val text = view.text as Spannable
        val start = text.indexOf('。')
        val end = start + 2
        Selection.setSelection(text, start, end)
        val bitmap = render(view, 600, 200)
        assertEquals("。”", text.subSequence(Selection.getSelectionStart(text), Selection.getSelectionEnd(text)).toString())
        for (i in start..end) {
            val x = view.readerHorizontalPosition(i)!!
            assertEquals("Selection caret does not map back to its offset", i, view.readerOffsetForHorizontal(0, x))
        }
        val selectedLeft = view.readerHorizontalPosition(start)!!
        val selectedRight = view.readerHorizontalPosition(end, true)!!
        val greenColumns = (0 until bitmap.width).filter { x ->
            (0 until bitmap.height).any { y -> bitmap.getPixel(x,y) == Color.GREEN }
        }
        assertTrue(greenColumns.isNotEmpty())
        assertTrue(greenColumns.first() <= selectedLeft && greenColumns.last() >= selectedRight - 1f)
        bitmap.recycle()
    }

    private fun render(view: View, width: Int, height: Int): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
}
