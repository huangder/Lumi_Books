package com.huangder.lumibooks.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationBackdropPolicyTest {
    @Test fun neverRecordCurrentIncomingOrOutgoingReader() {
        assertFalse(navigationBackdropCaptureAllowed(Screen.Reader.route, emptyList()))
        assertFalse(navigationBackdropCaptureAllowed(Screen.Reader.route, listOf(Screen.Bookshelf.route)))
        // popBackStack changes the current route before the departing reader leaves the scene.
        assertFalse(navigationBackdropCaptureAllowed(Screen.Bookshelf.route,
            listOf(Screen.Bookshelf.route, Screen.Reader.route)))
        assertTrue(navigationBackdropCaptureAllowed(Screen.Bookshelf.route, listOf(Screen.Bookshelf.route)))
    }
}
