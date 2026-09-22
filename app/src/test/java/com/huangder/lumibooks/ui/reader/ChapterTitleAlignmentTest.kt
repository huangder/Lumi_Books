package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.engine.ReaderLineGeometry
import com.huangder.lumibooks.ui.reader.engine.applyReaderPunctuationCompression
import com.huangder.lumibooks.util.ChineseConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 章首标题段落固定左对齐：不管用户选哪种对齐模式，标题都不跟随居中/右对齐，
 * 也不参与两端对齐拉伸；正文段落仍按用户设置排版。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChapterTitleAlignmentTest {

    private val title = "第5章 与时间赛跑"
    private val body = "正文，继续阅读。"

    /** 模拟 ReaderViewModel 产出的 TXT 章首标题文本：标题 + 段落换行 + 正文。 */
    private fun txtChapter(): SpannableStringBuilder =
        SpannableStringBuilder("$title\n$body\n").apply {
            setSpan(AbsoluteSizeSpan(22, true), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private val titleEnd get() = title.length + 1

    private fun chapterTitleParagraphEndOf(text: CharSequence, chapterTitle: String? = title): Int =
        chapterTitleParagraphEnd(text, chapterTitle)

    // ── 标题段落判定 ──

    @Test
    fun detectsTxtSyntheticTitleParagraph() {
        assertEquals(titleEnd, chapterTitleParagraphEndOf(txtChapter()))
    }

    @Test
    fun detectsHtmlHeadingParagraphWithoutTocTitle() {
        val heading = "8. PDF 与 MinerU"
        val text = SpannableStringBuilder("$heading\n$body\n").apply {
            setSpan(RelativeSizeSpan(1.6f), 0, heading.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, heading.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        assertEquals(heading.length + 1, chapterTitleParagraphEndOf(text, chapterTitle = null))
    }

    @Test
    fun skipsLeadingBlankAndImageOnlyParagraphs() {
        val heading = "第一章 开始"
        val prefix = "\n￼\n"
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val text = SpannableStringBuilder("$prefix$heading\n$body\n").apply {
            setSpan(
                ImageSpan(RuntimeEnvironment.getApplication(), bitmap),
                1,
                2,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                RelativeSizeSpan(1.6f),
                prefix.length,
                prefix.length + heading.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                StyleSpan(Typeface.BOLD),
                prefix.length,
                prefix.length + heading.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        assertEquals(
            prefix.length + heading.length + 1,
            chapterTitleParagraphEndOf(text, chapterTitle = null)
        )
    }

    @Test
    fun reportsNoTitleWhenChapterStartsWithBodyText() {
        assertEquals(0, chapterTitleParagraphEndOf(SpannableStringBuilder("$body\n$body\n")))
    }

    // ── 段落对齐 ──

    @Test
    fun titleParagraphStaysLeadingAlignedInEveryMode() {
        ReaderTextAlignment.entries.forEach { alignment ->
            val aligned = applyReaderTextAlignment(txtChapter(), alignment, titleEnd) as Spanned
            val spans = aligned.getSpans(0, 1, AlignmentSpan::class.java)

            assertEquals("$alignment 下标题段落应只有一个对齐 span", 1, spans.size)
            assertEquals(
                "$alignment 下标题应左对齐",
                Layout.Alignment.ALIGN_NORMAL,
                spans.single().alignment
            )
            assertEquals(0, aligned.getSpanStart(spans.single()))
            assertEquals(titleEnd, aligned.getSpanEnd(spans.single()))
        }
    }

    @Test
    fun bodyParagraphsKeepConfiguredAlignment() {
        val expected = mapOf(
            ReaderTextAlignment.LEFT to Layout.Alignment.ALIGN_NORMAL,
            ReaderTextAlignment.CENTER to Layout.Alignment.ALIGN_CENTER,
            ReaderTextAlignment.RIGHT to Layout.Alignment.ALIGN_OPPOSITE
        )
        expected.forEach { (alignment, layoutAlignment) ->
            val aligned = applyReaderTextAlignment(txtChapter(), alignment, titleEnd) as Spanned
            val spans = aligned.getSpans(titleEnd, titleEnd + 1, AlignmentSpan::class.java)

            assertEquals("$alignment 下正文段落应只有一个对齐 span", 1, spans.size)
            assertEquals(layoutAlignment, spans.single().alignment)
            assertEquals(titleEnd, aligned.getSpanStart(spans.single()))
            assertEquals(aligned.length, aligned.getSpanEnd(spans.single()))
        }

        listOf(ReaderTextAlignment.NATURAL, ReaderTextAlignment.JUSTIFY).forEach { alignment ->
            val aligned = applyReaderTextAlignment(txtChapter(), alignment, titleEnd) as Spanned
            assertTrue(
                "$alignment 不给正文挂对齐 span（两端对齐由排版模式负责）",
                aligned.getSpans(titleEnd, titleEnd + 1, AlignmentSpan::class.java).isEmpty()
            )
        }
    }

    @Test
    fun naturalAlignmentKeepsPublisherAlignmentOutsideTitle() {
        val source = txtChapter().apply {
            // 出版社把正文整段居中（标题段落本身没有对齐 span）。
            setSpan(
                AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                titleEnd,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val aligned = applyReaderTextAlignment(source, ReaderTextAlignment.NATURAL, titleEnd) as Spanned
        val bodySpans = aligned.getSpans(titleEnd, titleEnd + 1, AlignmentSpan::class.java)

        assertEquals(
            Layout.Alignment.ALIGN_NORMAL,
            aligned.getSpans(0, 1, AlignmentSpan::class.java).single().alignment
        )
        assertEquals(1, bodySpans.size)
        assertEquals(Layout.Alignment.ALIGN_CENTER, bodySpans.single().alignment)
    }

    @Test
    fun publisherCenteredTitleIsForcedBackToLeading() {
        val source = txtChapter().apply {
            setSpan(
                AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val aligned = applyReaderTextAlignment(source, ReaderTextAlignment.NATURAL, titleEnd) as Spanned

        assertEquals(
            Layout.Alignment.ALIGN_NORMAL,
            aligned.getSpans(0, 1, AlignmentSpan::class.java).single().alignment
        )
        // 覆盖到标题之外的出版社对齐要原样补回正文。
        val bodySpans = aligned.getSpans(titleEnd, titleEnd + 1, AlignmentSpan::class.java)
        assertEquals(Layout.Alignment.ALIGN_CENTER, bodySpans.single().alignment)
        assertEquals(titleEnd, aligned.getSpanStart(bodySpans.single()))
    }

    @Test
    fun alignmentWithoutTitleParagraphMatchesLegacyBehaviour() {
        val chapter = txtChapter()

        // 没有识别出标题时不做任何复制，也不改对齐。
        assertSame(chapter, applyReaderTextAlignment(chapter, ReaderTextAlignment.NATURAL))

        val aligned = applyReaderTextAlignment(chapter, ReaderTextAlignment.RIGHT) as Spanned
        listOf(0, titleEnd).forEach { offset ->
            assertEquals(
                Layout.Alignment.ALIGN_OPPOSITE,
                aligned.getSpans(offset, offset + 1, AlignmentSpan::class.java).single().alignment
            )
        }
    }

    // ── 两端对齐与 span 存活 ──

    /**
     * 用户反馈的「标题被拉伸成字距很大」来自分页把标题段落切在中间：标题行成了续行，
     * 于是被当成两端对齐的拉伸对象。标记 span 必须让标题行保持排版原生坐标。
     */
    @Test
    fun titleLineIsNotStretchedByJustification() {
        val longTitle = "第5章 " + "很长很长的章节标题".repeat(3)
        val text = SpannableStringBuilder("$longTitle\n$body$body\n").apply {
            setSpan(RelativeSizeSpan(1.6f), 0, longTitle.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, longTitle.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val titleEnd = chapterTitleParagraphEnd(text, chapterTitle = null)
        assertTrue("长标题本身就要被识别成标题段落", titleEnd > 0)

        val paint = TextPaint().apply { textSize = 48f; density = 3f }
        val marked = applyReaderTextAlignment(text, ReaderTextAlignment.JUSTIFY, titleEnd)
        val layout = StaticLayout.Builder.obtain(marked, 0, marked.length, paint, 600)
            .setIncludePad(false)
            .setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_CHARACTER)
            .build()
        assertTrue("标题应换行才能覆盖续行场景", layout.lineCount > 1)

        val geometry = ReaderLineGeometry(
            layout = layout,
            text = marked,
            justificationMode = Layout.JUSTIFICATION_MODE_INTER_CHARACTER,
            forceLastLineJustification = true
        )
        val secondCharOffset = layout.getLineStart(0) + 1
        assertEquals(
            "标题行不能额外拉伸字符间距",
            layout.getPrimaryHorizontal(secondCharOffset),
            geometry.horizontalPosition(secondCharOffset) ?: Float.NaN,
            0.01f
        )
    }

    @Test
    fun titleMarkerSurvivesReaderTransforms() {
        val aligned = applyReaderTextAlignment(txtChapter(), ReaderTextAlignment.NATURAL, titleEnd)
        val compressed = applyReaderPunctuationCompression(aligned)
        val converted = ChineseConverter.convertPreservingSpans(compressed, "simplified")

        val marked = converted as Spanned
        val markers = marked.getSpans(0, marked.length, ReaderChapterTitleSpan::class.java)

        assertEquals(1, markers.size)
        assertEquals(0, marked.getSpanStart(markers.single()))
        assertEquals(titleEnd, marked.getSpanEnd(markers.single()))
    }
}
