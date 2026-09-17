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

    // ── 渐变遮罩几何 ────────────────────────────────────────────

    @Test
    fun topEdgeIsFullyBlurredByTheHeavyLevel() {
        assertEquals(1f, progressiveBlurLevelAlpha(PROGRESSIVE_BLUR_LEVEL_HEAVY, 0f), 0.0001f)
        assertEquals(1f, progressiveBlurCoverage(0f), 0.0001f)
    }

    @Test
    fun heavyLevelFadesOutAtItsStop() {
        assertEquals(
            0f,
            progressiveBlurLevelAlpha(PROGRESSIVE_BLUR_LEVEL_HEAVY, PROGRESSIVE_BLUR_HEAVY_STOP),
            0.0001f
        )
        assertEquals(
            0f,
            progressiveBlurLevelAlpha(PROGRESSIVE_BLUR_LEVEL_HEAVY, PROGRESSIVE_BLUR_HEAVY_STOP + 0.1f),
            0.0001f
        )
    }

    @Test
    fun lighterLevelsTakeOverWhereTheHeavierLevelEnds() {
        val heavyStop = PROGRESSIVE_BLUR_HEAVY_STOP
        val mediumStop = PROGRESSIVE_BLUR_MEDIUM_STOP

        assertEquals(1f, progressiveBlurLevelAlpha(PROGRESSIVE_BLUR_LEVEL_MEDIUM, heavyStop), 0.0001f)
        assertEquals(0f, progressiveBlurLevelAlpha(PROGRESSIVE_BLUR_LEVEL_MEDIUM, mediumStop), 0.0001f)
        assertEquals(1f, progressiveBlurLevelAlpha(PROGRESSIVE_BLUR_LEVEL_LIGHT, mediumStop), 0.0001f)
        assertEquals(0f, progressiveBlurLevelAlpha(PROGRESSIVE_BLUR_LEVEL_LIGHT, PROGRESSIVE_BLUR_LIGHT_STOP), 0.0001f)
    }

    @Test
    fun everyLevelStaysInsideTheUnitRange() {
        for (step in 0..200) {
            val fraction = step / 200f
            for (level in PROGRESSIVE_BLUR_LEVEL_LIGHT..PROGRESSIVE_BLUR_LEVEL_HEAVY) {
                val alpha = progressiveBlurLevelAlpha(level, fraction)
                assertTrue("level $level alpha $alpha out of range", alpha in 0f..1f)
            }
            assertTrue(progressiveBlurCoverage(fraction) in 0f..1f)
        }
    }

    @Test
    fun transitionBandKeepsTheContentCoveredWithoutSharpGaps() {
        // 过渡带内不允许出现“完全清晰”的缝：覆盖率始终接近 1（最差处约 0.9，位于重/中交叉点）。
        for (step in 0..120) {
            val fraction = step / 200f * PROGRESSIVE_BLUR_MEDIUM_STOP
            assertTrue(
                "coverage $fraction = ${progressiveBlurCoverage(fraction)}",
                progressiveBlurCoverage(fraction) >= 0.88f
            )
        }
    }

    @Test
    fun coverageDissolvesToClearAtTheBottomOfTheBand() {
        val nearBottom = progressiveBlurCoverage(0.999f)
        assertEquals(0f, progressiveBlurCoverage(1f), 0.0001f)
        assertTrue(nearBottom < 0.05f)

        var previous = progressiveBlurCoverage(PROGRESSIVE_BLUR_MEDIUM_STOP)
        var fraction = PROGRESSIVE_BLUR_MEDIUM_STOP
        while (fraction <= 1f) {
            val coverage = progressiveBlurCoverage(fraction)
            assertTrue("coverage increased at $fraction", coverage <= previous + 0.0001f)
            previous = coverage
            fraction += 0.005f
        }
    }

    @Test
    fun blurStrengthWalksFromHeavyToLightToClear() {
        // 用「不透明度过半的最强层级」衡量每一行的实际模糊强度，必须单调递减。
        var previous = strongestActiveLevel(0f)
        assertEquals(PROGRESSIVE_BLUR_LEVEL_HEAVY, previous)
        var fraction = 0f
        while (fraction < 0.999f) {
            fraction = (fraction + 0.002f).coerceAtMost(0.999f)
            val current = strongestActiveLevel(fraction)
            assertTrue("level rose from $previous to $current at $fraction", current <= previous)
            previous = current
        }
        assertEquals(-1, strongestActiveLevel(1f))
    }

    @Test
    fun nonFiniteMaskInputIsTreatedAsClear() {
        for (level in PROGRESSIVE_BLUR_LEVEL_LIGHT..PROGRESSIVE_BLUR_LEVEL_HEAVY) {
            assertEquals(0f, progressiveBlurLevelAlpha(level, Float.NaN), 0.0001f)
        }
        assertEquals(0f, progressiveBlurCoverage(Float.NaN), 0.0001f)
    }

    // ── 可读性 tint ─────────────────────────────────────────────

    @Test
    fun tintIsStrongestAtTheTopAndDissolvesBeforeTheContent() {
        assertEquals(1f, progressiveTintAlpha(0f), 0.0001f)
        assertEquals(0f, progressiveTintAlpha(PROGRESSIVE_BLUR_MEDIUM_STOP), 0.0001f)
        assertEquals(0f, progressiveTintAlpha(1f), 0.0001f)
        assertEquals(0f, progressiveTintAlpha(Float.NaN), 0.0001f)

        var previous = progressiveTintAlpha(0f)
        var fraction = 0f
        while (fraction <= PROGRESSIVE_BLUR_MEDIUM_STOP) {
            val alpha = progressiveTintAlpha(fraction)
            assertTrue(alpha <= previous + 0.0001f)
            previous = alpha
            fraction += 0.005f
        }
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

    // ── 能力门控 ───────────────────────────────────────────────

    @Test
    fun blurRequiresRenderEffectSupportAndIsDisabledInEInk() {
        assertTrue(progressiveBlurEnabled(supported = true, eInkMode = false))
        assertFalse(progressiveBlurEnabled(supported = false, eInkMode = false))
        assertFalse(progressiveBlurEnabled(supported = true, eInkMode = true))
        assertFalse(progressiveBlurEnabled(supported = false, eInkMode = true))
    }

    @Test
    fun scrimOnlyReplacesBlurWhenBlurIsUnavailableAndNotEInk() {
        assertTrue(progressiveTopScrimEnabled(supported = false, eInkMode = false))
        assertFalse(progressiveTopScrimEnabled(supported = true, eInkMode = false))
        assertFalse(progressiveTopScrimEnabled(supported = false, eInkMode = true))
        assertFalse(progressiveTopScrimEnabled(supported = true, eInkMode = true))
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

    private fun strongestActiveLevel(fraction: Float): Int {
        for (level in PROGRESSIVE_BLUR_LEVEL_HEAVY downTo PROGRESSIVE_BLUR_LEVEL_LIGHT) {
            if (progressiveBlurLevelAlpha(level, fraction) > 0.5f) return level
        }
        return -1
    }
}
