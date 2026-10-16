package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.graphics.Typeface
import android.text.SpannedString
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownNavigationTest {
    @Test fun latestJumpSurvivesInitialRestoreReplacementAndRebuild() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val doc = MarkdownNativeParser.parse((1..1600).joinToString("\n\n") {
            "## Heading $it\n\n" + "Body $it to scroll. ".repeat(18)
        })
        val source = object : MarkdownBlockSource {
            override val document = doc
            override suspend fun renderBlock(index: Int, style: MarkdownRenderStyle) = SpannedString(doc.blocks[index].text)
            override fun close() = Unit
        }
        val surface = MarkdownNativeSurface(activity.get(), source)
        val results = mutableListOf<EpubNavigationResult>()
        var offset = -1
        surface.failed = { throw AssertionError(it) }
        surface.navigationFinished = { results += it }
        surface.pageChanged = { _, _, json -> offset = org.json.JSONObject(json).getInt("textPosition") }
        fun layout() {
            surface.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            surface.layout(0, 0, 600, 900)
        }
        fun frame() { ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS); layout(); Thread.yield() }
        fun jump(id: Long, heading: Int) = surface.navigate(EpubNavigationRequest(id,
            EpubNavigationOrigin.TOC, 0, 0, 0, EpubNavigationDestination.Fragment(doc.outline[heading].anchor), false))
        fun verify(id: Long, heading: Int) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (results.none { it.operationId == id } && System.nanoTime() < deadline) frame()
            assertTrue("jump $id must commit: $results", results.any { it.operationId == id })
            repeat(15) { frame() }
            val list = surface.getChildAt(0) as RecyclerView
            val manager = list.layoutManager as LinearLayoutManager
            val target = doc.index.blockAt(doc.outline[heading].offset)
            assertNotNull("jump $id: heading $heading block $target, visible ${manager.findFirstVisibleItemPosition()}..${manager.findLastVisibleItemPosition()}, offset $offset", manager.findViewByPosition(target))
            assertEquals("jump $id must retain the requested document coordinate", doc.outline[heading].offset, offset)
        }
        try {
            surface.configure(ReaderUiState(), Typeface.DEFAULT, android.graphics.Color.BLACK, false, true)
            layout() // Queue offset-zero restoration before attachment.
            activity.get().setContentView(surface, ViewGroup.LayoutParams(600, 900))
            jump(1, 1200); verify(1, 1200)
            jump(2, 50); jump(3, 800); verify(3, 800)
            assertFalse(results.any { it.operationId == 2L })
            jump(4, 800); verify(4, 800)
            surface.configure(ReaderUiState(marginLeftDp = 32f), Typeface.DEFAULT, android.graphics.Color.BLACK, false, true)
            jump(5, 1400); verify(5, 1400)
            jump(6, doc.outline.lastIndex); verify(6, doc.outline.lastIndex)
        } finally {
            surface.release(); activity.pause().stop().destroy()
        }
    }
}
