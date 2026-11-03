package com.huangder.lumibooks.ui.reader

import android.app.Application
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.ui.reader.engine.PageLayoutEngine
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
class ReflowTtsPageSourceTest {
    @Test
    fun verticalListeningPagesExposeTheNextPageInReadingOrder() = runBlocking {
        val text = "天地玄黃宇宙洪荒。".repeat(240)
        val layoutEngine = PageLayoutEngine().apply {
            configure(
                width = 240,
                height = 300,
                fontSizePx = 24f,
                lineSpacingPx = 0f,
                marginLeftPx = 0f,
                marginRightPx = 0f,
                marginTopPx = 0f,
                marginBottomPx = 0f,
                chapterCount = 1,
                writingMode = ReaderWritingMode.VERTICAL_RL
            )
        }
        val source = ReflowTtsPageSource(
            layoutEngine = layoutEngine,
            chapterCount = 1,
            chineseMode = "original",
            chapterProvider = { text }
        )

        try {
            val first = source.getPage(0, 0)!!
            val next = first.next!!
            val second = source.getPage(next.chapterIndex, next.pageIndex)!!

            assertTrue(layoutEngine.layout(0, text).totalPages > 1)
            assertEquals(0, first.location.pageIndex)
            assertEquals(1, second.location.pageIndex)
            assertEquals(first.endCharacterOffset, second.startCharacterOffset)
            assertTrue(second.endCharacterOffset > second.startCharacterOffset)
        } finally {
            source.close()
        }
    }
}
