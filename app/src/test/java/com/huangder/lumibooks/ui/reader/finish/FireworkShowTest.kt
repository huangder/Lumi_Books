package com.huangder.lumibooks.ui.reader.finish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FireworkShowTest {
    @Test
    fun sameSeedProducesSameShow() {
        val a = FireworkShow.generate(42)
        val b = FireworkShow.generate(42)
        val pa = FloatArray(2)
        val pb = FloatArray(2)
        for (burst in 0 until a.burstCount) {
            assertEquals(a.burstTime(burst), b.burstTime(burst), 0f)
            a.sparkPosition(burst, 3, 0.5f, 1080f, 2400f, pa)
            b.sparkPosition(burst, 3, 0.5f, 1080f, 2400f, pb)
            assertEquals(pa[0], pb[0], 0f)
            assertEquals(pa[1], pb[1], 0f)
        }
    }

    @Test
    fun particleCountStaysWithinBudget() {
        val show = FireworkShow.generate(7, burstCount = 8, particlesPerBurst = 500)
        assertTrue(show.burstCount * show.particlesPerBurst <= FireworkShow.MAX_PARTICLES)
    }

    @Test
    fun showEndsAfterLastBurstFades() {
        val show = FireworkShow.generate(1)
        assertTrue(show.durationSec < 4f)
        for (burst in 0 until show.burstCount) {
            assertTrue(show.launchTime(burst) < show.burstTime(burst))
            assertEquals(0f, show.burstAlpha(show.burstAge(burst, show.durationSec)), 1e-3f)
        }
    }

    @Test
    fun rocketOnlyExistsBeforeItsBurst() {
        val show = FireworkShow.generate(3)
        val out = FloatArray(2)
        assertTrue(show.rocketPosition(0, show.launchTime(0) + 0.01f, out))
        assertFalse(show.rocketPosition(0, show.burstTime(0) + 0.01f, out))
    }
}
