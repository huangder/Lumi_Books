package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.text.TextPaint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderCompressedSpacingTest {
    private val text = "天地玄黄宇宙洪荒日月盈昃辰宿列张".repeat(40)

    @Test fun compressedHorizontalSpacingFitsMoreTextWithoutLosingCharacters() = runBlocking {
        suspend fun layout(lineHeight: Float, letterSpacing: Float) = PageLayoutEngine().apply {
            configure(320, 480, 32f, lineSpacingPx = 0f,
                lineSpacingMult = lineHeight, letterSpacingPx = letterSpacing,
                marginLeftPx = 0f, marginRightPx = 0f, marginTopPx = 0f, marginBottomPx = 0f)
        }.layout(0, text)

        val normal = layout(1f, 0f)
        val narrow = layout(1f, -5f)
        val compressed = layout(0.5f, -5f)

        assertTrue(narrow.staticLayout!!.lineCount < normal.staticLayout!!.lineCount)
        assertTrue(compressed.pages.size < narrow.pages.size)
        assertEquals(text, compressed.pages.joinToString("") {
            text.substring(it.startCharOffset, it.endCharOffset)
        })
    }

    @Test fun compressedVerticalSpacingFitsMoreColumnsWithoutLosingCharacters() {
        fun layout(lineHeight: Float) = VerticalTextLayouter.layout(
            text, TextPaint().apply { textSize = 32f; density = 1f },
            320, 480, 0f, lineHeight, -5f)

        val normal = layout(1f)
        val compressed = layout(0.5f)

        assertTrue(compressed.first().geometry.glyphs.maxOf { it.columnIndex } >
            normal.first().geometry.glyphs.maxOf { it.columnIndex })
        assertTrue(compressed.size < normal.size)
        assertEquals(text, compressed.joinToString("") { text.substring(it.startOffset, it.endOffset) })
    }
}
