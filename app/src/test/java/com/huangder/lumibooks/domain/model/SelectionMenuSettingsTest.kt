package com.huangder.lumibooks.domain.model

import org.junit.Assert.*
import org.junit.Test

class SelectionMenuSettingsTest {
    @Test fun lastVisibleActionCannotBeHidden() {
        var settings = SelectionMenuSettings()
        SelectionMenuAction.entries.filter { it != SelectionMenuAction.COPY }.forEach {
            settings = settings.withVisibility(it, false)
        }
        assertEquals(listOf(SelectionMenuAction.COPY), settings.visibleActions())
        assertEquals(settings, settings.withVisibility(SelectionMenuAction.COPY, false))
    }

    @Test fun reorderingSurvivesHidingAndShowingAnAction() {
        val settings = SelectionMenuSettings().move(SelectionMenuAction.EXCERPT, -1)
        val hidden = settings.withVisibility(SelectionMenuAction.EXCERPT, false)
        assertFalse(hidden.visibleActions().contains(SelectionMenuAction.EXCERPT))
        assertEquals(settings, hidden.withVisibility(SelectionMenuAction.EXCERPT, true))
        assertNotEquals(SelectionMenuSettings(), settings)
    }

    @Test fun corruptOrObsoletePreferencesStillLeaveAnAction() {
        val settings = SelectionMenuSettings.fromStored(
            listOf("copy", "copy", "obsolete"),
            SelectionMenuAction.entries.associate { it.key to false }
        )
        assertEquals(SelectionMenuAction.COPY, settings.order.first())
        assertEquals(SelectionMenuAction.entries.size, settings.order.distinct().size)
        assertEquals(listOf(SelectionMenuAction.COPY), settings.visibleActions())
    }

    @Test fun unavailableTxtActionFallsBackToCopyInOtherFormats() {
        val settings = SelectionMenuSettings.fromStored(emptyList(),
            SelectionMenuAction.entries.associate { it.key to (it == SelectionMenuAction.REPLACE) })
        assertEquals(listOf(SelectionMenuAction.REPLACE), settings.visibleActions(isTxt = true))
        assertEquals(listOf(SelectionMenuAction.COPY), settings.visibleActions(isTxt = false))
    }
}
