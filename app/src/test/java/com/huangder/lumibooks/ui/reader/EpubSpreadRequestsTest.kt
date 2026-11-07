package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EpubSpreadRequestsTest {
    private val opening = request(1, 5)

    @Test fun initialLayoutCanRepeatOpeningLocatorUntilTheFirstCommit() {
        val requests = EpubSpreadOpeningRequest(opening)
        repeat(3) { assertSame(opening, requests.resolve(null)) }
    }

    @Test fun completingSearchDoesNotNavigateBackToOpeningLocation() {
        val requests = EpubSpreadOpeningRequest(opening)
        requests.resolve(null)
        val search = request(2, 12)
        assertSame(search, requests.resolve(search))
        repeat(3) { assertNull(requests.resolve(null)) }
    }

    @Test fun newNavigationStillWorksAfterClearingThePreviousRequest() {
        val requests = EpubSpreadOpeningRequest(opening)
        requests.resolve(request(2, 12))
        assertNull(requests.resolve(null))
        val next = request(3, 7)
        assertSame(next, requests.resolve(next))
        assertNull(requests.resolve(null))
    }

    private fun request(id: Long, chapter: Int) = EpubNavigationRequest(id,
        EpubNavigationOrigin.SEARCH, 0, 0, chapter, EpubNavigationDestination.Page(0), false)
}
