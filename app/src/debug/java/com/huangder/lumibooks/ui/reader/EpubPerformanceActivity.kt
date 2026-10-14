package com.huangder.lumibooks.ui.reader

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.huangder.lumibooks.tts.TtsPageChangeOrigin
import com.huangder.lumibooks.ui.reader.engine.ReadView
import com.huangder.lumibooks.ui.reader.engine.ReadViewCallbacks
import com.huangder.lumibooks.util.epub.EpubRenderSession
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Debug-only, isolated fixture runner: no library writes, preferences, or test APK. */
class EpubPerformanceActivity : ComponentActivity() {
    private var session: EpubRenderSession? = null
    private var releaseReader: (((() -> Unit)) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val engine = intent.getStringExtra("engine") ?: "epub"
        val transition = intent.getStringExtra("transition") ?: "slide"
        val theme = intent.getStringExtra("theme") ?: "day"
        val publisher = intent.getBooleanExtra("publisher", false)
        val turns = intent.getIntExtra("turns", 0).coerceIn(0, 120)
        val fixture = intent.getStringExtra("fixture") ?: "text"
        val suiteCycles = intent.getIntExtra("suite_cycles", 0).coerceIn(0, 30)
        val chapters = List(40) { chapter ->
            List(if (chapter % 3 == 2) 3 else 40) { paragraph ->
                "第 ${chapter + 1} 章，第 ${paragraph + 1} 段。清晨的阳光照在书页上，阅读应该流畅而安静。".repeat(3)
            }.joinToString("\n\n")
        }
        setContent {
            var renderSession by remember { mutableStateOf<EpubRenderSession?>(null) }
            var chapter by remember { mutableIntStateOf(0) }
            var page by remember { mutableIntStateOf(0) }
            var count by remember { mutableIntStateOf(1) }
            var ready by remember { mutableStateOf(false) }
            var turn by remember { mutableStateOf<((Int) -> Boolean)?>(null) }
            var activeTheme by remember { mutableStateOf(theme) }
            var publisherPaint by remember { mutableStateOf(publisher) }
            fun position(c: Int, p: Int, n: Int) {
                chapter = c; page = p; count = n; ready = true
                Log.d("EpubPerfFixture", "position engine=$engine transition=$transition chapter=$c page=$p count=$n")
            }
            LaunchedEffect(Unit) {
                if (engine != "native") renderSession = withContext(Dispatchers.IO) {
                    val book = if (fixture == "tutorial") {
                        File(cacheDir, "epub-performance-tutorial.epub").also { file ->
                            assets.open("builtin/lumi/guide_zh-CN.epub").use { input -> file.outputStream().use(input::copyTo) }
                        }
                    } else createFixture(chapters, fixture == "images")
                    EpubRenderSession.open(book.path).also { session = it }
                }
                while (!ready || turn == null) delay(50)
                delay(1500)
                if (suiteCycles > 0 && engine == "epub") {
                    repeat(suiteCycles) { cycle ->
                        for (step in 0..2) {
                            activeTheme = listOf("night", "sepia_dark", "day")[step]
                            publisherPaint = step == 2
                            delay(35)
                        }
                        delay(550)
                        val sourceChapter = chapter
                        val sourcePage = page
                        Log.d("EpubPerfFixture", "SUITE_TURN cycle=$cycle from=$sourceChapter/$sourcePage")
                        turn?.invoke(1)
                        var elapsed = 0
                        while (chapter == sourceChapter && page == sourcePage && elapsed < 8_000) {
                            delay(100)
                            elapsed += 100
                        }
                        if (chapter == sourceChapter && page == sourcePage) {
                            Log.e("EpubPerfFixture", "SUITE_FAIL cycle=$cycle chapter=$chapter page=$page")
                            return@LaunchedEffect
                        }
                        Log.d("EpubPerfFixture", "SUITE_PASS cycle=$cycle to=$chapter/$page latencyMs=$elapsed")
                        delay(300)
                    }
                    Log.d("EpubPerfFixture", "SUITE_DONE cycles=$suiteCycles transition=$transition")
                }
                Log.d("EpubPerfFixture", "START engine=$engine transition=$transition fixture=$fixture turns=$turns")
                repeat(turns) { index ->
                    Log.d("EpubPerfFixture", "intent=$index chapter=$chapter page=$page count=$count")
                    turn?.invoke(1)
                    delay(700)
                }
                Log.d("EpubPerfFixture", "DONE engine=$engine transition=$transition chapter=$chapter page=$page")
            }
            if (engine == "native") {
                AndroidView(factory = { context ->
                    ReadView(context).apply {
                        setContentProvider { chapters.getOrNull(it) }
                        setCallbacks(object : ReadViewCallbacks {
                            override fun onPageChanged(globalPage: Int, chapterIndex: Int, pageInChapter: Int,
                                chapterTotalPages: Int, origin: TtsPageChangeOrigin) = position(chapterIndex, pageInChapter, chapterTotalPages)
                            override fun onMenuToggle() = Unit
                            override fun onLoadingChanged(isLoading: Boolean) = Unit
                        })
                        turn = { direction -> if (direction > 0) turnToNextPage() else turnToPreviousPage(); true }
                        post {
                            configure(18f * resources.displayMetrics.scaledDensity, theme, chapters.size, 0, 0,
                                marginLeftDp = 20f, marginRightDp = 20f, marginTopDp = 24f, marginBottomDp = 24f)
                            setPageTransitionTiming(transition, 260)
                            setPageTransition(transition)
                        }
                    }
                }, modifier = Modifier.fillMaxSize())
            } else renderSession?.let { book ->
                EpubWebViewReader(
                    session = book, chapterIndex = chapter, nativeCssEnabled = engine == "native_css", fontSizeSp = 18f, fontType = "system",
                    fontFilePath = null, textColorOverride = null, theme = activeTheme, coverBackgroundColor = null,
                    publisherPaintOnly = publisherPaint, restoreLocatorJson = null, restoreProgression = 0f,
                    pageTransition = transition, pageTransitionDurationMs = 260,
                    marginLeftDp = 20f, marginRightDp = 20f, marginTopDp = 24f, marginBottomDp = 24f,
                    continuousScroll = intent.getBooleanExtra("continuous", false),
                    onPageTextProviderReady = {}, onPageTurnHandlerReady = { turn = it },
                    onReleaseHandlerReady = { releaseReader = it }, onPageChanged = { c, p, n, _ -> position(c, p, n) },
                    onCenterTap = {}, onImagePreviewOpen = {},
                    onChapterTurn = { chapter = (chapter + it).coerceIn(0, book.chapterCount - 1) },
                    onInternalLink = { c, _ -> chapter = c }, onExternalLink = {}, onSelection = {},
                    onSelectionCleared = {}, onSearchResolved = { _, _ -> },
                    onRenderUnavailable = { Log.e("EpubPerfFixture", "render failure: $it") }
                )
            }
        }
    }

