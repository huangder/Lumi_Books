package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannedString
import android.view.View
import android.view.ViewGroup
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import com.huangder.lumibooks.util.markdown.native.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownPageIndexRestoreTest {
    @Test fun completeIndexSkipsDocumentScanAndSurvivesColorRefresh() = verifyRestore(partial = false)
    @Test fun partialIndexScansOnlyTheUnconfirmedSuffix() = verifyRestore(partial = true)

    private fun verifyRestore(partial: Boolean) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val context = activity.get()
        val input = (1..120).joinToString("\n\n") { "## Heading $it\n\n" + "Body $it flows across pages. ".repeat(30) }
        val doc = MarkdownNativeParser.parse(input)
        val requestedBlocks = ConcurrentHashMap.newKeySet<Int>()
        val source = object : MarkdownBlockSource {
            override val document = doc
            override suspend fun renderBlock(index: Int, style: MarkdownRenderStyle): SpannedString {
                requestedBlocks += index
                return SpannedString(doc.blocks[index].text)
            }
            override fun close() = Unit
        }
        val file = File.createTempFile("markdown-pages", ".md", context.filesDir).apply { writeText(input) }
        val book = Book("markdown-pages", "Title", "", file.path, null, BookFormat.MARKDOWN, 0, 0f, createdAt = 0)
        val state = ReaderUiState(book = book, chapterCount = 1,
            textAlignment = com.huangder.lumibooks.domain.model.ReaderTextAlignment.LEFT)
        val metrics = context.resources.displayMetrics
        val render = MarkdownRenderStyle(
            600 - (state.marginLeftDp * metrics.density).toInt() - (state.marginRightDp * metrics.density).toInt(),
            900 - (state.marginTopDp * metrics.density).toInt() - (state.marginBottomDp * metrics.density).toInt(),
            state.fontSize * metrics.scaledDensity, Color.BLACK, false,
            (state.paragraphSpacing * metrics.density).toInt(), state.firstLineIndent)
        val style = MarkdownPageStyle(render, Typeface.DEFAULT, state.lineHeight,
            state.letterSpacing * metrics.density / render.fontSize, Layout.Alignment.ALIGN_NORMAL)
        val signature = markdownPaginationSignature(state.paginationContentKey() + "|" +
            readerPaginationFontKey(context, state.fontType, state.customFontPath, state.bodyFontWeight),
            style, metrics.density, metrics.scaledDensity)
        val starts = runBlocking {
            val provider = MarkdownPageProvider(source, style)
            val result = mutableListOf<Int>()
            var at = 0
            while (at < doc.text.length) {
                result += at
                at = provider.page(at).end.also { check(it > at) }
            }
            provider.clear()
            result.toList()
        }
        assertTrue(starts.size > 20)
        val resumePage = starts.size * 2 / 3
        val resumeBlock = doc.index.blockAt(starts[resumePage])
        ReaderCacheStore.get(context).clear()
        runBlocking {
            val cache = ReaderPageIndexCache.open(context, book, signature, 1)
            cache.submit(if (partial) ReaderBookPages(chapterCounts = listOf(null),
                chapterStarts = mapOf(0 to starts.take(resumePage + 1))) else
                ReaderBookPages(chapterCounts = listOf(starts.size), chapterStarts = mapOf(0 to starts)))
            cache.close(); cache.awaitWrites()
        }
        requestedBlocks.clear()
        val surface = MarkdownNativeSurface(context, source)
        var restored = emptyList<Int>()
        val errors = mutableListOf<String>()
        surface.pageIndexChanged = { restored = it }
        surface.failed = { errors += it }
        fun frame() {
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
            surface.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            surface.layout(0, 0, 600, 900)
            Thread.sleep(5)
        }
        try {
            surface.configure(state, Typeface.DEFAULT, Color.BLACK, false, true)
            context.setContentView(surface, ViewGroup.LayoutParams(600, 900))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (restored.isEmpty() && errors.isEmpty() && System.nanoTime() < deadline) frame()
            assertTrue(errors.toString(), errors.isEmpty())
            assertEquals(starts, restored)
            // Foreground block rendering is expected, but the already confirmed middle
            // of the document must never be measured by the statistics job again.
            assertFalse(requestedBlocks.any { it in 20 until resumeBlock - 1 })
            if (partial) assertTrue(requestedBlocks.any { it >= resumeBlock })
            else assertFalse(requestedBlocks.any { it >= resumeBlock })
            surface.configure(state.copy(readerTextColor = Color.GRAY), Typeface.DEFAULT, Color.GRAY, true, true)
            repeat(12) { frame() }
            assertEquals(starts, restored)
            assertFalse(requestedBlocks.any { it in 20 until resumeBlock - 1 })
        } finally {
            surface.release(); activity.pause().stop().destroy()
            file.delete(); ReaderCacheStore.get(context).clear()
        }
    }
}
