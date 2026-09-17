package com.huangder.lumibooks.ui.animation

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BookReaderTransitionTest {
    @Test
    fun `shared key is stable per book`() {
        assertEquals("book-window-abc", BookReaderMotion.sharedKey("abc"))
    }

    @Test
    fun `control point stays close to the straight path`() {
        val start = Offset(100f, 1800f)
        val end = Offset(540f, 1200f)
        val control = BookReaderMotion.controlPoint(
            start = start,
            end = end,
            screenCenter = end,
            offsetPx = 28f
        )
        val midpoint = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
        val distanceFromMidpoint = hypot(
            control.x - midpoint.x,
            control.y - midpoint.y
        )

        assertEquals(28f, distanceFromMidpoint, 0.001f)
        assertTrue(distanceFromMidpoint >= 20f)
        assertTrue(distanceFromMidpoint <= 40f)
    }

    @Test
    fun `quadratic path starts and ends at the requested centers`() {
        val start = Offset(100f, 1800f)
        val end = Offset(540f, 1200f)
        val control = Offset(200f, 1200f)

        assertEquals(start, BookReaderMotion.quadraticPoint(start, control, end, 0f))
        assertEquals(end, BookReaderMotion.quadraticPoint(start, control, end, 1f))
    }

    @Test
    fun `motion uses independent position and size timing`() {
        assertEquals(650, BookReaderMotion.WINDOW_DURATION_MS)
        assertEquals(600, BookReaderMotion.POSITION_DURATION_MS)
        assertEquals(650, BookReaderMotion.SIZE_DURATION_MS)
        assertTrue(BookReaderMotion.POSITION_DURATION_MS < BookReaderMotion.SIZE_DURATION_MS)
    }

    @Test
    fun `cover fit keeps the source aspect instead of stretching`() {
        val source = androidx.compose.ui.geometry.Rect(0f, 0f, 300f, 400f)
        val scale = BookReaderMotion.coverFitScale(
            source = source,
            windowWidth = 1080f,
            windowHeight = 2400f
        )

        assertEquals(3.6f, scale, 0.001f)
        assertEquals(300f / 400f, (source.width * scale) / (source.height * scale), 0.001f)
    }

    @Test
    fun `edge blend is zero before the window expands`() {
        val source = androidx.compose.ui.geometry.Rect(0f, 0f, 300f, 400f)
        assertEquals(
            0f,
            BookReaderMotion.edgeBlend(
                source = source,
                windowWidth = source.width,
                windowHeight = source.height
            ),
            0.001f
        )
    }

    @Test
    fun `library fallback parameters remain subtle`() {
        assertTrue(BookReaderMotion.LIBRARY_DIM_ALPHA in 0.2f..0.4f)
        assertTrue(BookReaderMotion.LIBRARY_SCALE in 0.98f..1f)
        assertEquals(12f, BookReaderMotion.LIBRARY_BLUR_DP, 0.001f)
    }

    @Test
    fun `phase machine allows open loading ready reader close path`() {
        assertTrue(
            BookReaderTransitionPhase.Library.canTransitionTo(
                BookReaderTransitionPhase.Opening
            )
        )
        assertTrue(
            BookReaderTransitionPhase.Opening.canTransitionTo(
                BookReaderTransitionPhase.ReaderLoading
            )
        )
        assertTrue(
            BookReaderTransitionPhase.ReaderLoading.canTransitionTo(
                BookReaderTransitionPhase.Ready
            )
        )
        assertTrue(
            BookReaderTransitionPhase.Ready.canTransitionTo(
                BookReaderTransitionPhase.Reader
            )
        )
        assertTrue(
            BookReaderTransitionPhase.Reader.canTransitionTo(
                BookReaderTransitionPhase.Closing
            )
        )
        assertTrue(
            BookReaderTransitionPhase.Closing.canTransitionTo(
                BookReaderTransitionPhase.Library
            )
        )
        assertFalse(
            BookReaderTransitionPhase.Library.canTransitionTo(
                BookReaderTransitionPhase.Reader
            )
        )
    }
}
