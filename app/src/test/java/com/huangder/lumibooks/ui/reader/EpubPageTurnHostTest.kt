package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.util.epub.EpubRenditionLayout
import com.huangder.lumibooks.ui.reader.engine.PageAnimationController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPageTurnHostTest {
    @Test
    fun nextTurnPreparesPageAfterItsVisualDestination() {
        assertEquals(
            EpubPageTarget(4, 7),
            slideLookaheadTarget(
                current = EpubPageTarget(4, 5),
                currentPageCount = 20,
                direction = 1
            )
        )
    }

    @Test
    fun previousTurnPreparesPageBeforeItsVisualDestination() {
        assertEquals(
            EpubPageTarget(4, 3),
            slideLookaheadTarget(
                current = EpubPageTarget(4, 5),
                currentPageCount = 20,
                direction = -1
            )
        )
    }

    @Test
    fun lookaheadStopsAtChapterBoundary() {
        assertNull(
            slideLookaheadTarget(
                current = EpubPageTarget(4, 18),
                currentPageCount = 20,
                direction = 1
            )
        )
        assertNull(
            slideLookaheadTarget(
                current = EpubPageTarget(4, 1),
                currentPageCount = 20,
                direction = -1
            )
        )
    }

    @Test
    fun zeroDirectionDoesNotPrepareAnotherPage() {
        assertNull(
            slideLookaheadTarget(
                current = EpubPageTarget(4, 5),
                currentPageCount = 20,
                direction = 0
            )
        )
    }

    @Test
    fun preparedCallbackUsesRoleAtCompletionTime() {
        assertEquals(
            EpubPageTurnHost.PreloadSlot.NEXT,
            resolvedPreloadSlot(
                role = EpubPageTurnHost.WebViewRole.NEXT,
                callbackGeneration = 14,
                currentGeneration = 14
            )
        )
        assertEquals(
            EpubPageTurnHost.PreloadSlot.PREVIOUS,
            resolvedPreloadSlot(
                role = EpubPageTurnHost.WebViewRole.PREVIOUS,
                callbackGeneration = 14,
                currentGeneration = 14
            )
        )
    }

    @Test
    fun preparedCallbackRejectsPromotedAndReplacedRequests() {
        assertNull(
            resolvedPreloadSlot(
                role = EpubPageTurnHost.WebViewRole.ACTIVE,
                callbackGeneration = 14,
                currentGeneration = 14
            )
        )
        assertNull(
            resolvedPreloadSlot(
                role = EpubPageTurnHost.WebViewRole.NEXT,
                callbackGeneration = 14,
                currentGeneration = 15
            )
        )
    }

    @Test
    fun onlyCurlPreloadsImmutableBitmaps() {
        assertTrue(requiresEpubPreloadBitmap("curl"))
        assertFalse(requiresEpubPreloadBitmap("slide"))
        assertFalse(requiresEpubPreloadBitmap("scroll"))
        assertFalse(requiresEpubPreloadBitmap("none"))
    }

    @Test
    fun verticalPagingUsesLiveWebViewRoles() {
        assertTrue(isLiveEpubPageTransition("slide"))
        assertTrue(isLiveEpubPageTransition("scroll"))
        assertFalse(isLiveEpubPageTransition("curl"))
    }

    @Test
    fun curlPromotesPreparedWebViewRolesAfterCommit() {
        assertTrue(supportsEpubPageRolePromotion("curl"))
        assertTrue(supportsEpubPageRolePromotion("slide"))
        assertTrue(supportsEpubPageRolePromotion("scroll"))
        assertFalse(supportsEpubPageRolePromotion("none"))
    }

    @Test
    fun verticalPagingSupportsReflowableAndPrePaginatedEpub() {
        assertTrue(
            usesNativeEpubPageTurn(false, "scroll", EpubRenditionLayout.REFLOWABLE)
        )
        assertTrue(
            usesNativeEpubPageTurn(false, "scroll", EpubRenditionLayout.PRE_PAGINATED)
        )
        assertFalse(
            usesNativeEpubPageTurn(true, "scroll", EpubRenditionLayout.REFLOWABLE)
        )
        assertFalse(
            usesNativeEpubPageTurn(false, "slide", EpubRenditionLayout.PRE_PAGINATED)
        )
        assertTrue(
            usesNativeEpubPageTurn(false, "curl", EpubRenditionLayout.PRE_PAGINATED)
        )
    }

    @Test
    fun activePagePayloadMustMatchItsPreloadTarget() {
        assertTrue(
            epubPageTargetMatchesPayload(
                requested = EpubPageTarget(2, 7),
                actualPageIndex = 7,
                actualPageCount = 12
            )
        )
        assertFalse(
            epubPageTargetMatchesPayload(
                requested = EpubPageTarget(2, 7),
                actualPageIndex = 6,
                actualPageCount = 12
            )
        )
    }

    @Test
    fun lastPagePreloadTargetMatchesTheResolvedChapterEnd() {
        assertTrue(
            epubPageTargetMatchesPayload(
                requested = EpubPageTarget(2, Int.MAX_VALUE),
                actualPageIndex = 11,
                actualPageCount = 12
            )
        )
        assertFalse(
            epubPageTargetMatchesPayload(
                requested = EpubPageTarget(2, Int.MAX_VALUE),
                actualPageIndex = 10,
                actualPageCount = 12
            )
        )
    }

    @Test
    fun stalePageNotificationsAreRejectedAfterPromotion() {
        assertTrue(isEpubPageNotificationCurrent(notificationSerial = 12L, minimumSerial = 12L))
        assertTrue(isEpubPageNotificationCurrent(notificationSerial = 13L, minimumSerial = 12L))
        assertFalse(isEpubPageNotificationCurrent(notificationSerial = 11L, minimumSerial = 12L))
        assertFalse(isEpubPageNotificationCurrent(notificationSerial = -1L, minimumSerial = 12L))
        assertTrue(isEpubPageNotificationCurrent(notificationSerial = -1L, minimumSerial = null))
    }

    @Test
    fun promotedPageNotificationMustMatchThePromotedTarget() {
        assertTrue(
            epubPageNotificationMatchesTarget(
                target = EpubPageTarget(2, 1),
                chapterIndex = 2,
                pageIndex = 1
            )
        )
        assertFalse(
            epubPageNotificationMatchesTarget(
                target = EpubPageTarget(2, 1),
                chapterIndex = 2,
                pageIndex = 0
            )
        )
    }

    @Test
    fun curlDoesNotQueueAnOppositeTurnDuringHandoff() {
        assertTrue(
            curlTurnDirectionIsCompatible(
                PageAnimationController.Direction.NONE,
                PageAnimationController.Direction.PREV
            )
        )
        assertTrue(
            curlTurnDirectionIsCompatible(
                PageAnimationController.Direction.NEXT,
                PageAnimationController.Direction.NEXT
            )
        )
        assertFalse(
            curlTurnDirectionIsCompatible(
                PageAnimationController.Direction.NEXT,
                PageAnimationController.Direction.PREV
            )
        )
    }

    @Test
    fun centerTapIsForwardedWhileCurlIsSettling() {
        assertTrue(
            isCapturedBusyCurlCenterTap(
                gestureClaimed = false,
                direction = PageAnimationController.Direction.NONE,
                elapsedMs = 120L,
                deltaX = 2f,
                deltaY = 3f
            )
        )
        assertFalse(
            isCapturedBusyCurlCenterTap(
                gestureClaimed = true,
                direction = PageAnimationController.Direction.NEXT,
                elapsedMs = 120L,
                deltaX = -80f,
                deltaY = 3f
            )
        )
    }

    @Test
    fun crossChapterTurnRemainsPotentialBeforeItsTargetIsReady() {
        assertTrue(
            hasPotentialEpubPageTurn(
                current = EpubPageTarget(0, 0),
                currentPageCount = 1,
                chapterCount = 8,
                direction = 1
            )
        )
        assertFalse(
            hasPotentialEpubPageTurn(
                current = EpubPageTarget(7, 0),
                currentPageCount = 1,
                chapterCount = 8,
                direction = 1
            )
        )
    }

    @Test
    fun crossChapterTurnDetectionDistinguishesAdjacentPage() {
        assertFalse(
            isCrossChapterPageTurn(
                current = EpubPageTarget(3, 8),
                target = EpubPageTarget(3, 9)
            )
        )
        assertTrue(
            isCrossChapterPageTurn(
                current = EpubPageTarget(3, 9),
                target = EpubPageTarget(4, 0)
            )
        )
    }

    @Test
    fun crossChapterDestinationPreparesItsFollowingPage() {
        assertEquals(
            EpubPageTarget(4, 1),
            slideLookaheadFromDestination(
                destination = EpubPageTarget(4, 0),
                destinationPageCount = 6,
                direction = 1
            )
        )
        assertEquals(
            EpubPageTarget(3, 4),
            slideLookaheadFromDestination(
                destination = EpubPageTarget(3, 5),
                destinationPageCount = 6,
                direction = -1
            )
        )
    }

    @Test
    fun onePageDestinationHasNoSameChapterLookahead() {
        assertNull(
            slideLookaheadFromDestination(
                destination = EpubPageTarget(4, 0),
                destinationPageCount = 1,
                direction = 1
            )
        )
    }
}
