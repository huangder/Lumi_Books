package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class EpubSpreadEligibilityTest {
    @Test fun minimumWidthIs600DpEvenWhenANarrowWindowIsLandscape() {
        assertFalse(eligible(width = 599, height = 300))
        assertTrue(eligible(width = 600, height = 300))
        assertTrue(eligible(width = 600, height = 600))
        assertTrue(eligible(width = 840, height = 600))
    }

    @Test fun nearSquareBoundaryIncludes25PercentTallerAndRejectsPortraitBeyondIt() {
        assertTrue(eligible(width = 600, height = 750))
        assertFalse(eligible(width = 600, height = 751))
        assertTrue(eligible(width = 800, height = 1000))
        assertFalse(eligible(width = 800, height = 1001))
        assertFalse(eligible(width = 900, height = 1200))
        assertFalse(eligible(width = 1200, height = 1600))
    }

    @Test fun disabledSwitchOtherFormatsInkAndScrollingKeepSinglePage() {
        assertTrue(eligible())
        assertFalse(eligible(enabled = false))
        assertFalse(eligible(isEpub = false))
        assertFalse(eligible(eInk = true))
        assertFalse(eligible(transition = "continuous"))
        assertFalse(eligible(transition = "scroll"))
        for (transition in listOf("none", "slide", "curl", "fade", "wave")) {
            assertTrue(transition, eligible(transition = transition))
        }
    }

    @Test fun unmeasuredOrInvalidWindowDoesNotEnableSpread() {
        assertFalse(eligible(width = 0, height = 0))
        assertFalse(eligible(width = 600, height = 0))
        assertFalse(eligible(width = 600, height = -1))
        assertFalse(eligible(width = -1, height = 600))
    }

    private fun eligible(
        enabled: Boolean = true,
        isEpub: Boolean = true,
        width: Int = 1000,
        height: Int = 800,
        eInk: Boolean = false,
        transition: String = "slide"
    ): Boolean = originalEpubSpreadEligible(enabled, isEpub, width, height, eInk, transition)
}
