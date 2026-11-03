package com.huangder.lumibooks.util.kindle

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.reader.readerFormatSupportsBookLayout
import com.huangder.lumibooks.ui.reader.readerLayoutTargetFor
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.util.epub.EpubComicArchive
import com.huangder.lumibooks.util.epub.EpubPackageReader
import com.huangder.lumibooks.util.epub.EpubRenderMode
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.huangder.lumibooks.util.parser.KindleParser
import com.huangder.lumibooks.util.parser.epubParserOrNull
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KindleReaderIntegrationTest {
    @Test fun freeReaderUsesSameCapabilitiesForEveryKindleExtension() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (format in listOf(BookFormat.MOBI, BookFormat.AZW, BookFormat.AZW3)) {
            assertTrue(BookParserFactory.createParser(format, context) is KindleParser)
            assertTrue(readerFormatSupportsBookLayout(format.name))
            assertEquals(ReaderLayoutTarget.BOOK_LAYOUT, readerLayoutTargetFor(format.name, EpubRenderMode.BOOK_LAYOUT))
        }
    }

    @Test fun userImageBookHas310PagesInOriginalOrderAndSurvivesCacheRebuild() = runBlocking {
        val source = System.getenv("LUMI_KINDLE_SAMPLE")?.let(::File)
        assumeTrue("Set LUMI_KINDLE_SAMPLE for the local user-book acceptance check", source?.isFile == true)
        assumeTrue("Set LUMI_KINDLE_NATIVE_DIR for host JNI validation", System.getenv("LUMI_KINDLE_NATIVE_DIR") != null)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = KindleParser(context)
        try {
            assertTrue(parser.parse(source!!.path).chapters.isNotEmpty())
            assertNotNull(parser.epubParserOrNull)
            val index = EpubComicArchive.index(context, source.path)
            assertEquals(310, index.pages.size)
            assertEquals(310, index.pages.map { it.imagePath }.distinct().size)
            val first = index.pages.first()
            EpubComicArchive.open(context, source.path, index).use { archive ->
                val expected = java.io.RandomAccessFile(source, "r").use { input ->
                    fun offset(record: Int): Long { input.seek(78L + record * 8); return input.readInt().toLong() and 0xffffffffL }
                    val start = offset(151); val end = offset(152)
                    ByteArray((end - start).toInt()).also { input.seek(start); input.readFully(it) }
                }
                assertArrayEquals(expected, archive.openEntry(first.imagePath)!!.use { it.readBytes() })
                KindleEpubCache.clear(context)
                assertNotNull(archive.openEntry(index.pages.last().imagePath)?.use { it.read() })
            }
            val saved = com.huangder.lumibooks.util.epub.EpubComicPosition.fromPage(index.pages[49], .4f)
            parser.close()
            parser.parse(source.path)
            val rebuilt = EpubComicArchive.index(context, source.path)
            assertEquals(310, rebuilt.pages.size)
            assertEquals(49, rebuilt.restore(saved))
            assertEquals(index.pages, rebuilt.pages)
            assertNotNull(parser.openRenderSession().chapterHref(0))
        } finally { parser.close(); KindleEpubCache.clear(context) }
    }
}
