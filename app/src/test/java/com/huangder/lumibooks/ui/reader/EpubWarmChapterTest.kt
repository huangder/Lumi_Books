package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class EpubWarmChapterTest {
    @Test fun warmsThreePagesBeforeBothBoundaries() {
        assertEquals(EpubPageTarget(3, 0), epubWarmChapterTarget(2, 7, 10, 5, 1, false))
        assertEquals(EpubPageTarget(1, Int.MAX_VALUE), epubWarmChapterTarget(2, 2, 10, 5, -1, false))
        assertNull(epubWarmChapterTarget(2, 5, 10, 5, 1, false))
    }

    @Test fun shortChapterUsesRecentDirectionAndBookBounds() {
        assertEquals(EpubPageTarget(3, 0), epubWarmChapterTarget(2, 0, 1, 5, 1, false))
        assertEquals(EpubPageTarget(1, Int.MAX_VALUE), epubWarmChapterTarget(2, 0, 1, 5, -1, false))
        assertEquals(EpubPageTarget(1, 0), epubWarmChapterTarget(0, 0, 1, 2, -1, false))
        assertNull(epubWarmChapterTarget(0, 0, 1, 1, 1, false))
    }

    @Test fun fastTurningWidensTheWarmMargin() {
        assertEquals(3, epubWarmEdgePages(0))
        assertEquals(3, epubWarmEdgePages(2))
        assertEquals(9, epubWarmEdgePages(8))
        assertEquals(12, epubWarmEdgePages(40))
        assertNull(epubWarmChapterTarget(2, 12, 20, 5, 1, false))
        assertEquals(EpubPageTarget(3, 0), epubWarmChapterTarget(2, 12, 20, 5, 1, false, epubWarmEdgePages(8)))
        assertEquals(EpubPageTarget(1, Int.MAX_VALUE), epubWarmChapterTarget(2, 7, 20, 5, -1, false, epubWarmEdgePages(8)))
    }

    @Test fun continuousScrollAlreadyPreloadsNeighbourChapters() {
        assertNull(epubWarmChapterTarget(2, 7, 10, 5, 1, true))
    }
}
