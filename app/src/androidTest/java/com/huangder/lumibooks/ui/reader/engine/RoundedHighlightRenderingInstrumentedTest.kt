package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.Selection
import android.text.Layout
import android.util.TypedValue
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoundedHighlightRenderingInstrumentedTest {
    @Test
    fun ttsHighlightUsesTheUnifiedTenPercentContrastBoost() {
        val lightBackground = Color.WHITE
        val darkBackground = 0xFF111111.toInt()
        val oldLight = TtsSentenceHighlightSpan.computeHighlightColor(lightBackground, 0.06f)
        val newLight = TtsSentenceHighlightSpan.computeHighlightColor(lightBackground)
        val oldDark = TtsSentenceHighlightSpan.computeHighlightColor(darkBackground, 0.06f)
        val newDark = TtsSentenceHighlightSpan.computeHighlightColor(darkBackground)

        assertEquals(Color.alpha(oldLight), Color.alpha(newLight))
        assertEquals(Color.alpha(oldDark), Color.alpha(newDark))
        assertTrue("light TTS color must be visibly darker", Color.red(newLight) < Color.red(oldLight))
        assertTrue("dark TTS color must be visibly lighter", Color.red(newDark) > Color.red(oldDark))
    }

    @Test
    fun visiblePagedUnderlineUsesWrappedLineEndAndGlyphDescent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val underlineColor = 0xFF1677FF.toInt()
            val value = SpannableString("这是一段用于验证自动换行划线位置的正文，它应当覆盖完整的首行并继续到第二行。").apply {
                setSpan(WaveUnderlineSpan(underlineColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val horizontalPadding = (20f * density).roundToInt()
            val view = RoundedHighlightTextView(context).apply {
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setLineSpacing(0f, 1.8f)
                setPadding(horizontalPadding, 0, horizontalPadding, 0)
                text = value
            }
            val width = (360f * density).roundToInt()
            val height = (280f * density).roundToInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))

            val textLayout = requireNotNull(view.layout)
            assertTrue("fixture must wrap", textLayout.lineCount >= 2)
            val firstBaseline = view.totalPaddingTop + textLayout.getLineBaseline(0)
            val firstBandTop = (firstBaseline + view.paint.fontMetrics.descent).roundToInt()
            val firstBandBottom = (firstBandTop + 5f * density).roundToInt().coerceAtMost(height - 1)
            val firstLineRight = (view.totalPaddingLeft + textLayout.getLineRight(0)).roundToInt()
            val rightmostUnderline = (firstBandTop.coerceAtLeast(0)..firstBandBottom)
                .flatMap { y -> (0 until width).map { x -> x to y } }
                .filter { (x, y) -> isHighlightPixel(bitmap.getPixel(x, y), underlineColor) }
                .maxOfOrNull { it.first }
            assertTrue("the first wrapped underline must be visible", rightmostUnderline != null)
            assertTrue(
                "the visible underline must reach the wrapped line end",
                requireNotNull(rightmostUnderline) >= firstLineRight - 4f * density
            )

            val lastLine = textLayout.lineCount - 1
            val expectedLastTop = view.totalPaddingTop + textLayout.getLineBaseline(lastLine) +
                view.paint.fontMetrics.descent + 1f * density - 1.6f * density
            val lastLineUnderlineRows = (textLayout.getLineTop(lastLine) until height)
                .filter { y ->
                    (0 until width).any { x -> isHighlightPixel(bitmap.getPixel(x, y), underlineColor) }
                }
            assertTrue("the final underline must be visible", lastLineUnderlineRows.isNotEmpty())
            assertTrue(
                "the final underline must follow glyph descent instead of the spacious line box",
                kotlin.math.abs(lastLineUnderlineRows.first() - expectedLastTop) <= 2f * density
            )
        }
    }

    @Test
    fun justifiedUnderlineUsesVisibleGlyphGeometryAcrossWrappedLines() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val underlineColor = 0xFF1677FF.toInt()
            val value = SpannableString("这是一段用于验证自动换行划线位置的正文，它应当覆盖完整的首行并继续到第二行。").apply {
                setSpan(
                    WaveUnderlineSpan(underlineColor),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val horizontalPadding = (20f * density).roundToInt()
            val view = JustifiedTextView(context).apply {
                setTextSize(20f * density)
                setDefaultTextColor(Color.BLACK)
                setLineSpacing(0f, 1.5f)
                setPadding(horizontalPadding, 0, horizontalPadding, 0)
                text = value
            }
            val width = (360f * density).roundToInt()
            val height = (260f * density).roundToInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))

            val underlinePixels = mutableListOf<Pair<Int, Int>>()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (isHighlightPixel(bitmap.getPixel(x, y), underlineColor)) {
                        underlinePixels += x to y
                    }
                }
            }
            assertTrue("wrapped underline must be visible", underlinePixels.isNotEmpty())
            val occupiedRows = underlinePixels.map { it.second }.distinct().sorted()
            val rowBands = occupiedRows.fold(mutableListOf<MutableList<Int>>()) { bands, row ->
                if (bands.isEmpty() || row > bands.last().last() + 1) bands += mutableListOf(row)
                else bands.last() += row
                bands
            }
            assertTrue("fixture must produce underline segments on multiple lines", rowBands.size >= 2)

            val firstBandRows = rowBands.first().toSet()
            val firstBandRight = underlinePixels
                .asSequence()
                .filter { it.second in firstBandRows }
                .maxOf { it.first }
            val contentRight = width - horizontalPadding
            assertTrue(
                "the first wrapped segment must reach the justified line end: right=$firstBandRight expected=$contentRight",
                firstBandRight >= contentRight - 4f * density
            )

            val lastBand = rowBands.last()
            val previousBandBottom = rowBands[rowBands.lastIndex - 1].last()
            val lastUnderlineTop = lastBand.first()
            val lastGlyphBottom = (previousBandBottom + 1 until lastUnderlineTop)
                .filter { y ->
                    (0 until width).any { x ->
                        val pixel = bitmap.getPixel(x, y)
                        Color.alpha(pixel) > 128 &&
                            Color.red(pixel) < 80 &&
                            Color.green(pixel) < 80 &&
                            Color.blue(pixel) < 80
                    }
                }
                .maxOrNull()
            assertTrue("the last line must contain visible glyphs above its underline", lastGlyphBottom != null)
            assertTrue(
                "the final underline must follow glyph descent, not the spacious line box",
                lastUnderlineTop - requireNotNull(lastGlyphBottom) <= 6f * density
            )
        }
    }

    @Test
    fun savedHighlightRendersAsSeparatedRoundedLines() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val text = "大江东去，浪淘尽，千古风流人物。故垒西边，人道是，\n三国周郎赤壁。乱石穿空，惊涛拍岸，卷起千堆雪。"
            val highlightStart = text.indexOf('浪')
            val highlightEnd = text.length - 2
            val highlightColor = 0xFFFF5F64.toInt()
            val view = PageContentView(context).apply {
                setReaderBackground(Color.WHITE, null)
                configure(
                    fontSizePx = 18f * density,
                    textColor = Color.BLACK,
                    lineHeightMult = 1.2f,
                    marginLeftPx = 24f * density,
                    marginTopPx = 24f * density,
                    marginRightPx = 24f * density,
                    marginBottomPx = 24f * density
                )
                setPageContent(
                    fullText = text,
                    startChar = 0,
                    endChar = text.length,
                    highlights = listOf(Triple(highlightStart, highlightEnd, highlightColor))
                )
            }
            val width = (420f * density).roundToInt()
            val height = (360f * density).roundToInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))

            val layout = requireNotNull(view.textView.layout)
            val firstLine = layout.getLineForOffset(highlightStart)
            val nextLine = firstLine + 1
            assertTrue("fixture must wrap the highlight", nextLine < layout.lineCount)

            val lineBoundaryY = view.textView.totalPaddingTop + layout.getLineBottom(firstLine)
            val highlightedPixelsOnBoundary = (0 until width).count { x ->
                isHighlightPixel(bitmap.getPixel(x, lineBoundaryY), highlightColor)
            }
            assertTrue(
                "adjacent highlight rows must have a visible gap",
                highlightedPixelsOnBoundary == 0
            )

            val firstSegmentLeft = layout.getPrimaryHorizontal(highlightStart)
            val outerLeft = (
                view.textView.totalPaddingLeft + firstSegmentLeft - 3f * density
                ).roundToInt().coerceIn(0, width - 1)
            val top = (
                view.textView.totalPaddingTop + layout.getLineTop(firstLine) + 1.5f * density
                ).roundToInt().coerceIn(0, height - 1)
            assertFalse(
                "the outer corner must remain rounded instead of square",
                isHighlightPixel(bitmap.getPixel(outerLeft, top), highlightColor)
            )

            val prefixRight = (outerLeft - 1).coerceAtLeast(view.textView.totalPaddingLeft)
            val middleY = view.textView.totalPaddingTop +
                (layout.getLineTop(firstLine) + layout.getLineBottom(firstLine)) / 2
            val highlightedPrefixPixels =
                (view.textView.totalPaddingLeft until prefixRight).count { x ->
                    isHighlightPixel(bitmap.getPixel(x, middleY), highlightColor)
                }
            assertTrue(
                "text before the saved range must not be highlighted",
                highlightedPrefixPixels == 0
            )

        }
    }

    @Test
    fun roundedHighlightIsCenteredOnFontMetricsInSpaciousLine() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val highlightColor = 0xFFFF5F64.toInt()
            val value = SpannableString("highlight alignment").apply {
                setSpan(
                    ReaderHighlightSpan(highlightColor),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val view = RoundedHighlightTextView(context).apply {
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setLineSpacing(0f, 1.8f)
                setPadding((16f * density).roundToInt(), 0, (16f * density).roundToInt(), 0)
                text = value
            }
            val width = (360f * density).roundToInt()
            val height = (100f * density).roundToInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))

            val layout = requireNotNull(view.layout)
            val sampleX = (
                view.totalPaddingLeft + layout.getPrimaryHorizontal(value.length) + 2f * density
                ).roundToInt().coerceIn(0, width - 1)
            val highlightedRows = (0 until height).filter { y ->
                isHighlightPixel(bitmap.getPixel(sampleX, y), highlightColor)
            }
            assertTrue("highlight must be visible beside the final glyph", highlightedRows.isNotEmpty())
            val actualCenter = (highlightedRows.first() + highlightedRows.last()) / 2f
            val metrics = view.paint.fontMetrics
            val expectedCenter = view.totalPaddingTop + layout.getLineBaseline(0) +
                (metrics.ascent + metrics.descent) / 2f
            assertTrue(
                "highlight center must follow the glyph center instead of the line box",
                kotlin.math.abs(actualCenter - expectedCenter) <= 1.5f * density
            )
        }
    }

    @Test
    fun singleCharacterHighlightUsesTheCharacterAdvanceWithoutHorizontalDrift() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val color = Color.RED
            val value = SpannableString("甲小贵族").apply {
                setSpan(ReaderHighlightSpan(color), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val padding = (24f * density).roundToInt()
            val view = RoundedHighlightTextView(context).apply {
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setPadding(padding, 0, padding, 0)
                text = value
            }
            val width = (360f * density).roundToInt()
            val height = (90f * density).roundToInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))

            val layout = requireNotNull(view.layout)
            val expectedCenter = view.totalPaddingLeft +
                (layout.getPrimaryHorizontal(1) + layout.getPrimaryHorizontal(2)) / 2f
            val redPixels = (0 until height).flatMap { y ->
                (0 until width).mapNotNull { x ->
                    if (Color.red(bitmap.getPixel(x, y)) > 220 &&
                        Color.green(bitmap.getPixel(x, y)) < 80 &&
                        Color.blue(bitmap.getPixel(x, y)) < 80
                    ) x else null
                }
            }
            assertTrue("single-character highlight must render visible pixels", redPixels.isNotEmpty())
            val actualCenter = (redPixels.minOrNull()!! + redPixels.maxOrNull()!!) / 2f
            assertTrue(
                "highlight center must align with the selected character advance: actual=$actualCenter expected=$expectedCenter",
                kotlin.math.abs(actualCenter - expectedCenter) <= 2f * density
            )
        }
    }

    @Test
    fun selectionBackgroundUsesJustifiedCharacterCoordinates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val selectionColor = Color.BLUE
            val value = SpannableString("甲乙丙丁戊己庚辛壬癸甲乙丙丁戊己庚辛壬癸")
            val padding = (24f * density).roundToInt()
            val view = RoundedHighlightTextView(context).apply {
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setPadding(padding, 0, padding, 0)
                readerJustificationMode = Layout.JUSTIFICATION_MODE_INTER_CHARACTER
                readerSelectionColor = selectionColor
                highlightColor = Color.TRANSPARENT
                text = value
            }
            val width = (360f * density).roundToInt()
            val height = (180f * density).roundToInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
            val editable = view.text as Spannable
            Selection.setSelection(editable, 3, 4)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))

            val layout = requireNotNull(view.layout)
            val line = layout.getLineForOffset(3)
            val geometry = ReaderLineGeometry(
                layout = layout,
                text = editable,
                justificationMode = Layout.JUSTIFICATION_MODE_INTER_CHARACTER
            )
            val expected = requireNotNull(geometry.horizontalRange(line, 3, 4))
            val y = view.totalPaddingTop +
                (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
            val selectedPixels = (0 until width).filter { x ->
                val pixel = bitmap.getPixel(x, y)
                Color.blue(pixel) > 180 && Color.red(pixel) < 100
            }
            assertTrue("custom selection background must be visible", selectedPixels.isNotEmpty())
            val actualLeft = selectedPixels.minOrNull()!!.toFloat()
            val actualRight = selectedPixels.maxOrNull()!!.toFloat()
            assertTrue("selection left must follow justified position", actualLeft >= expected.left - 4f * density)
            assertTrue("selection right must follow justified position", actualRight <= expected.right + 4f * density)
        }
    }

    @Test
    fun readerLineGeometryExpandsOnlyEligibleInterCharacterLines() {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val value = SpannableString("甲乙丙丁戊己庚辛壬癸甲乙丙丁戊己庚辛壬癸")
            val view = RoundedHighlightTextView(context).apply {
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setPadding((20f * density).roundToInt(), 0, (20f * density).roundToInt(), 0)
                readerJustificationMode = Layout.JUSTIFICATION_MODE_INTER_CHARACTER
                text = value
            }
            val width = (280f * density).roundToInt()
            val height = (240f * density).roundToInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
            val layout = requireNotNull(view.layout)
            assertTrue("fixture must wrap", layout.lineCount >= 2)
            val geometry = ReaderLineGeometry(
                layout,
                value,
                Layout.JUSTIFICATION_MODE_INTER_CHARACTER
            )
            val firstLineStart = layout.getLineStart(0)
            val firstLineEnd = readerLineContentEnd(value, firstLineStart, layout.getLineEnd(0))
            val firstRange = requireNotNull(geometry.horizontalRange(0, firstLineStart, firstLineEnd))
            val targetRight = layout.getParagraphRight(0).toFloat()
            assertTrue(
                "the justified first line must reach the paragraph edge: " +
                    "right=${firstRange.right} target=$targetRight",
                firstRange.right >= targetRight - 4f * density
            )
            var previous = firstRange.left
            for (offset in firstLineStart..firstLineEnd) {
                val position = requireNotNull(geometry.horizontalPosition(offset))
                assertTrue("positions must be monotonic at offset=$offset", position + 0.5f >= previous)
                previous = position
            }

            val lastLine = layout.lineCount - 1
            val lastStart = layout.getLineStart(lastLine)
            val lastEnd = readerLineContentEnd(value, lastStart, layout.getLineEnd(lastLine))
            if (lastEnd > lastStart) {
                assertEquals(
                    layout.getPrimaryHorizontal(lastEnd - 1),
                    requireNotNull(geometry.horizontalPosition(lastEnd - 1)),
                    1f
                )
            }
        }
    }

    /**
     * A page whose text ends mid-paragraph forces its own final line, but the
     * short paragraph-final lines earlier on that page must keep their natural
     * width instead of being stretched into unreadable letter spacing.
     */
    @Test
    fun forcedPageEndJustificationLeavesParagraphFinalLinesAtNaturalWidth() {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val value = SpannableString(
                "短句。\n" + "甲乙丙丁戊己庚辛壬癸".repeat(6)
            )
            val view = RoundedHighlightTextView(context).apply {
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setPadding((20f * density).roundToInt(), 0, (20f * density).roundToInt(), 0)
                readerJustificationMode = Layout.JUSTIFICATION_MODE_INTER_CHARACTER
                text = value
            }
            val width = (280f * density).roundToInt()
            val height = (240f * density).roundToInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
            val layout = requireNotNull(view.layout)
            assertTrue("fixture must produce a wrapped paragraph", layout.lineCount >= 3)

            // The page ends mid-paragraph, so the page's final line is forced.
            val geometry = ReaderLineGeometry(
                layout = layout,
                text = value,
                justificationMode = Layout.JUSTIFICATION_MODE_INTER_CHARACTER,
                forceLastLineJustification = true
            )

            val paragraphFinalLine = 0
            val paragraphFinalStart = layout.getLineStart(paragraphFinalLine)
            val paragraphFinalEnd = readerLineContentEnd(
                value,
                paragraphFinalStart,
                layout.getLineEnd(paragraphFinalLine)
            )
            assertTrue(
                "fixture's first line must be a paragraph-final line",
                readerLineEndsParagraph(value, paragraphFinalStart, layout.getLineEnd(paragraphFinalLine))
            )
            val paragraphFinalRange = requireNotNull(
                geometry.horizontalRange(paragraphFinalLine, paragraphFinalStart, paragraphFinalEnd)
            )
            val paragraphFinalTarget = layout.getParagraphRight(paragraphFinalLine).toFloat()
            assertTrue(
                "paragraph-final line must stay at its natural width: " +
                    "right=${paragraphFinalRange.right} target=$paragraphFinalTarget",
                paragraphFinalRange.right <= paragraphFinalTarget - 20f * density
            )

            val lastLine = layout.lineCount - 1
            val lastStart = layout.getLineStart(lastLine)
            val lastEnd = readerLineContentEnd(value, lastStart, layout.getLineEnd(lastLine))
            val lastRange = requireNotNull(geometry.horizontalRange(lastLine, lastStart, lastEnd))
            assertTrue(
                "page-final continuation line must still be justified: " +
                    "right=${lastRange.right} target=${layout.getParagraphRight(lastLine)}",
                lastRange.right >= layout.getParagraphRight(lastLine) - 4f * density
            )
        }
    }

    @Test
    fun readerLineGeometryExpandsInterWordSpacesButNotPlainLeftAlignment() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val value = SpannableString("one two three four five six seven eight nine")
            fun build(mode: Int): Pair<RoundedHighlightTextView, ReaderLineGeometry> {
                val view = RoundedHighlightTextView(context).apply {
                    includeFontPadding = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    setPadding((16f * density).roundToInt(), 0, (16f * density).roundToInt(), 0)
                    readerJustificationMode = mode
                    text = value
                }
                val width = (250f * density).roundToInt()
                val height = (220f * density).roundToInt()
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, width, height)
                return view to ReaderLineGeometry(requireNotNull(view.layout), value, mode)
            }

            val (wordView, wordGeometry) = build(Layout.JUSTIFICATION_MODE_INTER_WORD)
            val wordLayout = requireNotNull(wordView.layout)
            assertTrue("fixture must wrap", wordLayout.lineCount >= 2)
            val wordEnd = readerLineContentEnd(value, wordLayout.getLineStart(0), wordLayout.getLineEnd(0))
            val wordRange = requireNotNull(wordGeometry.horizontalRange(0, wordLayout.getLineStart(0), wordEnd))
            assertTrue(
                "inter-word line must use the available width",
                wordRange.right >= wordLayout.getParagraphRight(0) - 4f * density
            )

            val (plainView, plainGeometry) = build(Layout.JUSTIFICATION_MODE_NONE)
            val plainLayout = requireNotNull(plainView.layout)
            val plainOffset = minOf(2, value.length)
            assertEquals(
                plainLayout.getPrimaryHorizontal(plainOffset),
                requireNotNull(plainGeometry.horizontalPosition(plainOffset)),
                0.01f
            )
        }
    }

    @Test
    fun verticalHighlightMergesAdjacentGlyphsIntoOneRoundedColumn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val highlightColor = 0xFFFF5F64.toInt()
            val value = SpannableString("\u5929\u5730").apply {
                setSpan(
                    ReaderHighlightSpan(highlightColor),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val geometry = VerticalPageGeometry(
                items = listOf(
                    VerticalGlyphLayout(0, 1, VerticalRect(60f, 20f, 100f, 60f), 0, false),
                    VerticalGlyphLayout(1, 2, VerticalRect(60f, 60f, 100f, 100f), 0, false)
                ),
                width = 140f,
                height = 140f
            )
            val view = VerticalTextView(context).apply {
                configure(32f, Color.BLACK, Typeface.DEFAULT, Color.BLUE)
                setPage(value, geometry, 0)
            }
            view.measure(
                View.MeasureSpec.makeMeasureSpec(140, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(140, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 140, 140)
            val bitmap = Bitmap.createBitmap(140, 140, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))

            assertTrue(
                "adjacent highlighted glyphs must have a continuous column background",
                isHighlightPixel(
                    bitmap.getPixel((60f + 1.5f * density + 3f).roundToInt(), 60),
                    highlightColor
                )
            )
            val roundedTopY = (20f - 3f * density).roundToInt().coerceAtLeast(0)
            val insetLeft = (60f + 1.5f * density).roundToInt().coerceIn(0, 139)
            assertFalse(
                "column endpoint must keep a rounded outer corner",
                isHighlightPixel(bitmap.getPixel(insetLeft, roundedTopY), highlightColor)
            )
        }
    }

    private fun isHighlightPixel(pixel: Int, highlightColor: Int): Boolean =
        Color.red(pixel) == Color.red(highlightColor) &&
            Color.green(pixel) == Color.green(highlightColor) &&
            Color.blue(pixel) == Color.blue(highlightColor)
}
