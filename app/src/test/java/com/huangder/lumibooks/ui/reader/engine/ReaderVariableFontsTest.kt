package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import com.huangder.lumibooks.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderVariableFontsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun weightControlUsesTheActualAxisAndHidesForStaticFonts() {
        assertEquals(200f..900f, readerVariableWeightRange(context, "serif", null))
        assertNull(readerVariableWeightRange(context, "kaiti", null))
        assertNull(variableWeightRange(ByteBuffer.wrap(byteArrayOf(0, 1, 2))))
        for (id in listOf(R.font.source_serif4_regular, R.font.source_serif4_italic, R.font.lxgw_wenkai)) {
            val file = File.createTempFile("reader-font-", ".ttf", context.cacheDir)
            try {
                context.resources.openRawResource(id).use { input -> file.outputStream().use(input::copyTo) }
                val range = readerVariableWeightRange(context, "custom:test", file.path)
                if (id == R.font.lxgw_wenkai) assertNull(range) else assertEquals(200f..900f, range)
            } finally { file.delete() }
        }
    }

    @Test fun variableWeightsAndTrueItalicProduceDifferentInkWithoutFakeBold() {
        val light = resolveReaderTypeface(context, "serif", null, 250)
        val medium = resolveReaderTypeface(context, "serif", null, 550)
        val heavy = resolveReaderTypeface(context, "serif", null, 850)
        assertFalse(light.fakeBold || medium.fakeBold || heavy.fakeBold)
        assertEquals(250, light.typeface.weight)
        assertEquals(550, medium.typeface.weight)
        assertEquals(850, heavy.typeface.weight)
        val a = ink(light.typeface)
        val b = ink(medium.typeface)
        val c = ink(heavy.typeface)
        val italic = Typeface.create(medium.typeface, Typeface.ITALIC)
        assertTrue(italic.isItalic)
        val d = ink(italic)
        assertFalse(a.sameAs(b))
        assertFalse(b.sameAs(c))
        assertFalse(b.sameAs(d))
        a.recycle(); b.recycle(); c.recycle(); d.recycle()
    }

    @Test fun serifUsesSystemCjkFallbackWithoutReplacingWesternTypeface() {
        val face = readerSerifTypeface(context)
        fun shapedFont(typeface: Typeface, text: String) = TextRunShaper.shapeTextRun(
            text, 0, text.length, 0, text.length, 0f, 0f, false,
            Paint().apply { this.typeface = typeface; textSize = 32f }
        ).getFont(0)

        for (text in listOf("中文", "日本語", "한글")) {
            assertEquals(shapedFont(Typeface.SERIF, text), shapedFont(face, text))
        }
        assertNotEquals(shapedFont(face, "Latin"), shapedFont(face, "中文"))
    }

    private fun ink(face: Typeface): Bitmap = Bitmap.createBitmap(600, 100, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawText("Source Serif 4 — office", 8f, 72f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face; textSize = 48f; color = Color.BLACK
        })
    }
}
