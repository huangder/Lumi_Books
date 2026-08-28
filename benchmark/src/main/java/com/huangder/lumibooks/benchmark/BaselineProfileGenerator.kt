package com.huangder.lumibooks.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        BenchmarkBooks.prepareApp(device)
        BenchmarkBooks.all.forEach { fixture ->
            BenchmarkBooks.ensureImported(device, fixture)
        }
        baselineProfileRule.collect(BenchmarkBooks.PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()
            BenchmarkBooks.showBookshelf(device)
            BenchmarkBooks.scrollBookshelf(device)
            BenchmarkBooks.openFromBookshelf(device, BenchmarkBooks.txtLarge)
            BenchmarkBooks.awaitReaderSettled(device)
            repeat(12) { BenchmarkBooks.turnNextPage(device) }
            BenchmarkBooks.closeReader(device, BenchmarkBooks.txtLarge)
            BenchmarkBooks.openFromBookshelf(device, BenchmarkBooks.epubLarge)
            BenchmarkBooks.awaitReaderSettled(device)
            repeat(12) { BenchmarkBooks.turnNextPage(device) }
        }
    }
}
