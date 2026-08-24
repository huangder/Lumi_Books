package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderProgressNavigationTest {
    @Test
    fun `maps global progress at start boundary middle and end`() {
        assertEquals(GlobalProgressTarget(0, 0f), mapGlobalProgress(0f, 4))
        assertEquals(GlobalProgressTarget(1, 0f), mapGlobalProgress(25f, 4))
        assertEquals(GlobalProgressTarget(1, 0.5f), mapGlobalProgress(37.5f, 4))
        assertEquals(GlobalProgressTarget(3, 1f), mapGlobalProgress(100f, 4))
        assertNull(mapGlobalProgress(50f, 0))

        assertEquals(0, pageIndexForChapterFraction(0f, 10))
        assertEquals(5, pageIndexForChapterFraction(0.5f, 10))
        assertEquals(9, pageIndexForChapterFraction(1f, 10))
    }

    @Test
    fun `drag previews locally and commits exactly once on release`() {
        val session = CatalogProgressDragSession()
        val commits = mutableListOf<Float>()

        session.begin(20f)
        assertEquals(25f, session.dragBy(5f))
        assertEquals(35f, session.dragBy(10f))
        assertEquals(emptyList<Float>(), commits)

        session.finish(commits::add)
        session.finish(commits::add)

        assertEquals(listOf(35f), commits)
        assertFalse(session.isActive)
    }

    @Test
    fun `cancel discards preview without committing`() {
        val session = CatalogProgressDragSession()
        val commits = mutableListOf<Float>()

        session.begin(40f)
        session.dragBy(15f)
        session.cancel()

        assertNull(session.finish(commits::add))
        assertEquals(emptyList<Float>(), commits)
    }

    @Test
    fun `new drag starts from latest external progress`() {
        val session = CatalogProgressDragSession()

        session.begin(10f)
        session.dragBy(20f)
        session.cancel()

        assertEquals(72f, session.begin(72f))
        assertEquals(73f, session.dragBy(1f))
    }
}
