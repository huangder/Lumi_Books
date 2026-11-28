package com.huangder.lumibooks.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    private fun leaveReader(device: UiDevice) {
        repeat(4) {
            device.waitForIdle(3_000)
            if (!device.hasObject(By.res("reader_content_ready")) &&
                !device.hasObject(By.res("reader_content_loading"))
            ) return
            BenchmarkBooks.requireAppForeground(device)
            device.pressBack()
            // Wait out the close transition: a second Back on the home screen leaves the app.
            device.wait(Until.gone(By.res("reader_content_ready")), 6_000)
            device.wait(Until.gone(By.res("reader_content_loading")), 6_000)
            if (device.hasObject(By.res("home_screen")) || device.hasObject(By.res("bookshelf_screen"))) return
        }
    }

    /**
     * Cold start, home, opening and paging the bundled guide, then bookshelf scrolling.
     * Uses only content that ships in the app, so it does not depend on importing fixtures.
     * The guide is opened from the home screen: the shelf keeps the guides inside a folder.
     *
     * Startup is required in every iteration. The steps after it are best effort: a launch
     * that restores an earlier reader session must not discard the profile already collected.
     */
    @Test
    fun generate() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        BenchmarkBooks.prepareApp(device)
        var readerJourneys = 0
        baselineProfileRule.collect(BenchmarkBooks.PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()
            runCatching {
                BenchmarkBooks.awaitStartupContent(device)
                leaveReader(device)
                // The first tap can land while the home entrance animation is still settling.
                var opened = false
                for (attempt in 0 until 3) {
                    val guide = device.wait(Until.findObject(By.textStartsWith("Lumi ")), 10_000) ?: break
                    device.waitForIdle(2_000)
                    val center = guide.visibleCenter
                    device.click(center.x, center.y)
                    opened = device.wait(Until.hasObject(By.res("reader_content_ready")), 12_000)
                    if (opened) break
                }
                if (opened) {
                    BenchmarkBooks.awaitReaderSettled(device, 30_000)
                    repeat(3) { BenchmarkBooks.turnNextPage(device) }
                    readerJourneys++
                }
                leaveReader(device)
                BenchmarkBooks.showBookshelf(device)
                BenchmarkBooks.scrollBookshelf(device)
            }
        }
        check(readerJourneys > 0) { "No iteration opened the bundled guide" }
    }
}
