package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.content.Context
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.PageRenderMode
import com.huangder.lumibooks.util.epub.EpubComicArchive
import com.huangder.lumibooks.util.parser.EpubParser
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Optional local reproduction. The user's book is never copied into test fixtures or the APK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubComicBookRegressionTest {
    @Test fun realMixedChapterHasFullWidthSeamlessImagesAndOriginalResourceDecode() = runBlocking {
        val path = System.getenv("LUMI_TEST_EPUB")
        assumeTrue("Set LUMI_TEST_EPUB for local book regression", path != null && File(path).isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = EpubParser(context)
        try {
            val book = parser.parse(path!!)
            val mixed = book.chapters.take(30).firstNotNullOfOrNull { chapter ->
                val content = parser.getChapterContent(chapter.index, 1080, 0) as? Spanned
                content?.takeIf { it.getSpans(0, it.length, ImageSpan::class.java).size >= 3 &&
                    it.any { char -> !char.isWhitespace() && char != '\uFFFC' } }
            }
            assertNotNull("Book contains a mixed multi-image chapter", mixed)
            val text = wrapReaderImages(SpannableStringBuilder(mixed)) as SpannableStringBuilder
            sizeContinuousComicImages(text, 1080, 66, 88)
            protectContinuousImageHeights(text, 2f, comicMode = true)
            val reader = ContinuousSelectableTextView(context).apply {
                textSize = 18f
                setPadding(66, 0, 88, 0)
                readerImageBleed = true
                setLineSpacing(0f, 1f)
                this.text = text
                measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                layout(0, 0, 1080, measuredHeight)
            }
            assertEquals(mixed!!.toString(), reader.text.toString())
            val displayed = reader.text as Spanned
            val images = displayed.getSpans(0, displayed.length, ContinuousComicImageSpan::class.java)
                .sortedBy(displayed::getSpanStart)
            assertTrue(images.size >= 3)
            val rects = images.map { continuousImageBounds(reader.layout, it, Layout.JUSTIFICATION_MODE_NONE)!! }
            rects.forEach { rect ->
                assertEquals("left edge", 0f, rect.left + reader.totalPaddingLeft, 0.1f)
                assertEquals("right edge", 1080f, rect.right + reader.totalPaddingLeft, 0.1f)
            }
            var joins = 0
            images.zipWithNext().forEachIndexed { i, (before, after) ->
                if ((displayed.getSpanEnd(before) until displayed.getSpanStart(after)).all { displayed[it].isWhitespace() }) {
                    assertEquals("image join $i", rects[i].bottom, rects[i + 1].top, 0.1f)
                    joins++
                }
            }
            assertTrue(joins >= 2)
            val index = EpubComicArchive.index(context, path)
            assertTrue(index.pages.size > 100)
            CbzPageDecoder(EpubComicArchive.open(context, path, index)).use { decoder ->
                // Verify that the adapter selects the original ZIP resource, not a cover or thumbnail.
                for (page in listOf(0, 1, 2, index.pages.lastIndex)) {
                    val dimensions = decoder.dimensions(page)!!
                    val spec = decoder.spec(page, dimensions, 1080, PageRenderMode.NATIVE, preview = false)
                    assertEquals(1, spec.sample)
                    val pixels = decoder.decode(spec)!!
                    assertEquals(dimensions.width, pixels.width)
                    assertEquals(dimensions.height, pixels.height)
                    pixels.recycle()
                }
            }
            println("LOCAL EPUB PASS: ${index.pages.size} image occurrences; ${images.size} mixed-chapter images fill 1080 px; $joins seamless joins; original ZIP pixels decoded.")
        } finally { parser.close() }
    }
}
