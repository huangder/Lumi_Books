package com.huangder.lumibooks.data.local

import android.app.Application
import com.huangder.lumibooks.domain.model.SelectionMenuAction
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SelectionMenuSettingsJsonTest {
    @Test fun migratesLegacyVisibilityWithoutDroppingNewActions() {
        val settings = decodeSelectionMenuSettings("""{"copy":false,"search":true}""")
        assertFalse(settings.isVisible(SelectionMenuAction.COPY))
        assertTrue(settings.isVisible(SelectionMenuAction.EXCERPT))
    }

    @Test fun orderAndVisibilityRoundTripTogether() {
        val settings = SelectionMenuSettings().move(SelectionMenuAction.COPY, -1)
            .withVisibility(SelectionMenuAction.SEARCH, false)
        assertEquals(settings, decodeSelectionMenuSettings(encodeSelectionMenuSettings(settings)))
    }

    @Test fun malformedStoredDataFallsBackToUsableDefaults() {
        assertEquals(SelectionMenuSettings(), decodeSelectionMenuSettings("broken"))
        assertEquals(SelectionMenuSettings(), decodeSelectionMenuSettings(null))
    }
}
