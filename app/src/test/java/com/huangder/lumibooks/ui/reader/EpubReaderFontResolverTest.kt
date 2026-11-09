package com.huangder.lumibooks.ui.reader

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EpubReaderFontResolverTest {
    @Test fun concurrentBodyAndRulePreparationShareACompleteFontFile() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        File(context.cacheDir, "epub_reader_fonts/lxgw_wenkai_v1.ttf").delete()
        val paths = List(8) {
            async(Dispatchers.Default) { requireNotNull(prepareEpubReaderFontPath(context, "kaiti", null)) }
        }.awaitAll()
        assertEquals(1, paths.distinct().size)
        val expectedSize = context.resources.openRawResource(com.huangder.lumibooks.R.font.lxgw_wenkai)
            .use { it.readBytes().size.toLong() }
        assertEquals(expectedSize, File(paths.first()).length())
    }

    @Test fun kaitiRulePreparesBundledFontWithoutChangingBodyFont() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val font = File(requireNotNull(prepareEpubReaderFontPath(context, "kaiti", null)))
        assertTrue(font.isFile && font.length() > 0L)
        assertEquals("400", epubReaderFontWeightDescriptor(font.path))
    }

    @Test fun serifPreparesWesternFontWithoutBundledCjkOverride() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val latin = File(requireNotNull(prepareEpubReaderFontPath(context, "serif", null)))
        assertTrue(latin.isFile && latin.length() > 0L)
        assertEquals("200.0 900.0", epubReaderFontWeightDescriptor(latin.path))
        assertTrue(File(latin.parentFile, "source_serif4_italic_v1.ttf").isFile)
    }
}
