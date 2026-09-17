package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderCornerMargins
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderCornerLayoutTest {
    /** 章节信息只占一行，过长由 Compose 的 Ellipsis 截断。 */
    @Test
    fun chapterInfoStaysOnASingleLine() {
        assertEquals(1, readerCornerContentMaxLines(ReaderCornerContent.CHAPTER_INFO))
    }

    @Test
    fun otherCornerContentKeepsTwoLineRoom() {
        listOf(
            ReaderCornerContent.NONE,
            ReaderCornerContent.BOOK_PROGRESS,
            ReaderCornerContent.PAGE_NUMBER,
            ReaderCornerContent.BATTERY,
            ReaderCornerContent.TIME
        ).forEach { content ->
            assertEquals(content.name, 2, readerCornerContentMaxLines(content))
        }
    }

    /** 没调过的边保持旧版位置：左右跟正文，上下用固定值。 */
    @Test
    fun unsetCornerMarginsKeepLegacyGeometry() {
        val margins = ReaderCornerMargins()

        assertEquals(44f, margins.resolvedLeftDp(44f))
        assertEquals(44f, margins.resolvedRightDp(44f))
        assertEquals(ReaderCornerMargins.DEFAULT_TOP_DP, margins.resolvedTopDp())
        assertEquals(ReaderCornerMargins.DEFAULT_BOTTOM_DP, margins.resolvedBottomDp())
    }

    /** 用户调过的边用自定义值，不再跟随正文。 */
    @Test
    fun setCornerMarginsOverrideBodyMargins() {
        val margins = ReaderCornerMargins(leftDp = 12f, rightDp = 18f, topDp = 40f, bottomDp = 52f)

        assertEquals(12f, margins.resolvedLeftDp(38f))
        assertEquals(18f, margins.resolvedRightDp(38f))
        assertEquals(40f, margins.resolvedTopDp())
        assertEquals(52f, margins.resolvedBottomDp())
    }

    /** 单边设置只影响这一边，其余仍跟随正文 / 旧版默认值。 */
    @Test
    fun partialCornerMarginsOnlyOverrideTouchedEdge() {
        val margins = ReaderCornerMargins(bottomDp = 4f)

        assertEquals(38f, margins.resolvedLeftDp(38f))
        assertEquals(38f, margins.resolvedRightDp(38f))
        assertEquals(ReaderCornerMargins.DEFAULT_TOP_DP, margins.resolvedTopDp())
        assertEquals(4f, margins.resolvedBottomDp())
    }

    @Test
    fun cornerMarginClampMatchesSliderRanges() {
        assertEquals(0f, ReaderCornerMargins.clampHorizontal(-10f))
        assertEquals(80f, ReaderCornerMargins.clampHorizontal(200f))
        assertEquals(0f, ReaderCornerMargins.clampVertical(-10f))
        assertEquals(120f, ReaderCornerMargins.clampVertical(200f))
    }
}
