package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.applyReaderTextAlignment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pure-text chapters, paginated and rendered the same way as the paged reader:
 * no rendered page may end below the configured bottom margin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPageBottomGapTest {
    private val paragraphs = listOf(
        "　　夜色渐深，城市的灯火一盏接一盏地熄灭。他站在窗前，望着远处模糊的山影，心里反复想着白天那封信。“你真的决定了吗？”她问。他没有回答，只是点了点头。",
        "　　第二天清晨，雨停了。街道上积着一洼一洼的水，倒映着灰白的天空；行人匆匆，没有人注意到角落里那个背着旧书包的少年——他已经在这里等了整整三个小时。",
        "　　“我们走吧。”",
        "　　火车缓缓驶出站台，窗外的田野、村庄、河流依次后退。他想起很多年前，父亲也是这样送他离开的：一句话也没说，只是把一袋橘子塞进他的手里，然后转身走进人群。",
        "　　有些事情，过去了就再也回不来；有些人，错过了就是一辈子。可是，谁又能真正分得清，哪些是该坚持的，哪些是该放下的呢？",
    )

    @Test
    fun renderedPagesNeverCrossTheBottomMargin() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val raw = buildString { repeat(30) { i -> append(paragraphs[i % paragraphs.size]).append('\n') } }
        val report = StringBuilder()
        var worst = Int.MAX_VALUE
        val serif = readerSerifTypeface(context, 400)
        val serifBold = readerSerifTypeface(context, 700)
        for (alignment in listOf(ReaderTextAlignment.NATURAL, ReaderTextAlignment.JUSTIFY)) {
            for ((fontSp, lineHeight) in listOf(18f to 1.5f, 20f to 1.8f, 16f to 1.3f, 24f to 1.0f, 26f to 2.2f, 14f to 2.0f)) {
                for ((face, letterDp) in listOf(Typeface.DEFAULT to 0f, serif to 0f, serifBold to 1f, Typeface.DEFAULT to 2f)) {
                for (paragraphDp in listOf(0f, 8f, 16f)) {
                    for (aligned in listOf(false)) {
                        val fontPx = fontSp * density
                        val spacingPx = 2.5f * density
                        val chapter = applyReaderPunctuationCompression(applyReaderTextAlignment(
                            ReaderParagraphFormatter.applyFirstLineIndent(raw, 2f, fontPx, paragraphDp * density, false),
                            alignment))
                        val margins = resolveReaderVerticalMargins(height, 64f * density, 64f * density,
                            fontPx, lineHeight, spacingPx, face, bottomAligned = aligned)
                        val view = PageContentView(context)
                        view.configure(fontPx, Color.BLACK, lineHeightMult = lineHeight, lineSpacingExtraPx = spacingPx,
                            letterSpacingPx = letterDp * density, typeface = face,
                            marginLeftPx = 24f * density, marginRightPx = 24f * density,
                            marginTopPx = margins.topPx.toFloat(), marginBottomPx = margins.bottomPx.toFloat(),
                            textAlignment = alignment, bottomAligned = aligned)
                        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                        view.layout(0, 0, width, height)
                        val engine = PageLayoutEngine().apply {
                            configure(width, height, fontPx, lineSpacingPx = spacingPx, lineSpacingMult = lineHeight,
                                letterSpacingPx = letterDp * density, typeface = face, marginLeftPx = 24f * density, marginRightPx = 24f * density,
                                marginTopPx = margins.topPx.toFloat(), marginBottomPx = margins.bottomPx.toFloat(),
                                chapterCount = 1, textAlignment = alignment)
                            sharedTextPaint = view.textView.paint
                        }
                        val pages = engine.layout(0, chapter).pages
                        val gaps = mutableListOf<Int>()
                        for (page in pages.dropLast(1)) {
                            view.setPageContent(chapter, page.startCharOffset, page.endCharOffset)
                            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                            view.layout(0, 0, width, height)
                            val layout = view.textView.layout
                            val text = view.textView.text
                            var last = layout.lineCount - 1
                            while (last > 0 && text.subSequence(layout.getLineStart(last), layout.getLineEnd(last)).isBlank()) last--
                            val ink = view.textView.paddingTop + layout.getLineBaseline(last) +
                                view.textView.paint.fontMetricsInt.descent
                            gaps += height - ink
                        }
                        val minGap = gaps.minOrNull() ?: continue
                        worst = minOf(worst, minGap)
                        if (gaps.min() < 40f * density) report.append("!! ")
                        report.append("$alignment ${fontSp}sp x$lineHeight face=${if (face === Typeface.DEFAULT) "sans" else if (face === serif) "serif" else "serifBold"} ls=$letterDp para=${paragraphDp}dp aligned=$aligned " +
                            "margins=${margins.topPx}/${margins.bottomPx}px gaps(dp)=" +
                            "${(gaps.min() / density).toInt()}..${(gaps.max() / density).toInt()} " +
                            "pages=${pages.size}\n")
                    }
                }
                }
            }
        }
        println("BOTTOM-GAP REPORT\n$report")
        assertTrue("A page ends ${worst / density}dp above the screen bottom (margin is 64dp):\n$report",
            worst >= (64f * density * 0.5f).toInt())
    }
}
