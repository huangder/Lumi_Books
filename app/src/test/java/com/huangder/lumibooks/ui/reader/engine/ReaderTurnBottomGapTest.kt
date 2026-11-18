package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.os.Looper
import android.view.View
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.applyReaderTextAlignment
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Real ReadView slots and page turns: no visible page may run into the bottom margin. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderTurnBottomGapTest {
    private val paragraphs = listOf(
        "　　夜色渐深，城市的灯火一盏接一盏地熄灭。他站在窗前，望着远处模糊的山影，心里反复想着白天那封信。“你真的决定了吗？”她问。他没有回答，只是点了点头。",
        "　　第二天清晨，雨停了。街道上积着一洼一洼的水，倒映着灰白的天空；行人匆匆，没有人注意到角落里那个背着旧书包的少年——他已经在这里等了整整三个小时。",
        "　　“我们走吧。”",
        "　　火车缓缓驶出站台，窗外的田野、村庄、河流依次后退。他想起很多年前，父亲也是这样送他离开的：一句话也没说，只是把一袋橘子塞进他的手里，然后转身走进人群。",
        "　　有些事情，过去了就再也回不来；有些人，错过了就是一辈子。可是，谁又能真正分得清，哪些是该坚持的，哪些是该放下的呢？",
    )

    @Test
    fun turningPagesKeepsTheBottomMargin() {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val raw = buildString { repeat(60) { i -> append(paragraphs[i % paragraphs.size]).append('\n') } }
        val report = StringBuilder()
        var worst = Int.MAX_VALUE
        for (transition in listOf("none", "slide")) for (paragraphDp in listOf(0f, 8f)) {
            val fontPx = 18f * density
            val chapter = applyReaderPunctuationCompression(applyReaderTextAlignment(
                ReaderParagraphFormatter.applyFirstLineIndent(raw, 2f, fontPx, paragraphDp * density, false),
                ReaderTextAlignment.JUSTIFY))
            val reader = ReadView(context)
            reader.setContentProvider { chapter }
            reader.slotManager.backgroundPreparationEnabled = true
            reader.setPageTransition(transition)
            fun layout() {
                reader.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                reader.layout(0, 0, width, height)
            }
            try {
                reader.configure(fontSizePx = fontPx, theme = "day", chapterCount = 1,
                    startChapter = 0, startPage = 0, lineHeightMult = 1.5f,
                    textAlignment = ReaderTextAlignment.JUSTIFY,
                    marginLeftDp = 24f, marginRightDp = 24f, marginTopDp = 64f, marginBottomDp = 64f,
                    paragraphSpacingDp = paragraphDp, firstLineIndent = 2f,
                    width = width, height = height)
                layout()
                val gaps = mutableListOf<String>()
                for (turn in 0 until 12) {
                    awaitPage(reader)
                    layout()
                    val slot = reader.slotManager.getCurSlot()
                    val view = slot.contentView
                    val tv = view.textView
                    val lay = tv.layout ?: error("no layout on turn $turn")
                    val text = tv.text
                    var last = lay.lineCount - 1
                    while (last > 0 && text.subSequence(lay.getLineStart(last), lay.getLineEnd(last)).isBlank()) last--
                    val pageHeight = (view.parent as? View)?.height ?: view.height
                    val ink = tv.top + tv.paddingTop + lay.getLineBaseline(last) + tv.paint.fontMetricsInt.descent
                    val gap = pageHeight - ink
                    val isChapterEnd = slot.pageIndex == (reader.layoutEngine.getChapterLayout(0)?.totalPages ?: 0) - 1
                    if (!isChapterEnd) worst = minOf(worst, gap)
                    gaps += "p${slot.pageIndex}:${(gap / density).toInt()}dp(pad=${tv.paddingTop}/${tv.paddingBottom},h=${tv.height}/${pageHeight},lines=${lay.lineCount})"
                    val deadline = System.nanoTime() + 10_000_000_000L
                    while (!reader.slotManager.getNextSlot().isLoaded && System.nanoTime() < deadline) {
                        shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
                    }
                    if (!reader.slotManager.getNextSlot().isLoaded) break
                    reader.slotManager.shiftForward()
                    shadowOf(Looper.getMainLooper()).idle()
                }
                report.append("$transition para=${paragraphDp}dp: ${gaps.joinToString(" ")}\n")
            } finally {
                reader.slotManager.destroy()
            }
        }
        println("TURN-GAP REPORT\n$report")
        assertTrue("Bottom gap fell to ${worst / density}dp (margin 64dp):\n$report",
            worst >= (32f * density).toInt())
    }

    private fun awaitPage(reader: ReadView) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!reader.slotManager.getCurSlot().isLoaded && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Current page did not finish loading", reader.slotManager.getCurSlot().isLoaded)
    }
}
