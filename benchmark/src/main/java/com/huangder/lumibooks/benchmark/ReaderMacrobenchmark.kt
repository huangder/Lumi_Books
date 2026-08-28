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
    fun coldStartupAndBookshelfScroll() = measureStartupAndBookshelf(StartupMode.COLD)

    @Test
    fun warmStartupAndBookshelfScroll() = measureStartupAndBookshelf(StartupMode.WARM)

    @Test
    fun firstOpenLargeTxt() = measureBookOpen(BenchmarkBooks.txtLarge, clearCache = true)

    @Test
    fun hotOpenLargeTxt() = measureBookOpen(BenchmarkBooks.txtLarge, clearCache = false)

    @Test
    fun firstOpenLargeEpub() = measureBookOpen(BenchmarkBooks.epubLarge, clearCache = true)

    @Test
    fun hotOpenLargeEpub() = measureBookOpen(BenchmarkBooks.epubLarge, clearCache = false)

    @Test
    fun continuousThirtyPageTurnsTxt() = measurePageTurns(BenchmarkBooks.txtLarge, 30)

    @Test
    fun continuousAndCrossChapterTurnsEpub() = measurePageTurns(BenchmarkBooks.epubRegular, 40)

    private fun measureStartupAndBookshelf(startupMode: StartupMode) {
        BenchmarkBooks.all.forEach { BenchmarkBooks.ensureImported(device, it) }
        rule.measureRepeated(
            packageName = BenchmarkBooks.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric(), FrameTimingGfxInfoMetric()),
            iterations = 10,
            compilationMode = optimizedCompilation,
            startupMode = startupMode,
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait()
            repeat(3) { BenchmarkBooks.scrollHome(device) }
            BenchmarkBooks.showBookshelf(device)
            repeat(3) { BenchmarkBooks.scrollBookshelf(device) }
        }
    }

    private fun measureBookOpen(
        fixture: BenchmarkBooks.Fixture,
        clearCache: Boolean
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
                if (clearCache) {
                    BenchmarkBooks.clearReaderCache(device)
                } else {
                    BenchmarkBooks.openFromBookshelf(device, fixture)
                    BenchmarkBooks.awaitReaderSettled(device)
                    BenchmarkBooks.closeReader(device, fixture)
                }
            }
        ) {
            BenchmarkBooks.openFromBookshelf(device, fixture, resetPosition = true)
            BenchmarkBooks.awaitReaderSettled(device)
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
                BenchmarkBooks.openFromBookshelf(device, fixture, resetPosition = true)
                BenchmarkBooks.awaitReaderSettled(device)
            }
        ) {
            repeat(count) { BenchmarkBooks.turnNextPage(device) }
        }
    }

    private companion object {
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
