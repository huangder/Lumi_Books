package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.huangder.lumibooks.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderCustomFontTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun staticFontSurvivesSwitchingAndSharesItsFaceWithPaginationAndPreviews() = withFont { file ->
        val custom = resolve(file)
        assertFalse(custom.fakeBold)
        assertDifferentInk(Typeface.DEFAULT, custom.typeface)
        repeat(3) {
            resolveReaderTypeface(context, "system", file.path, 400)
            assertSame(custom.typeface, resolve(file).typeface)
            assertSame(custom.typeface, resolve(file, 700).typeface)
            assertTrue(resolve(file, 700).fakeBold)
        }

        val reader = ReadView(context)
        reader.slotManager.backgroundPreparationEnabled = false
        try {
            for (fontType in listOf("system", "custom:test", "serif", "custom:test")) {
                reader.configure(fontSizePx = 32f, theme = "day", chapterCount = 1,
                    startChapter = 0, startPage = 0, fontType = fontType,
                    customFontPath = file.path, width = 600, height = 1000)
                if (fontType.startsWith("custom")) {
                    for (page in listOf(reader.prevPageView, reader.curPageView, reader.nextPageView)) {
                        assertSame(custom.typeface, page.textView.typeface)
                    }
                    assertSame(custom.typeface, reader.layoutEngine.sharedTextPaint!!.typeface)
                }
            }
        } finally { reader.slotManager.destroy() }
    }

    @Test fun concurrentPreviewAndReaderRequestsConstructOneFace() = withFont { file ->
        val pool = Executors.newFixedThreadPool(4)
        try {
            val requests = (1..8).map { pool.submit<Typeface> { resolve(file).typeface } }
            val faces = requests.map { it.get(10, TimeUnit.SECONDS) }
            faces.forEach { assertSame(faces.first(), it) }
        } finally { pool.shutdownNow() }
    }

    @Test fun replacingAFileInvalidatesItsEntryAndAxisAndCachesNewWeights() = withFont { file ->
        val original = readerFileFont(file)
        assertNull(original.weightRange)
        // Change the file before native Typeface loading maps it. Windows keeps
        // loaded fonts mapped and cannot replace or truncate them afterwards.
        copyFont(R.font.source_serif4_regular, file)
        val replacement = readerFileFont(file)
        assertNotSame(original, replacement)
        assertEquals(200f..900f, readerVariableWeightRange(context, "custom:test", file.path))
        assertTrue(file.setLastModified(file.lastModified() + 2_000))
        assertNotSame(replacement, readerFileFont(file))
        val light = resolve(file, 250)
        val heavy = resolve(file, 850)
        assertSame(light.typeface, resolve(file, 250).typeface)
        assertEquals(250, light.typeface.weight)
        assertEquals(850, heavy.typeface.weight)
        assertFalse(light.fakeBold || heavy.fakeBold)
        assertDifferentInk(light.typeface, heavy.typeface)
    }

    @Test fun missingFileDoesNotPermanentlyCacheTheDefaultFont() = withFont { file ->
        file.delete()
        assertSame(Typeface.DEFAULT, resolve(file).typeface)
        copyFont(R.font.lxgw_wenkai, file)
        assertDifferentInk(Typeface.DEFAULT, resolve(file).typeface)
    }

    private fun resolve(file: File, weight: Int = 400) =
        resolveReaderTypeface(context, "custom:test", file.path, weight)

    private fun copyFont(id: Int, file: File) {
        context.resources.openRawResource(id).use { input -> file.outputStream().use(input::copyTo) }
    }

    private fun withFont(block: (File) -> Unit) {
        val file = File.createTempFile("reader-custom-", ".ttf", context.cacheDir)
        try { copyFont(R.font.lxgw_wenkai, file); block(file) } finally { file.delete() }
    }

    private fun assertDifferentInk(first: Typeface, second: Typeface) {
        fun ink(face: Typeface) = Bitmap.createBitmap(600, 100, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawText("阅读字体 TXT Reader", 8f, 72f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face; textSize = 40f; color = Color.BLACK
            })
        }
        val a = ink(first)
        val b = ink(second)
        try { assertFalse("Selected font must change the rendered glyphs", a.sameAs(b)) }
        finally { a.recycle(); b.recycle() }
    }
}
