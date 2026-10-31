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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.huangder.lumibooks.data.local.dao.BookDao
import com.huangder.lumibooks.data.local.dataStore
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.domain.model.BookFormat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.huangder.lumibooks.domain.model.LongPressSelectionRange
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.domain.model.ReaderPageTransition
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.util.epub.BookRenderSession
import com.huangder.lumibooks.util.epub.BookRenderSource
import com.huangder.lumibooks.util.epub.EpubRenderSession
import com.huangder.lumibooks.util.parser.BookParserFactory
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Debug-only fixture runner: reads library rows and preferences, never writes them; no test APK. */
class EpubPerformanceActivity : ComponentActivity() {
    private var session: BookRenderSession? = null
    private var releaseReader: (((() -> Unit)) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val engine = intent.getStringExtra("engine") ?: "epub"
        val transition = intent.getStringExtra("transition") ?: "slide"
        val theme = intent.getStringExtra("theme") ?: "day"
        val publisher = intent.getBooleanExtra("publisher", false)
        val turns = intent.getIntExtra("turns", 0).coerceIn(0, 300)
        val fixture = intent.getStringExtra("fixture") ?: "text"
        val suiteCycles = intent.getIntExtra("suite_cycles", 0).coerceIn(0, 30)
        // Fast cross-chapter runs: interval between inputs, and api | tap | swipe input.
        val intervalMs = intent.getLongExtra("interval_ms", 700L).coerceIn(50L, 5_000L)
        val input = intent.getStringExtra("input") ?: "api"
        // "user" for transition/long_press reads the saved reader settings.
        val longPressKey = intent.getStringExtra("long_press")
        // A library book by percent-encoded title, opened in this process only.
        val bookTitle = intent.getStringExtra("book_title")?.let { android.net.Uri.decode(it) }
        if (android.os.Build.VERSION.SDK_INT >= 27 && bookTitle == null && intent.getBooleanExtra("locked_fixture", false)) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val chapters = List(40) { chapter ->
            List(if (chapter % 3 == 2) 3 else 40) { paragraph ->
                "第 ${chapter + 1} 章，第 ${paragraph + 1} 段。清晨的阳光照在书页上，阅读应该流畅而安静。".repeat(3)
            }.joinToString("\n\n")
        }
        setContent {
            var renderSession by remember { mutableStateOf<BookRenderSession?>(null) }
            var chapter by remember { mutableIntStateOf(0) }
            var page by remember { mutableIntStateOf(0) }
            var count by remember { mutableIntStateOf(1) }
            var ready by remember { mutableStateOf(false) }
            var turn by remember { mutableStateOf<((Int) -> Boolean)?>(null) }
            var activeTheme by remember { mutableStateOf(theme) }
            var publisherPaint by remember { mutableStateOf(publisher) }
            var activeTransition by remember { mutableStateOf(transition) }
            var activeDurationMs by remember { mutableIntStateOf(260) }
            var activeLongPress by remember { mutableStateOf(LongPressSelectionRange.fromKey(longPressKey)) }
            var bookFormat by remember { mutableStateOf("EPUB") }
            var bookNativeCss by remember { mutableStateOf(engine == "native_css") }
            fun position(c: Int, p: Int, n: Int) {
                chapter = c; page = p; count = n; ready = true
                Log.d("EpubPerfFixture", "position engine=$engine transition=$activeTransition chapter=$c page=$p count=$n")
            }
            LaunchedEffect(Unit) {
                if (transition == "user" || longPressKey == "user") {
                    val preferences = dataStore.data.first()
                    if (transition == "user") {
                        activeTransition = ReaderPageTransition.normalizeKey(
                            preferences[stringPreferencesKey("page_transition")])
                        activeDurationMs = VisualMotion.durationMillis(
                            preferences[intPreferencesKey("page_transition_${activeTransition}_duration_ms")]
                                ?: ReaderPageAnimationSettings.defaultFor(activeTransition))
                    }
                    if (longPressKey == "user") activeLongPress = LongPressSelectionRange.fromKey(
                        preferences[stringPreferencesKey("long_press_selection_range")])
                }
                if (engine != "native") renderSession = withContext(Dispatchers.IO) {
                    if (bookTitle != null) {
                        val books = EntryPointAccessors.fromApplication(
                            applicationContext, PerformanceFixtureEntryPoint::class.java).bookDao().getAllBooksSnapshot()
                        val entity = libraryBookMatching(bookTitle, books)
                        if (entity == null) {
                            val series = bookTitle.trimEnd { it.isDigit() }
                            Log.e("EpubPerfFixture", "BOOK_MISSING title=$bookTitle candidates=" +
                                books.map { it.title }.filter { series.isNotBlank() && it.contains(series) }.take(8))
                            return@withContext null
                        }
                        val format = runCatching { BookFormat.valueOf(entity.format) }.getOrNull()
                        val parser = format?.let { BookParserFactory.createParser(it, applicationContext) }
                        if (parser !is BookRenderSource) {
                            Log.e("EpubPerfFixture", "BOOK_UNSUPPORTED title=${entity.title} format=${entity.format}")
                            return@withContext null
                        }
                        parser.parse(entity.filePath)
                        bookFormat = entity.format
                        bookNativeCss = dataStore.data.first()[booleanPreferencesKey("native_epub_css_${entity.id}")] ?: false
                        parser.openRenderSession().also {
                            session = it
                            Log.d("EpubPerfFixture", "BOOK title=${entity.title} format=${entity.format} " +
                                "chapters=${it.chapterCount} nativeCss=$bookNativeCss")
                        }
                    } else {
                        val book = if (fixture == "tutorial") {
                            File(cacheDir, "epub-performance-tutorial.epub").also { file ->
                                assets.open("builtin/lumi/guide_zh-CN.epub").use { input -> file.outputStream().use(input::copyTo) }
                            }
                        } else createFixture(chapters, fixture == "images")
                        EpubRenderSession.open(book.path).also { session = it }
                    }
                }
                if (engine != "native" && renderSession == null) return@LaunchedEffect
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
                Log.d("EpubPerfFixture", "START engine=$engine transition=$activeTransition fixture=$fixture turns=$turns " +
                    "input=$input intervalMs=$intervalMs longPress=${activeLongPress.storageKey} durationMs=$activeDurationMs")
                repeat(turns) { index ->
                    val startedAt = android.os.SystemClock.uptimeMillis()
                    Log.d("EpubPerfFixture", "intent=$index chapter=$chapter page=$page count=$count")
                    when (input) {
                        "swipe" -> injectSwipe(vertical = activeTransition == "scroll" || activeTransition == "continuous")
                        "tap" -> injectTap()
                        else -> turn?.invoke(1)
                    }
                    // interval_ms is the period between input starts, so 166 means six per second.
                    delay((intervalMs - (android.os.SystemClock.uptimeMillis() - startedAt)).coerceAtLeast(0L))
                }
                Log.d("EpubPerfFixture", "DONE engine=$engine transition=$activeTransition chapter=$chapter page=$page")
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
                val continuous = activeTransition == "continuous" || intent.getBooleanExtra("continuous", false)
                val reader: @Composable (
                    ((() -> com.huangder.lumibooks.ui.components.GlassBitmapBackdrop?)?) -> Unit,
                    () -> Unit
                ) -> Unit = { snapshotProvider, centerTap ->
                EpubWebViewReader(
                    session = book, chapterIndex = chapter, nativeCssEnabled = bookNativeCss, fontSizeSp = 18f, fontType = "system",
                    fontFilePath = null, textColorOverride = null, theme = activeTheme, coverBackgroundColor = null,
                    publisherPaintOnly = publisherPaint, restoreLocatorJson = null, restoreProgression = 0f,
                    pageTransition = if (continuous) "none" else activeTransition, pageTransitionDurationMs = activeDurationMs,
                    marginLeftDp = 20f, marginRightDp = 20f, marginTopDp = 24f, marginBottomDp = 24f,
                    longPressSelectionRange = activeLongPress, bookFormat = bookFormat,
                    continuousScroll = continuous,
                    onPageTextProviderReady = {}, onPageTurnHandlerReady = { turn = it },
                    onReleaseHandlerReady = { releaseReader = it }, onPageChanged = { c, p, n, _ -> position(c, p, n) },
                    onGlassSnapshotProviderReady = snapshotProvider,
                    onCenterTap = centerTap, onImagePreviewOpen = {},
                    onChapterTurn = { chapter = (chapter + it).coerceIn(0, book.chapterCount - 1) },
                    onInternalLink = { c, _ -> chapter = c }, onExternalLink = {}, onSelection = {},
                    onSelectionCleared = {}, onSearchResolved = { _, _ -> },
                    onRenderUnavailable = { Log.e("EpubPerfFixture", "render failure: $it") }
                )
                }
                if (intent.getBooleanExtra("glass", false)) {
                    EpubGlassPreview(chapter, page, ready, reader)
                } else reader({}, {})
            }
        }
    }

