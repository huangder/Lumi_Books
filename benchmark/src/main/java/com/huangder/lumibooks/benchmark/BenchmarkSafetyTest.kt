package com.huangder.lumibooks.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkSafetyTest {
    @Test
    fun homePreflightDoesNotSendInput() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        BenchmarkBooks.verifyHomeReadyWithoutInput(device)
    }
}
