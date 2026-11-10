package com.huangder.lumibooks.ui.animation

import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PagePushTransitionTest {
    @Test
    fun regularInAppPageUsesMatchingOpenAndCloseMotion() {
        assertEquals(
            PagePushSpec(PagePushSpeed.STANDARD, animateOpen = true),
            pagePushSpec(true, true, false, false, MotionPreference.STANDARD, eInk = false)
        )
    }

    @Test
    fun taskRootAndNonPageActivitiesDoNotReceivePushTransitions() {
        assertNull(pagePushSpec(true, true, true, false, MotionPreference.STANDARD, false))
        assertNull(pagePushSpec(false, true, false, false, MotionPreference.STANDARD, false))
        assertNull(pagePushSpec(true, false, false, false, MotionPreference.STANDARD, false))
    }

    @Test
    fun restoredPageDoesNotReplayItsEntrance() {
        assertEquals(
            PagePushSpec(PagePushSpeed.STANDARD, animateOpen = false),
            pagePushSpec(true, true, false, true, MotionPreference.STANDARD, eInk = false)
        )
    }

    @Test
    fun fastMotionUsesShorterWindowAnimations() {
        assertEquals(
            PagePushSpec(PagePushSpeed.FAST, animateOpen = true),
            pagePushSpec(true, true, false, false, MotionPreference.FAST, eInk = false)
        )
    }

    @Test
    fun reducedMotionAndEInkDisableWindowAnimations() {
        assertEquals(
            PagePushSpec(PagePushSpeed.NONE, animateOpen = true),
            pagePushSpec(true, true, false, false, MotionPreference.REDUCED, eInk = false)
        )
        assertEquals(
            PagePushSpec(PagePushSpeed.NONE, animateOpen = true),
            pagePushSpec(true, true, false, false, MotionPreference.STANDARD, eInk = true)
        )
    }
}
