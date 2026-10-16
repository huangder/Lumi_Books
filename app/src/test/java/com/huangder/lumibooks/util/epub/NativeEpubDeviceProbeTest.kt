package com.huangder.lumibooks.util.epub

import android.app.Application
import android.graphics.*
import com.huangder.lumibooks.ui.reader.engine.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeEpubDeviceProbeTest {
    @Test fun actualDeviceBookChaptersCompileAndDraw() {
        val session = EpubRenderSession.open(File("build/device-epubs/mao.epub").absolutePath)
        try {
            for (chapter in 184..186) {
                val source = session.nativeChapterSource(chapter)!!
                val result = NativeEpubCompiler.compile(source)
                println("DEVICE_PROBE chapter=$chapter href=${source.href} engine=${result.decision.engine} reason=${result.decision.reason}")
                assertEquals(result.decision.reason, ChapterRenderEngine.NATIVE, result.decision.engine)
                val doc = result.document!!
                val cfg = NativeEpubRenderConfig(1200, 2670, 54f, Typeface.SERIF, null, Color.WHITE, true, 60f, 72f, 60f, 72f)
                val layout = NativeEpubChapterRenderer.layout(doc, cfg) { session.nativeResource(it)?.bytes }
                try {
                    assertTrue(layout.pages.isNotEmpty())
                    val bitmap = Bitmap.createBitmap(cfg.width, cfg.height, Bitmap.Config.ARGB_8888)
                    try { layout.draw(Canvas(bitmap), 0) } finally { bitmap.recycle() }
                    println("DEVICE_PROBE ready chapter=$chapter pages=${layout.pages.size}")
                } finally { layout.close() }
            }
        } finally { session.close() }
    }
}
