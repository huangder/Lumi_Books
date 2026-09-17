package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class CurlAnimationTimingTest {
    @Test
    fun settleDurationScalesWithTravelAtConstantSpeed() {
        // 一次完整翻页 = 折角 tip 走 2 屏宽（右缘 → -viewWidth）≈ 配置时长。
        assertEquals(800, curlSettleDurationMs(800, 2160f, 1080f))
        assertEquals(400, curlSettleDurationMs(800, 1080f, 1080f))
        assertEquals(300, curlSettleDurationMs(300, 2160f, 1080f))
        // 极短行程有下限，避免 0ms 瞬移。
        assertEquals(120, curlSettleDurationMs(800, 100f, 1080f))
        // 配置时长本身就小于下限时按配置时长走。
        assertEquals(120, curlSettleDurationMs(120, 2160f, 1080f))
    }

    @Test
    fun expeditedDurationUsesThirtyPercentWithinBounds() {
        assertEquals(120, curlExpeditedDurationMs(300))
        assertEquals(240, curlExpeditedDurationMs(800))
        assertEquals(240, curlExpeditedDurationMs(1200))
    }
}
