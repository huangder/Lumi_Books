package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 连续滚动里“未解码章节”的占位高度估算。
 *
 * 空条目只有章间距那么高时，一屏能塞下二十多章，任何小幅滚动都会跨过四五章：章节标题 / 进度
 * 与正文一起乱跳，可见区域全是空条目时列表总高度接近视口，还会表现为“完全滚不动”。
 */
class ContinuousScrollPlaceholderTest {
    private val viewportHeight = 2_000
    private val gap = 28

    @Test
    fun `placeholder keeps at least half a viewport when nothing was measured`() {
        val height = continuousPlaceholderContentHeightPx(
            rememberedTotalHeightPx = null,
            typicalChapterHeightPx = 0,
            viewportHeightPx = viewportHeight,
            chapterGapPx = gap
        )
        assertTrue("height=$height", height >= viewportHeight / 2)
    }

    @Test
    fun `placeholder reuses this chapter's own measured height`() {
        val measured = 3_000
        val height = continuousPlaceholderContentHeightPx(
            rememberedTotalHeightPx = measured,
            typicalChapterHeightPx = 900,
            viewportHeightPx = viewportHeight,
            chapterGapPx = gap
        )
        assertEquals(measured - gap, height)
    }

    @Test
    fun `placeholder falls back to the typical chapter height`() {
        val height = continuousPlaceholderContentHeightPx(
            rememberedTotalHeightPx = null,
            typicalChapterHeightPx = 3_200,
            viewportHeightPx = viewportHeight,
            chapterGapPx = gap
        )
        assertEquals(3_200 - gap, height)
    }

    @Test
    fun `placeholder is clamped so one outlier cannot blow up the list`() {
        val height = continuousPlaceholderContentHeightPx(
            rememberedTotalHeightPx = 900_000,
            typicalChapterHeightPx = 0,
            viewportHeightPx = viewportHeight,
            chapterGapPx = gap
        )
        assertEquals((viewportHeight * 6f).toInt(), height)
    }

    @Test
    fun `placeholder is zero while the viewport is still unknown`() {
        assertEquals(
            0,
            continuousPlaceholderContentHeightPx(
                rememberedTotalHeightPx = 1_000,
                typicalChapterHeightPx = 1_000,
                viewportHeightPx = 0,
                chapterGapPx = gap
            )
        )
    }

    @Test
    fun `typical height blends new samples instead of following a single chapter`() {
        assertEquals(1_000, continuousTypicalChapterHeight(previousPx = 0, measuredPx = 1_000))
        val blended = continuousTypicalChapterHeight(previousPx = 1_000, measuredPx = 5_000)
        assertTrue("blended=$blended", blended in 1_001 until 5_000)
    }

    @Test
    fun `typical height ignores tiny fluctuations`() {
        assertTrue(continuousTypicalChapterHeightChangedEnough(previousPx = 1_000, nextPx = 1_040))
        assertTrue(!continuousTypicalChapterHeightChangedEnough(previousPx = 1_000, nextPx = 1_005))
        assertTrue(!continuousTypicalChapterHeightChangedEnough(previousPx = 1_000, nextPx = 1_000))
    }
}
