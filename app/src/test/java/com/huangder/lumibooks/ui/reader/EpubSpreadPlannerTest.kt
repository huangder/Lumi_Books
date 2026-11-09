package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.util.epub.EpubPagePresentation
import com.huangder.lumibooks.util.epub.EpubPageSide
import com.huangder.lumibooks.util.epub.EpubRenditionLayout
import com.huangder.lumibooks.util.epub.EpubRenditionSpread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubSpreadPlannerTest {
    private fun page(chapter: Int, page: Int = 0) = EpubPageTarget(chapter, page)
    private fun ready(plan: EpubSpreadPlan) = (plan as EpubSpreadPlan.Ready).spread
    private fun fixed(
        side: EpubPageSide? = null,
        spread: EpubRenditionSpread = EpubRenditionSpread.AUTO,
        cover: Boolean = false,
        landscape: Boolean = false
    ) = EpubPagePresentation(
        renditionLayout = EpubRenditionLayout.PRE_PAGINATED,
        preferredSide = side,
        renditionSpread = spread,
        isCover = cover,
        viewportWidth = if (landscape) 1200f else 600f,
        viewportHeight = 900f
    )

    @Test fun duokanFullWindowPageDoesNotConsumeItsReflowableNeighbours() {
        val planner = EpubSpreadPlanner(3, { chapter -> EpubPagePresentation(
            duokanPage = if (chapter == 1) com.huangder.lumibooks.util.epub.EpubDuokanPage.SPREAD_LEFT else null
        ) })
        (0..2).forEach { planner.updatePageCount(it, if (it == 2) 2 else 1) }
        val before = ready(planner.planAt(page(0)))
        assertEquals(listOf(page(0)), before.pages)
        val special = ready(planner.planNext(before))
        assertTrue(special.fullWidth)
        assertEquals(listOf(page(1)), special.pages)
        val after = ready(planner.planNext(special))
        assertEquals(listOf(page(2), page(2, 1)), after.pages)
        assertEquals(special, ready(planner.planPrevious(after)))
        assertEquals(before, ready(planner.planPrevious(special)))
    }

    @Test fun textPairsAcrossChaptersWithoutLoadingTheWholeBook() {
        val planner = EpubSpreadPlanner(100, { EpubPagePresentation() })
        planner.updatePageCount(42, 3)
        assertEquals(EpubSpreadPlan.NeedsPageCount(43), planner.planAt(page(42, 2)))
        planner.updatePageCount(43, 4)
        val spread = ready(planner.planAt(page(42, 2)))
        assertEquals(listOf(page(42, 2), page(43)), spread.pages)
        assertEquals(listOf(page(43, 1), page(43, 2)), ready(planner.planNext(spread)).pages)
    }

    @Test fun entryAnchorsOddPageAndEveryCompletedTurnIsReversible() {
        val planner = EpubSpreadPlanner(1, { EpubPagePresentation() })
        planner.updatePageCount(0, 8)
        val entry = ready(planner.planAt(page(0, 3)))
        assertEquals(listOf(page(0, 3), page(0, 4)), entry.pages)
        val previous = ready(planner.planPrevious(entry))
        assertEquals(listOf(page(0, 1), page(0, 2)), previous.pages)
        val beginning = ready(planner.planPrevious(previous))
        assertEquals(listOf(page(0)), beginning.pages)
        assertEquals(previous, ready(planner.planNext(beginning)))
        assertEquals(entry, ready(planner.planNext(previous)))
        val next = ready(planner.planNext(entry))
        assertEquals(listOf(page(0, 5), page(0, 6)), next.pages)
        assertEquals(entry, ready(planner.planPrevious(next)))
    }

    @Test fun backwardsRequestsOnlyImmediatelyPrecedingUnknownChapter() {
        val planner = EpubSpreadPlanner(80, { EpubPagePresentation() })
        planner.updatePageCount(50, 4)
        val current = ready(planner.planAt(page(50)))
        assertEquals(EpubSpreadPlan.NeedsPageCount(49), planner.planPrevious(current))
        planner.updatePageCount(49, 3)
        assertEquals(listOf(page(49, 1), page(49, 2)), ready(planner.planPrevious(current)).pages)
    }

    @Test fun endSentinelResolvesAfterTheRequestedCountArrives() {
        val planner = EpubSpreadPlanner(3, { EpubPagePresentation() })
        assertEquals(EpubSpreadPlan.NeedsPageCount(2), planner.planAt(page(2, Int.MAX_VALUE)))
        planner.updatePageCount(2, 7)
        val spread = ready(planner.planAt(page(2, Int.MAX_VALUE)))
        assertEquals(listOf(page(2, 6)), spread.pages)
        assertEquals(EpubSpreadPlan.Boundary, planner.planNext(spread))
    }

    @Test fun coverIsStandaloneAndDoesNotConsumeTheFirstTextPage() {
        val planner = EpubSpreadPlanner(3, { if (it == 0) fixed(cover = true) else EpubPagePresentation() })
        planner.updatePageCount(0, 1)
        planner.updatePageCount(1, 3)
        val cover = ready(planner.planAt(page(0)))
        assertTrue(cover.fullWidth)
        assertNull(cover.second)
        val next = ready(planner.planNext(cover))
        assertEquals(listOf(page(1), page(1, 1)), next.pages)
        assertEquals(cover, ready(planner.planPrevious(next)))
    }

    @Test fun fixedAndReflowableNeverShareASpread() {
        val planner = EpubSpreadPlanner(2, { if (it == 0) fixed() else EpubPagePresentation() })
        planner.updatePageCount(0, 1)
        val first = ready(planner.planAt(page(0)))
        assertNull(first.second)
        assertFalse(first.fullWidth)
        planner.updatePageCount(1, 3)
        assertEquals(listOf(page(1), page(1, 1)), ready(planner.planNext(first)).pages)
    }

    @Test fun publisherNoSpreadAndCenterAreAlwaysFullWidth() {
        listOf(fixed(spread = EpubRenditionSpread.NONE), fixed(side = EpubPageSide.CENTER)).forEach { info ->
            val planner = EpubSpreadPlanner(2, { info })
            planner.updatePageCount(0, 1)
            val spread = ready(planner.planAt(page(0)))
            assertTrue(spread.fullWidth)
            assertEquals(EpubPageSide.CENTER, spread.firstSide)
            assertNull(spread.second)
        }
    }

    @Test fun orientationSpecificPublisherSpreadIsRespected() {
        val planner = EpubSpreadPlanner(2, { fixed(spread = EpubRenditionSpread.PORTRAIT) }, isLandscape = true)
        planner.updatePageCount(0, 1)
        assertTrue(ready(planner.planAt(page(0))).fullWidth)

        val portrait = EpubSpreadPlanner(2, { fixed(spread = EpubRenditionSpread.PORTRAIT) }, isLandscape = false)
        portrait.updatePageCount(0, 1)
        portrait.updatePageCount(1, 1)
        assertEquals(listOf(page(0), page(1)), ready(portrait.planAt(page(0))).pages)

        val landscapeOnly = EpubSpreadPlanner(2, { fixed(spread = EpubRenditionSpread.LANDSCAPE) }, isLandscape = false)
        landscapeOnly.updatePageCount(0, 1)
        assertTrue(ready(landscapeOnly.planAt(page(0))).fullWidth)
    }

    @Test fun reflowCoverDoesNotMakeRemainingPagesOfItsChapterFullWidth() {
        val planner = EpubSpreadPlanner(1, { EpubPagePresentation(isCover = true) })
        planner.updatePageCount(0, 5)
        val cover = ready(planner.planAt(page(0)))
        assertTrue(cover.fullWidth)
        val text = ready(planner.planNext(cover))
        assertFalse(text.fullWidth)
        assertEquals(listOf(page(0, 1), page(0, 2)), text.pages)
        assertEquals(cover, ready(planner.planPrevious(text)))
    }

    @Test fun reflowableTerminalPageIgnoresFixedLayoutSideHints() {
        val planner = EpubSpreadPlanner(1, { EpubPagePresentation(preferredSide = EpubPageSide.CENTER) })
        planner.updatePageCount(0, 1)
        val spread = ready(planner.planAt(page(0)))
        assertEquals(EpubPageSide.LEFT, spread.firstSide)
        assertEquals(page(0), spread.left)
        assertFalse(spread.fullWidth)
    }

    @Test fun landscapePageIsFullWidthUnlessPublisherPlacesItOnASide() {
        val landscape = EpubSpreadPlanner(2, { fixed(landscape = true) })
        landscape.updatePageCount(0, 1)
        assertTrue(ready(landscape.planAt(page(0))).fullWidth)

        val sided = EpubSpreadPlanner(2, { fixed(
            side = if (it == 0) EpubPageSide.LEFT else EpubPageSide.RIGHT,
            landscape = true
        ) })
        sided.updatePageCount(0, 1)
        sided.updatePageCount(1, 1)
        val spread = ready(sided.planAt(page(0)))
        assertFalse(spread.fullWidth)
        assertEquals(page(0), spread.left)
        assertEquals(page(1), spread.right)
    }

    @Test fun incompatiblePublisherSidesLeaveABlankInsteadOfPairing() {
        val planner = EpubSpreadPlanner(3, { fixed(side = EpubPageSide.LEFT) })
        planner.updatePageCount(0, 1)
        val first = ready(planner.planAt(page(0)))
        assertEquals(page(0), first.left)
        assertNull(first.right)
        planner.updatePageCount(1, 1)
        val second = ready(planner.planNext(first))
        assertEquals(page(1), second.left)
        assertNull(second.right)
        assertEquals(first, ready(planner.planPrevious(second)))
    }

    @Test fun trailingSideIsStandaloneWithoutRequestingAnotherChapter() {
        val planner = EpubSpreadPlanner(4, { fixed(side = EpubPageSide.RIGHT) })
        planner.updatePageCount(0, 1)
        val spread = ready(planner.planAt(page(0)))
        assertNull(spread.left)
        assertEquals(page(0), spread.right)
        assertFalse(spread.fullWidth)
    }

    @Test fun reverseReadingKeepsLogicalOrderAndMirrorsPhysicalPlacement() {
        val planner = EpubSpreadPlanner(2, { fixed(
            side = if (it == 0) EpubPageSide.RIGHT else EpubPageSide.LEFT
        ) }, reverse = true)
        planner.updatePageCount(0, 1)
        planner.updatePageCount(1, 1)
        val spread = ready(planner.planAt(page(0)))
        assertEquals(listOf(page(0), page(1)), spread.pages)
        assertEquals(page(0), spread.right)
        assertEquals(page(1), spread.left)
    }

    @Test fun changedPaginationDropsLinksThatReferenceOldPages() {
        val planner = EpubSpreadPlanner(1, { EpubPagePresentation() })
        planner.updatePageCount(0, 6)
        val first = ready(planner.planAt(page(0)))
        assertEquals(listOf(page(0, 2), page(0, 3)), ready(planner.planNext(first)).pages)
        planner.updatePageCount(0, 3)
        assertEquals(listOf(page(0, 2)), ready(planner.planNext(first)).pages)
    }

    @Test fun jumpCreatesAFreshAnchorInsteadOfReusingEarlierPairing() {
        val planner = EpubSpreadPlanner(1, { EpubPagePresentation() })
        planner.updatePageCount(0, 7)
        val first = ready(planner.planAt(page(0)))
        planner.planNext(first)
        val jumped = ready(planner.planAt(page(0, 3)))
        assertEquals(listOf(page(0, 3), page(0, 4)), jumped.pages)
        assertEquals(listOf(page(0, 1), page(0, 2)), ready(planner.planPrevious(jumped)).pages)
    }

    @Test fun reverseCanBeUpdatedAfterRuntimeWritingDirectionIsKnown() {
        val planner = EpubSpreadPlanner(1, { EpubPagePresentation() })
        planner.updatePageCount(0, 3)
        assertEquals(EpubPageSide.LEFT, ready(planner.planAt(page(0))).firstSide)
        planner.setReverse(true)
        assertEquals(EpubPageSide.RIGHT, ready(planner.planAt(page(0))).firstSide)
    }
}
