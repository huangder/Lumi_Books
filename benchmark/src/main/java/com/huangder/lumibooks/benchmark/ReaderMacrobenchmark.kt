package com.huangder.lumibooks.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ReaderMacrobenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun seedBooks() {
        BenchmarkBooks.prepareApp(device)
    }

    @Test
    fun coldStartup() = measureStartup(StartupMode.COLD, splash = false)

    @Test
    fun warmStartup() = measureStartup(StartupMode.WARM, splash = false)

    @Test
    fun coldStartupWithBrandedSplash() = measureStartup(StartupMode.COLD, splash = true)

    @Test
    fun firstOpenLargeTxt() = measureBookOpen(BenchmarkBooks.txtLarge, clearCache = true)

    @Test
    fun hotOpenLargeTxt() = measureBookOpen(BenchmarkBooks.txtLarge, clearCache = false)

    @Test
    fun firstOpenLargeEpub() = measureBookOpen(BenchmarkBooks.epubLarge, clearCache = true)

    @Test
    fun hotOpenLargeEpub() = measureBookOpen(BenchmarkBooks.epubLarge, clearCache = false)

    @Test
    fun resumeLargeTxt() = measureBookOpen(BenchmarkBooks.txtLarge, clearCache = false, resume = true)

    @Test
    fun resumeLargeEpub() = measureBookOpen(BenchmarkBooks.epubLarge, clearCache = false, resume = true)

    @Test
    fun firstOpenOriginalLargeEpub() = measureBookOpen(BenchmarkBooks.epubLarge, clearCache = true, original = true)

    @Test
    fun hotOpenOriginalLargeEpub() = measureBookOpen(BenchmarkBooks.epubLarge, clearCache = false, original = true)

    @Test
    fun resumeOriginalLargeEpub() = measureBookOpen(BenchmarkBooks.epubLarge, clearCache = false, original = true, resume = true)

    @Test
    fun continuousThirtyPageTurnsTxt() = measurePageTurns(BenchmarkBooks.txtLarge, 30)

    @Test
    fun continuousAndCrossChapterTurnsEpub() = measurePageTurns(BenchmarkBooks.epubRegular, 40)

    @Test
    fun continuousEpubFling() = measureReaderBurst(vertical = true, original = false)

    @Test
    fun rapidOriginalEpubCurl() = measureReaderBurst(vertical = false, original = true)

    @Test
    fun bookshelfFlingOriginalGlass() = measureBookshelfBurst("original")

    @Test
    fun bookshelfFlingPerformanceGlass() = measureBookshelfBurst("performance")

    private fun measureStartup(startupMode: StartupMode, splash: Boolean) {
        BenchmarkBooks.all.forEach { BenchmarkBooks.ensureImported(device, it) }
        BenchmarkBooks.prepareApp(device, splash)
        rule.measureRepeated(
            packageName = BenchmarkBooks.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            compilationMode = optimizedCompilation,
            startupMode = startupMode,
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait()
            BenchmarkBooks.awaitStartupContent(device)
        }
    }

    private fun measureBookOpen(
        fixture: BenchmarkBooks.Fixture,
        clearCache: Boolean,
        original: Boolean = false,
        resume: Boolean = false
    ) {
        BenchmarkBooks.ensureImported(device, fixture)
        rule.measureRepeated(
            packageName = BenchmarkBooks.PACKAGE_NAME,
            metrics = openMetrics,
            iterations = 10,
            compilationMode = optimizedCompilation,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                val mode = if (original) "book_layout" else "reader_layout"
                if (clearCache) {
                    BenchmarkBooks.clearReaderCache(device)
                } else {
                    BenchmarkBooks.configureReader(device, fixture, renderMode = mode, resume = resume)
                    BenchmarkBooks.openFromBookshelf(device, fixture)
                    BenchmarkBooks.awaitReaderSettled(device)
                    BenchmarkBooks.closeReader(device, fixture)
                }
                BenchmarkBooks.configureReader(device, fixture, renderMode = mode, resume = resume)
                BenchmarkBooks.prepareBookOpen(device, fixture)
            }
        ) {
            BenchmarkBooks.clickPreparedBook(device, fixture)
            BenchmarkBooks.awaitReaderSettled(device)
        }
    }

    private fun measureReaderBurst(vertical: Boolean, original: Boolean) {
        val fixture = BenchmarkBooks.epubRegular
        BenchmarkBooks.ensureImported(device, fixture)
        rule.measureRepeated(
            packageName = BenchmarkBooks.PACKAGE_NAME,
            metrics = if (vertical) frameMetrics else pageTurnMetrics,
            iterations = 10,
            compilationMode = optimizedCompilation,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                BenchmarkBooks.configureReader(device, fixture,
                    renderMode = if (original) "book_layout" else "reader_layout",
                    transition = if (vertical) "continuous" else "curl")
                BenchmarkBooks.openFromBookshelf(device, fixture)
                BenchmarkBooks.awaitReaderSettled(device)
            }
        ) {
            BenchmarkBooks.swipeBurst(device, BenchmarkBooks.readerBounds(device), vertical, forward = 12, reverse = 5)
            BenchmarkBooks.awaitBurstTail(device)
        }
    }

    private fun measureBookshelfBurst(glassQuality: String) {
        BenchmarkBooks.ensureImported(device, BenchmarkBooks.txtSmall)
        BenchmarkBooks.prepareBookshelfFixture(device, glassQuality)
        rule.measureRepeated(
            packageName = BenchmarkBooks.PACKAGE_NAME,
            metrics = frameMetrics,
            iterations = 10,
            compilationMode = optimizedCompilation,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                BenchmarkBooks.showBookshelf(device)
                BenchmarkBooks.scrollBookshelfToStart(device)
            }
        ) {
            BenchmarkBooks.swipeBurst(device, BenchmarkBooks.bookshelfBounds(device), vertical = true, forward = 4, reverse = 2)
            BenchmarkBooks.awaitBurstTail(device)
        }
    }

    private fun measurePageTurns(
        fixture: BenchmarkBooks.Fixture,
        count: Int
    ) {
        BenchmarkBooks.ensureImported(device, fixture)
        rule.measureRepeated(
            packageName = BenchmarkBooks.PACKAGE_NAME,
            metrics = pageTurnMetrics,
            iterations = 10,
            compilationMode = optimizedCompilation,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                BenchmarkBooks.configureReader(device, fixture)
                BenchmarkBooks.openFromBookshelf(device, fixture)
                BenchmarkBooks.awaitReaderSettled(device)
            }
        ) {
            repeat(count) { BenchmarkBooks.turnNextPage(device) }
        }
    }

    private companion object {
        val frameMetrics = listOf(FrameTimingMetric(), FrameTimingGfxInfoMetric())
        val optimizedCompilation = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.UseIfAvailable,
            warmupIterations = 3
        )
        val openMetrics = listOf(
            TraceSectionMetric(
                sectionName = "book_open_to_first_content",
                mode = TraceSectionMetric.Mode.First,
                label = "bookOpenToFirstContent",
                targetPackageOnly = false
            ),
            TraceSectionMetric(
                sectionName = "book_open_to_interactive",
                mode = TraceSectionMetric.Mode.First,
                label = "bookOpenToInteractive",
                targetPackageOnly = false
            ),
            FrameTimingMetric(),
            FrameTimingGfxInfoMetric()
        )
        val pageTurnMetrics = listOf(
            TraceSectionMetric(
                sectionName = "reader_page_turn_to_first_frame",
                mode = TraceSectionMetric.Mode.Max,
                label = "pageTurnToFirstFrame",
                targetPackageOnly = false
            ),
            TraceSectionMetric(
                sectionName = "reader_preloaded_turn_to_first_frame",
                mode = TraceSectionMetric.Mode.Max,
                label = "preloadedTurnToFirstFrame",
                targetPackageOnly = false
            ),
            TraceSectionMetric(
                sectionName = "reader_cross_chapter_turn_to_first_frame",
                mode = TraceSectionMetric.Mode.Max,
                label = "crossChapterTurnToFirstFrame",
                targetPackageOnly = false
            ),
            FrameTimingMetric(),
            FrameTimingGfxInfoMetric()
        )
    }
}
