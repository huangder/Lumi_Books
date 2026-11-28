package com.huangder.lumibooks.util.epub

import android.app.Application
import android.graphics.*
import com.huangder.lumibooks.ui.reader.engine.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RuntimeEnvironment
import com.huangder.lumibooks.ui.reader.resolveNativeEpubFonts
import com.huangder.lumibooks.ui.reader.engine.NativeEpubImageItem
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeEpubDeviceProbeTest {
    @Test fun scanLocalEditions() {
        val books = System.getenv("LUMI_NATIVE_EPUB_SCAN")?.split('|').orEmpty()
        assumeTrue("No local EPUB editions supplied", books.isNotEmpty())
        val failures = mutableListOf<String>()
        for (book in books) EpubRenderSession.open(book).use { session ->
            for (chapter in 0 until session.chapterCount) {
                val result = checkNotNull(session.compileNativeChapter(chapter))
                var failure = result.decision.reason
                val document = result.document
                if (document != null) {
                    val config = NativeEpubRenderConfig(1080, 2200, 54f, Typeface.SERIF, null,
                        Color.WHITE, true, 54f, 66f, 54f, 66f)
                    failure = runCatching {
                        NativeEpubChapterRenderer.layout(document, config.copy(
                            embeddedTypefaces = resolveNativeEpubFonts(document, RuntimeEnvironment.getApplication().cacheDir)
                        )) { session.nativeResource(it)?.bytes }.use { layout ->
                            val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
                            try { layout.pages.indices.forEach { layout.draw(Canvas(bitmap), it) } }
                            finally { bitmap.recycle() }
                        }
                    }.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
                }
                val identifier = "${File(book).name} #$chapter ${session.chapterHref(chapter)}"
                if (failure != null) failures += "$identifier $failure"
                println("SCAN $identifier " +
                    (failure ?: "ready fonts=${document?.embeddedFonts?.joinToString { it.family }}"))
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun selectedCorpusChapters() {
        val entries = System.getenv("LUMI_NATIVE_EPUB_PROBE")?.split('|').orEmpty()
        assumeTrue("No local corpus supplied", entries.isNotEmpty())
        var ready = 0
        for (entry in entries) {
            val (path, chapterName) = entry.split('#', limit = 2)
            val session = EpubRenderSession.open(path)
            try {
                val chapter = (0 until session.chapterCount).firstOrNull {
                    session.chapterHref(it).substringBefore('#').endsWith("/$chapterName", true)
                }
                    ?: error("No matching chapter $chapterName")
                val href = session.chapterHref(chapter)
                val started = System.nanoTime()
                val result = checkNotNull(session.compileNativeChapter(chapter))
                println("CORPUS href=$href engine=${result.decision.engine} reason=${result.decision.reason}")
                val document = result.document ?: continue
                assertTrue(result === session.compileNativeChapter(chapter))
                val config = NativeEpubRenderConfig(800, 1400, 32f, Typeface.SERIF, null,
                    Color.WHITE, true, 32f, 40f, 32f, 40f,
                    embeddedTypefaces = resolveNativeEpubFonts(document, RuntimeEnvironment.getApplication().cacheDir))
                NativeEpubChapterRenderer.layout(document, config) { session.nativeResource(it)?.bytes }.use { layout ->
                    val layoutMs = (System.nanoTime() - started) / 1_000_000
                    val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
                    try { layout.pages.indices.forEach { layout.draw(Canvas(bitmap), it) } }
                    finally { bitmap.recycle() }
                    ready++
                    for (page in layout.pages) {
                        page.items.filterIsInstance<NativeEpubImageItem>().forEach { image ->
                            val note = document.footnotes[document.imageLinks[image.start]] ?: return@forEach
                            val hit = image.hit(image.bounds.centerX(), image.bounds.centerY())
                            assertEquals(image.path, hit?.imagePath)
                            assertTrue(image.bounds.width() > config.width / 2)
                            note.imagePaths.forEach { assertTrue(session.nativeResource(it)?.bytes?.isNotEmpty() == true) }
                        }
                    }
                    println("CORPUS ready href=$href pages=${layout.pages.size} layoutMs=$layoutMs fonts=${config.embeddedTypefaces.size} notes=${document.footnotes.size}")
                }
            } finally { session.close() }
        }
        if (System.getenv("LUMI_NATIVE_EPUB_REQUIRE_READY") == "1") assertEquals(entries.size, ready)
    }

    @Test fun actualDeviceBookChaptersCompileAndDraw() {
        val epub = File("build/device-epubs/mao.epub")
        assumeTrue("device probe EPUB is unavailable: ${epub.absolutePath}", epub.isFile)
        val session = EpubRenderSession.open(epub.absolutePath)
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