    /** A real touch stream through the decor view, so the host's swipe paths run as in reading. */
    private suspend fun injectSwipe(vertical: Boolean = false) {
        val root = window.decorView
        // Next page: leftward swipe, or upward for vertical paging and scrolling.
        val start = if (vertical) root.width * 0.5f to root.height * 0.75f else root.width * 0.8f to root.height * 0.5f
        val end = if (vertical) root.width * 0.5f to root.height * 0.25f else root.width * 0.2f to root.height * 0.5f
        val downTime = android.os.SystemClock.uptimeMillis()
        fun send(action: Int, point: Pair<Float, Float>, time: Long) {
            val event = android.view.MotionEvent.obtain(downTime, time, action, point.first, point.second, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            root.dispatchTouchEvent(event)
            event.recycle()
        }
        send(android.view.MotionEvent.ACTION_DOWN, start, downTime)
        val steps = 6
        for (step in 1..steps) {
            delay(16)
            val fraction = step / steps.toFloat()
            send(android.view.MotionEvent.ACTION_MOVE,
                start.first + (end.first - start.first) * fraction to start.second + (end.second - start.second) * fraction,
                android.os.SystemClock.uptimeMillis())
        }
        send(android.view.MotionEvent.ACTION_UP, end, android.os.SystemClock.uptimeMillis())
    }

    private fun injectTap() {
        val root = window.decorView
        val x = root.width * 0.85f
        val y = root.height * 0.5f
        val downTime = android.os.SystemClock.uptimeMillis()
        listOf(android.view.MotionEvent.ACTION_DOWN to downTime,
            android.view.MotionEvent.ACTION_UP to downTime + 40L).forEach { (action, time) ->
            val event = android.view.MotionEvent.obtain(downTime, time, action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            root.dispatchTouchEvent(event)
            event.recycle()
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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PerformanceFixtureEntryPoint {
    fun bookDao(): BookDao
}

/** Exact title first; otherwise the series volume whose trailing number matches, so "1" never picks "10". */
private fun libraryBookMatching(query: String, books: List<BookEntity>): BookEntity? {
    fun normalized(text: String) = text.filterNot(Char::isWhitespace).lowercase()
    val wanted = normalized(query)
    books.firstOrNull { normalized(it.title) == wanted }?.let { return it }
    val volume = wanted.takeLastWhile(Char::isDigit)
    val series = wanted.dropLast(volume.length)
    if (series.isEmpty()) return null
    val inSeries = books.filter { normalized(it.title).contains(series) }
    if (volume.isEmpty()) return inSeries.singleOrNull()
    val volumePattern = Regex(Regex.escape(series) + "\\D{0,3}0*" + volume.trimStart('0') + "(?!\\d)")
    return inSeries.filter { volumePattern.containsMatchIn(normalized(it.title)) }.singleOrNull()
        ?: inSeries.singleOrNull()
}
