package com.huangder.lumibooks.ui.reader

import androidx.compose.ui.geometry.Offset
import com.huangder.lumibooks.tts.ReaderTtsDock
import com.huangder.lumibooks.tts.ReaderTtsPosition
import org.junit.Assert.*
import org.junit.Test

class ReaderTtsSafeAreaTest {
    private val area = ReaderTtsSafeArea(360f, 24f, 570f, 24f, 1f)

    @Test fun defaultCapsuleClearsFooterAndReservesExpansionAbove() {
        val point = area.point(ReaderTtsPosition())
        assertEquals(24f, point.x, .01f)
        assertEquals(570f, point.y + 48f, .01f)
        assertTrue(area.constrain(Offset(24f, -100f)).y - 58f >= area.top)
    }

    @Test fun rememberedCoordinatesAdaptToRotationAndMovedFooter() {
        val saved = area.save(Offset(140f, 300f), ReaderTtsDock.NONE)
        assertEquals(Offset(140f, 300f), area.point(saved))
        val rotated = ReaderTtsSafeArea(640f, 24f, 280f, 24f, 1f)
        val point = rotated.point(saved)
        assertTrue(point.x >= 0 && point.x + 128f <= rotated.width)
        assertTrue(point.y + 48f <= rotated.bottom)
        assertEquals(saved.xFraction, point.x / rotated.maxX, .0001f)
    }

    @Test fun dockingIsSymmetricAndRestoreClearsScreenEdge() {
        assertEquals(ReaderTtsDock.LEFT, area.dockAt(Offset(24f, 200f)))
        assertEquals(ReaderTtsDock.RIGHT, area.dockAt(Offset(area.maxX - 24f, 200f)))
        assertEquals(ReaderTtsDock.NONE, area.dockAt(Offset(100f, 200f)))
        assertEquals(12f, area.restored(Offset.Zero, ReaderTtsDock.LEFT).x, .01f)
        assertEquals(area.maxX - 12f, area.restored(Offset.Zero, ReaderTtsDock.RIGHT).x, .01f)
    }

    @Test fun sideRailsAndInvalidSavedValuesCannotLoseCapsule() {
        val rails = area.copy(inset = 72f)
        assertEquals(72f, rails.restored(Offset.Zero, ReaderTtsDock.LEFT).x, .01f)
        val point = area.point(ReaderTtsPosition(Float.NaN, Float.POSITIVE_INFINITY, saved = true))
        assertTrue(point.x.isFinite() && point.y.isFinite())
        assertEquals(Offset(0f, area.maxY), point)
    }

    @Test fun releaseGlideIsSmallBoundedAndStillClearsFooter() {
        val point = Offset(100f, 300f)
        assertEquals(point, readerTtsCoastTarget(point, Offset(80f, 0f), area))
        val moderate = readerTtsCoastTarget(point, Offset(400f, 0f), area)
        assertEquals(130f, moderate.x, .01f)
        val fast = readerTtsCoastTarget(point, Offset(8000f, 0f), area)
        assertEquals(148f, fast.x, .01f)
        val bottom = readerTtsCoastTarget(Offset(100f, area.maxY - 10f), Offset(0f, 4000f), area)
        assertEquals(area.maxY, bottom.y, .01f)
        val edge = readerTtsCoastTarget(Offset(40f, 300f), Offset(-400f, 0f), area)
        assertEquals(ReaderTtsDock.LEFT, area.dockAt(edge))
    }
}
