package com.huangder.lumibooks.ui.navigation

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabLifecycleStateTest {
    @Test fun activeVisiblePageResumesOnlyAsFarAsItsParent() {
        assertEquals(Lifecycle.State.RESUMED,
            mainTabLifecycleState(Lifecycle.State.RESUMED, active = true, visible = true))
        assertEquals(Lifecycle.State.STARTED,
            mainTabLifecycleState(Lifecycle.State.STARTED, active = true, visible = true))
    }

    @Test fun visibleTransitionNeighborStaysStarted() {
        assertEquals(Lifecycle.State.STARTED,
            mainTabLifecycleState(Lifecycle.State.RESUMED, active = false, visible = true))
    }

    @Test fun retainedInvisiblePageStopsAtCreated() {
        assertEquals(Lifecycle.State.CREATED,
            mainTabLifecycleState(Lifecycle.State.RESUMED, active = false, visible = false))
    }

    @Test fun destroyedParentAlwaysDestroysPages() {
        assertEquals(Lifecycle.State.DESTROYED,
            mainTabLifecycleState(Lifecycle.State.DESTROYED, active = true, visible = true))
    }
}
