package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Spanned
import com.huangder.lumibooks.ui.reader.engine.ReaderHighlightSpan
import com.huangder.lumibooks.ui.reader.engine.WaveUnderlineSpan
import com.huangder.lumibooks.ui.reader.engine.RoundedHighlightTextView
import com.huangder.lumibooks.domain.model.RuleStyle
import com.huangder.lumibooks.highlight.RuleStyleJson
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.util.markdown.native.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownRenderingRegressionTest {
    @Test fun continuousHeightEstimateAccountsForFullWidthCjkGlyphs() {
        val cjk = MarkdownNativeParser.parse("中".repeat(120)).blocks.single()
        val latin = MarkdownNativeParser.parse("a".repeat(120)).blocks.single()
        val cjkHeight = estimateMarkdownBlockHeight(cjk, 300, 20f, 1.2f, 12)
        val latinHeight = estimateMarkdownBlockHeight(latin, 300, 20f, 1.2f, 12)
        assertTrue("CJK lines must not be estimated at half their rendered width", cjkHeight > latinHeight)
        assertTrue("a long CJK paragraph should reserve roughly eight lines", cjkHeight >= 180)
    }

    private fun textViews(view: View): List<TextView> = if (view is TextView) listOf(view)
        else if (view is ViewGroup) (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else emptyList()

    private fun withSurface(markdown: String, state: ReaderUiState = ReaderUiState(),
        check: (MarkdownNativeSurface, List<TextView>) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val source = MarkwonBlockSource(activity.get(), MarkdownNativeParser.parse(markdown), emptyMap())
        val surface = MarkdownNativeSurface(activity.get(), source)
        var ready = false
        surface.failed = { throw AssertionError(it) }
        surface.pageChanged = { _, _, _ -> ready = true }
        try {
            activity.get().setContentView(surface, ViewGroup.LayoutParams(600, 900))
            surface.configure(state, Typeface.DEFAULT, Color.BLACK, false, true)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!ready && System.nanoTime() < deadline) {
                surface.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
                surface.layout(0, 0, 600, 900)
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                Thread.yield()
            }
            assertTrue("native viewport must finish restoring", ready)
            check(surface, textViews(surface))
        } finally {
            surface.release(); source.close(); activity.pause().stop().destroy()
        }
    }

    @Test fun leftCenterAndRightAlignTheActualSelectableText() {
        for (alignment in listOf(ReaderTextAlignment.LEFT, ReaderTextAlignment.CENTER, ReaderTextAlignment.RIGHT)) {
            withSurface("Short text", ReaderUiState(textAlignment = alignment, firstLineIndent = 0f)) { _, views ->
                val view = views.single()
                val layout = view.layout
                when (alignment) {
                    ReaderTextAlignment.LEFT -> assertEquals(0f, layout.getLineLeft(0), 1f)
                    ReaderTextAlignment.CENTER -> assertEquals(layout.width / 2f,
                        (layout.getLineLeft(0) + layout.getLineRight(0)) / 2f, 1f)
                    else -> assertEquals(layout.width.toFloat(), layout.getLineRight(0), 1f)
                }
            }
        }
    }

    @Test fun naturalAndExplicitJustificationUseBothEdges() {
        for (alignment in listOf(ReaderTextAlignment.NATURAL, ReaderTextAlignment.JUSTIFY)) {
            withSurface("Words with variable lengths fill the reading column. ".repeat(10),
                ReaderUiState(textAlignment = alignment)) { _, views ->
                val view = views.single()
                assertTrue(view.layout.lineCount > 1)
                assertEquals(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD, view.justificationMode)
                assertEquals(view.layout.width.toFloat(), view.layout.getLineRight(0), 1f)
            }
        }
    }

    @Test fun headingPaddingFallbackFontsAndDescendersFitInsideTheirHolders() {
        withSurface("# 标题 Heading gy\n\n普通文字 gyjp\n第二行内容\n\n### Another 标题\n\n````\ngyjp 中文\n````",
            ReaderUiState(lineHeight = 1.1f, paragraphSpacing = 0f)) { _, views ->
            assertTrue(views.size >= 4)
            for (view in views) {
                val needed = view.layout.height + view.totalPaddingTop + view.totalPaddingBottom
                assertTrue("${view.text}: needs $needed, view ${view.height}", view.height >= needed)
                val holder = view.parent as View
                assertTrue("holder must not clip ${view.text}", holder.height >= view.height)
            }
        }
    }

    @Test fun newlyAddedHighlightsAndUnderlinesAppearInTheMountedViewport() {
        withSurface("Marked text and ordinary text") { surface, views ->
            fun annotation(type: String) = Note(bookId = "md", chapterIndex = 0, startPosition = 0,
                endPosition = 6, selectedText = "Marked", note = "", color = "#FFCA28", createdAt = 0, type = type)
            surface.setNotes(listOf(annotation("highlight"), annotation("underline")))
            val text = views.single().text as Spanned
            val highlight = text.getSpans(0, 6, ReaderHighlightSpan::class.java).single()
            val underline = text.getSpans(0, 6, WaveUnderlineSpan::class.java).single()
            assertEquals(0, text.getSpanStart(highlight)); assertEquals(6, text.getSpanEnd(highlight))
            assertEquals(0, text.getSpanStart(underline)); assertEquals(6, text.getSpanEnd(underline))
            surface.setNotes(emptyList())
            assertTrue(text.getSpans(0, 6, ReaderHighlightSpan::class.java).isEmpty())
            assertTrue(text.getSpans(0, 6, WaveUnderlineSpan::class.java).isEmpty())
        }
    }

    private fun bitmap(view: View): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        .also { view.draw(Canvas(it)) }

    @Test fun changingSavedColorsAndUnderlineStylesRepaintsWithoutRecreatingTextViews() {
        withSurface("Marked text and ordinary text") { surface, views ->
            val view = views.single()
            for (type in listOf("highlight", "underline")) {
                val note = Note(bookId = "md", chapterIndex = 0, startPosition = 0, endPosition = 6,
                    selectedText = "Marked", note = "", color = "#FFFF0000", createdAt = 0, type = type)
                surface.setNotes(listOf(note))
                val red = bitmap(view)
                surface.setNotes(listOf(note.copy(color = "#FF0000FF")))
                val blue = bitmap(view)
                assertFalse("$type must visibly change color", red.sameAs(blue))
                val text = view.text as Spanned
                if (type == "underline") {
                    assertEquals(Color.BLUE, text.getSpans(0, 6, WaveUnderlineSpan::class.java).single().color)
                    for (mode in 1..4) {
                        val style = RuleStyle(null, mode, 2f, 1f, 400, false)
                        surface.setNotes(listOf(note.copy(styleSnapshotJson = RuleStyleJson.encode(style))))
                        assertEquals(mode, text.getSpans(0, 6, WaveUnderlineSpan::class.java).single().mode)
                    }
                } else assertEquals(Color.BLUE, text.getSpans(0, 6, ReaderHighlightSpan::class.java).single().color)
            }
        }
    }

    @Test fun markdownAndNativeEpubDrawTheSameHighlightAndFourUnderlineStyles() {
        val context = Robolectric.buildActivity(Activity::class.java).setup().get()
        fun render(view: TextView, type: String, mode: Int): Bitmap {
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 24f)
            view.setTextColor(Color.TRANSPARENT)
            view.includeFontPadding = false
            view.setLineSpacing(0f, 1.5f)
            val text = android.text.SpannableString("Shared annotation style")
            applyMarkdownAnnotationSpans(text, 0, listOf(Note(bookId = "md", chapterIndex = 0,
                startPosition = 1, endPosition = text.length - 1, selectedText = text.toString(), note = "",
                color = "#80FF0000", createdAt = 0, type = type,
                styleSnapshotJson = RuleStyleJson.encode(RuleStyle(null, mode, 2f, 1f, 400, false)))))
            view.setText(text, TextView.BufferType.SPANNABLE)
            view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(150, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 600, 150)
            return bitmap(view)
        }
        for (type in listOf("highlight", "underline")) for (mode in 1..4) {
            val markdown = render(MarkdownAnnotationTextView(context), type, mode)
            val epub = render(RoundedHighlightTextView(context), type, mode)
            assertTrue("$type mode $mode must match EPUB pixels", markdown.sameAs(epub))
            val pixels = IntArray(markdown.width * markdown.height)
            markdown.getPixels(pixels, 0, markdown.width, 0, 0, markdown.width, markdown.height)
            assertTrue("annotation must actually be drawn", pixels.any { Color.alpha(it) > 0 })
        }
    }

    @Test fun resolvedPaletteColorsAreNotRemappedWhenSlotsExchangeColors() {
        val palette = com.huangder.lumibooks.domain.model.HighlightPalette(id = "swapped", name = "Swapped",
            colors = listOf("#CFA09A", "#D6C58D", "#A7B59D", "#9DAFC1", "#B2A198", "#AFB0AC"))
        try {
            updateHighlightPalettes(listOf(palette), palette.id)
            withSurface("Marked text") { surface, views ->
                for (type in listOf("highlight", "underline")) {
                    val resolved = resolveReaderHighlightColor(readerHighlightColorReference(0, type))
                    surface.setNotes(listOf(Note(bookId = "md", chapterIndex = 0, startPosition = 0,
                        endPosition = 6, selectedText = "Marked", note = "", color = resolved,
                        createdAt = 0, type = type)))
                    val text = views.single().text as Spanned
                    val actual = if (type == "underline") text.getSpans(0, 6, WaveUnderlineSpan::class.java).single().color
                        else text.getSpans(0, 6, ReaderHighlightSpan::class.java).single().color
                    assertEquals(Color.parseColor(resolved), actual)
                }
            }
        } finally {
            updateHighlightPalettes(emptyList(), null)
        }
    }

    @Test fun underlineOnTheLastLineOfEveryBlockIsVisibleInAllFourStyles() {
        withSurface("Single line") { surface, views ->
            val view = views.single()
            for (mode in 1..4) {
                surface.setNotes(listOf(Note(bookId = "md", chapterIndex = 0, startPosition = 0,
                    endPosition = 6, selectedText = "Single", note = "", color = "#FFFF0000",
                    createdAt = 0, type = "underline",
                    styleSnapshotJson = RuleStyleJson.encode(RuleStyle(null, mode, 2f, 1f, 400, false)))))
                val image = bitmap(view)
                var redPixels = 0
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val color = image.getPixel(x, y)
                    if (Color.red(color) > 150 && Color.green(color) < 80 && Color.blue(color) < 80) redPixels++
                }
                assertTrue("last-line underline mode $mode must not be clipped", redPixels > 5)
            }
        }
    }

    @Test fun fullWidthRuleHighlightKeepsBothRoundedCornersInsideItsView() {
        withSurface("***", ReaderUiState(firstLineIndent = 0f)) { surface, views ->
            val view = views.single()
            surface.setNotes(listOf(Note(bookId = "md", chapterIndex = 0, startPosition = 0,
                endPosition = 1, selectedText = view.text.toString(), note = "", color = "#FFFF0000", createdAt = 0)))
            val image = bitmap(view)
            val middle = image.width / 2
            val top = (0 until image.height).first { Color.red(image.getPixel(middle, it)) > 200 &&
                Color.alpha(image.getPixel(middle, it)) > 200 }
            assertTrue("left corner must curve instead of being cut at the view edge", Color.alpha(image.getPixel(0, top)) < 100)
            assertTrue("right corner must curve instead of being cut at the view edge", Color.alpha(image.getPixel(image.width - 1, top)) < 100)
        }
    }

    @Test fun tableCellsKeepTheirDocumentStylesAndFitTheirMeasuredRows() {
        withSurface("| First | Second |\n|---|---|\n| **Bold** | " + "中文 gyjp ".repeat(12) + " |") { _, views ->
            val bold = views.single { it.text.toString() == "Bold" }
            assertTrue((bold.text as Spanned).getSpans(0, 4, MetricAffectingSpan::class.java).isNotEmpty())
            for (view in views) {
                assertTrue("table cell ${view.text} must fit", view.height >=
                    view.layout.height + view.totalPaddingTop + view.totalPaddingBottom)
            }
        }
    }
}