    private fun createFixture(chapters: List<String>, images: Boolean): File {
        val file = File(cacheDir, "epub-performance-fixture.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", """<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            entry("book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">lumi-performance</dc:identifier><dc:title>Performance fixture</dc:title><dc:language>zh</dc:language></metadata><manifest>${chapters.indices.joinToString("") { "<item id=\"c$it\" href=\"c$it.xhtml\" media-type=\"application/xhtml+xml\"/>" }}</manifest><spine>${chapters.indices.joinToString("") { "<itemref idref=\"c$it\"/>" }}</spine></package>""")
            chapters.forEachIndexed { index, content ->
                val picture = if (images) """<svg xmlns="http://www.w3.org/2000/svg" width="240" height="160"><rect width="240" height="160" fill="#c46c40"/></svg>""" else ""
                entry("c$index.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter $index</title><style>body{background:#fff;color:#333;font-size:18px;line-height:1.5}p{margin:0 0 12px}</style></head><body><h1>第 ${index + 1} 章</h1>$picture${content.split("\n\n").joinToString("") { "<p>$it</p>" }}</body></html>""")
            }
        }
        return file
    }

    override fun onDestroy() {
        val book = session
        releaseReader?.invoke { book?.close() } ?: book?.close()
        super.onDestroy()
    }
}
