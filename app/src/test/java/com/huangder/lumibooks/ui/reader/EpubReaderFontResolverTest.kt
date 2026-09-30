package com.huangder.lumibooks.ui.reader

import android.app.Application
import kotlinx.coroutines.runBlocking
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
    @Test fun serifPreparesWesternFontWithoutBundledCjkOverride() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val latin = File(requireNotNull(prepareEpubReaderFontPath(context, "serif", null)))
        assertTrue(latin.isFile && latin.length() > 0L)
        assertTrue(File(latin.parentFile, "source_serif4_italic_v1.ttf").isFile)
    }
}
