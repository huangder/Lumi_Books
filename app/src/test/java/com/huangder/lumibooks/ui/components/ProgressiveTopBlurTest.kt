package com.huangder.lumibooks.ui.components

import com.huangder.lumibooks.ui.settings.detailTitleCollapseFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class ProgressiveTopBlurTest {

    // ── 滚动进度 → 模糊强度 ──────────────────────────────────────

    @Test
    fun scrollAtRestProducesZeroStrength() {
        assertEquals(0f, blurStrengthAtScroll(scrollPx = 0f, rampPx = 64f), 0.0001f)
    }

    @Test
    fun strengthIsMonotonicAndSaturatesAtTheRampDistance() {
        val rampPx = 64f
        var previous = -1f
        var scroll = 0f
        while (scroll <= rampPx * 1.5f) {
            val strength = blurStrengthAtScroll(scroll, rampPx)
            assertTrue("strength decreased at $scroll", strength >= previous - 0.0001f)
            assertTrue(strength in 0f..1f)
            previous = strength
            scroll += 2f
        }
        assertEquals(1f, blurStrengthAtScroll(rampPx, rampPx), 0.0001f)
        assertEquals(1f, blurStrengthAtScroll(rampPx * 3f, rampPx), 0.0001f)
    }

    @Test
    fun unmeasuredTitleHeightFallsBackToZeroStrengthInsteadOfNaN() {
        // 首次布局前大标题高度为 0，进度函数必须安全返回 0 而不是 NaN。
        val strength = blurStrengthAtScroll(scrollPx = 32f, rampPx = 0f)
        assertFalse(strength.isNaN())
        assertEquals(0f, strength, 0.0001f)
    }

    @Test
    fun strengthSanitizesNonFiniteAndOutOfRangeValues() {
        assertEquals(0f, progressiveBlurStrength(Float.NaN), 0.0001f)
        assertEquals(0f, progressiveBlurStrength(-3f), 0.0001f)
        assertEquals(1f, progressiveBlurStrength(7f), 0.0001f)
        assertEquals(0.4f, progressiveBlurStrength(0.4f), 0.0001f)
    }

    @Test
    fun quantizationIsBoundedAndSnapsToDiscreteSteps() {
        assertEquals(0f, quantizeBlurStrength(0f), 0.0001f)
        assertEquals(1f, quantizeBlurStrength(1f), 0.0001f)
        assertEquals(0f, quantizeBlurStrength(Float.NaN), 0.0001f)
        for (step in 0..PROGRESSIVE_BLUR_STEPS) {
            val raw = step.toFloat() / PROGRESSIVE_BLUR_STEPS
            assertEquals(raw, quantizeBlurStrength(raw), 0.0001f)
        }
        // 任意输入都被吸附到 1/24 的网格上，且不会超出 [0, 1]。
        for (sample in 0..100) {
            val snapped = quantizeBlurStrength(sample / 100f)
            val grid = snapped * PROGRESSIVE_BLUR_STEPS
            assertEquals(grid.toDouble(), grid.roundToInt().toDouble(), 0.0001)
            assertTrue(snapped in 0f..1f)
        }
    }

    @Test
    fun quantizationDoesNotChangeTheVisibleStrengthByMoreThanOneStep() {
        val tolerance = 1f / PROGRESSIVE_BLUR_STEPS
        for (sample in 0..100) {
            val raw = sample / 100f
            assertTrue(kotlin.math.abs(quantizeBlurStrength(raw) - raw) <= tolerance + 0.0001f)
        }
    }

    @Test
    fun tintFallsSmoothlyFromOpaqueToTransparentWithoutEarlyCutoff() {
        assertEquals(1f, progressiveTintAlpha(0f), 0.0001f)
        assertEquals(0.5f, progressiveTintAlpha(0.5f), 0.0001f)
        assertEquals(0f, progressiveTintAlpha(1f), 0.0001f)
        assertEquals(0f, progressiveTintAlpha(Float.NaN), 0.0001f)
        var previous = 1f
        for (step in 0..200) {
            val alpha = progressiveTintAlpha(step / 200f)
            assertTrue(alpha in 0f..1f && alpha <= previous)
            previous = alpha
        }
    }

    @Test
    fun bottomAndRtlEdgesReverseOnlyTheGradient() {
        for (step in 0..100) {
            val fraction = step / 100f
            assertEquals(fraction, progressiveEdgeFraction(fraction, false), 0.0001f)
            assertEquals(1f - fraction, progressiveEdgeFraction(fraction, true), 0.0001f)
        }
    }

    @Test
    fun fallbackReachesActualOpaquePageColorEvenWhenCallerPassesATranslucentTint() {
        val tint = androidx.compose.ui.graphics.Color(0xFF123456).copy(alpha = 0.08f)
        assertEquals(tint.copy(alpha = 1f), progressiveScrimColor(tint, 0f))
        assertEquals(tint.copy(alpha = 0f), progressiveScrimColor(tint, 1f))
        assertEquals(0.5f, progressiveScrimColor(tint, 0.5f).alpha, 0.005f)
        assertEquals(tint.copy(alpha = 1f), progressiveScrimColor(tint, 0.4f, 0.4f))
        assertEquals(tint.copy(alpha = 0f), progressiveScrimColor(tint, 1f, 0.4f))
    }

    // ── 大标题 → 小标题过渡 ─────────────────────────────────────

    @Test
    fun largeTitleStartsAtFullSizeAndEndsAtTheSmallTitleSize() {
        assertEquals(1f, largeTitleScale(0f), 0.0001f)
        assertEquals(LARGE_TITLE_MIN_SCALE, largeTitleScale(1f), 0.0001f)
        assertEquals(1f, largeTitleAlpha(0f), 0.0001f)
        assertEquals(0f, largeTitleAlpha(LARGE_TITLE_FADE_END), 0.0001f)
        assertEquals(0f, largeTitleAlpha(1f), 0.0001f)
    }

    @Test
    fun largeTitleShrinksAndFadesMonotonically() {
        var previousScale = largeTitleScale(0f)
        var previousAlpha = largeTitleAlpha(0f)
        var fraction = 0f
        while (fraction <= 1f) {
            val scale = largeTitleScale(fraction)
            val alpha = largeTitleAlpha(fraction)
            assertTrue(scale <= previousScale + 0.0001f)
            assertTrue(alpha <= previousAlpha + 0.0001f)
            assertTrue(scale in LARGE_TITLE_MIN_SCALE..1f)
            assertTrue(alpha in 0f..1f)
            previousScale = scale
            previousAlpha = alpha
            fraction += 0.005f
        }
    }

    @Test
    fun smallTitleIsHiddenUntilTheLargeTitleStartsLeaving() {
        assertEquals(0f, smallTitleAlpha(0f), 0.0001f)
        assertEquals(0f, smallTitleAlpha(SMALL_TITLE_FADE_START), 0.0001f)
        assertEquals(1f, smallTitleAlpha(1f), 0.0001f)
        assertEquals(1f, smallTitleScale(1f), 0.0001f)
        assertTrue(smallTitleScale(0f) < 1f)

        var previous = 0f
        var fraction = 0f
        while (fraction <= 1f) {
            val alpha = smallTitleAlpha(fraction)
            assertTrue(alpha + 0.0001f >= previous)
            previous = alpha
            fraction += 0.005f
        }
    }

    @Test
    fun titlesNeverContradictEachOtherMidTransition() {
        // 小标题出现时大标题已经开始淡出，两者不会同时全量显示同一文案。
        val fraction = 0.6f
        assertTrue(smallTitleAlpha(fraction) > 0f)
        assertTrue(largeTitleAlpha(fraction) < 1f)
    }

    @Test
    fun progressiveBlurStartsAtApi33WhileOlderHardwareUsesTheGradient() {
        for (sdk in listOf(26, 30, 31, 32, 33, 35, 37)) {
            assertEquals(sdk >= 33, progressiveBlurEnabled(true, false, sdkInt = sdk))
            assertEquals(sdk < 33, progressiveTopScrimEnabled(true, false, sdkInt = sdk))
        }
    }

    @Test
    fun capabilityAndTransparencyFailuresFallBackAndEInkKeepsPriority() {
        for (sdk in listOf(26, 32, 33, 37)) {
            for (supported in listOf(false, true)) {
                for (reduced in listOf(false, true)) {
                    assertFalse(progressiveBlurEnabled(supported, true, reduced, sdk))
                    assertFalse(progressiveTopScrimEnabled(supported, true, reduced, sdk))
                    val expected = supported && !reduced && sdk >= 33
                    assertEquals(expected, progressiveBlurEnabled(supported, false, reduced, sdk))
                    assertEquals(!expected, progressiveTopScrimEnabled(supported, false, reduced, sdk))
                }
            }
        }
    }

    /**
     * 脚手架实际使用的映射：滚动位移经大标题折叠进度换算成模糊强度，
     * 与标题过渡共用同一进度源。
     */
    private fun blurStrengthAtScroll(scrollPx: Float, rampPx: Float): Float {
        val fraction = detailTitleCollapseFraction(
            titleTop = -scrollPx,
            titleHeight = rampPx,
            viewportTop = 0f
        )
        return progressiveBlurStrength(fraction)
    }

}
