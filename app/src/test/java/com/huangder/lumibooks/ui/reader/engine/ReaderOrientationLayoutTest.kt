package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderOrientationLayoutTest {
    private val text = "横竖屏切换时保留当前字符位置，缓存真正的章节排版。".repeat(160)
    private fun configure(engine: PageLayoutEngine, width: Int, height: Int, size: Float = 24f) =
        engine.configure(width, height, size, marginLeftPx = 20f, marginRightPx = 20f,
            marginTopPx = 20f, marginBottomPx = 20f, chapterCount = 12)

    @Test fun preparedOrientationIsAdoptedWithoutBuildingAgainAndBothDirectionsRemainReusable() = runBlocking {
        val engine = PageLayoutEngine()
        configure(engine, 400, 700)
        val portrait = engine.layout(0, text)
        val prepared = engine.forViewport(700, 400, 20f, 20f, 20f)
        val landscape = prepared.layout(0, text)
        engine.retainPrepared(prepared, engine.bookPages.value.generation)
        val builds = engine.layoutBuildCount
        configure(engine, 700, 400)
        assertSame(landscape, engine.layout(0, text))
        assertEquals(builds, engine.layoutBuildCount)
        val anchor = portrait.pages[2].startCharOffset
        val page = engine.bookPages.value.pageAtCharacter(0, anchor)!!
        assertTrue(anchor in landscape.pages[page].startCharOffset until landscape.pages[page].endCharOffset)
        configure(engine, 400, 700)
        assertSame(portrait, engine.layout(0, text))
        assertEquals(builds, engine.layoutBuildCount)
    }

    @Test fun fontOrContentChangeCannotAdoptAnOldPreparedResult() = runBlocking {
        val engine = PageLayoutEngine()
        configure(engine, 400, 700)
        engine.layout(0, text)
        val generation = engine.bookPages.value.generation
        val old = engine.forViewport(700, 400, 20f, 20f, 20f)
        val stale = old.layout(0, text)
        engine.setPaginationIdentity("new book revision")
        engine.retainPrepared(old, generation)
        configure(engine, 700, 400, 30f)
        assertNull(engine.getChapterLayout(0))
        assertNotSame(stale, engine.layout(0, text))
    }

    @Test fun exactViewportMismatchFallsBackAndTrimReleasesOnlyAlternate() = runBlocking {
        val engine = PageLayoutEngine()
        configure(engine, 400, 700)
        val portrait = engine.layout(0, text)
        val other = engine.forViewport(700, 400, 20f, 20f, 20f)
        other.layout(0, text)
        engine.retainPrepared(other, engine.bookPages.value.generation)
        engine.trimAlternate()
        assertSame(portrait, engine.getChapterLayout(0))
        configure(engine, 699, 400)
        assertNull(engine.getChapterLayout(0))
    }
}
