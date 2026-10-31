package com.huangder.lumibooks.domain.model

import com.huangder.lumibooks.domain.model.SelectionMenuAction.COPY
import com.huangder.lumibooks.domain.model.SelectionMenuAction.EXCERPT
import com.huangder.lumibooks.domain.model.SelectionMenuAction.HIGHLIGHT
import com.huangder.lumibooks.domain.model.SelectionMenuAction.NOTE
import com.huangder.lumibooks.domain.model.SelectionMenuAction.REPLACE
import com.huangder.lumibooks.domain.model.SelectionMenuAction.UNDERLINE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionMenuSettingsTest {
    private val everyAction = SelectionMenuAction.entries

    @Test fun storageKeysAreStable() {
        assertEquals(
            listOf("highlight", "underline", "note", "dictionary", "excerpt", "search", "web_search", "copy", "replace"),
            everyAction.map { it.storageKey }
        )
        everyAction.forEach { assertSame(it, SelectionMenuAction.fromStorageKey(it.storageKey)) }
        assertEquals(null, SelectionMenuAction.fromStorageKey("translate"))
    }

    @Test fun defaultsShowEveryActionInDeclarationOrder() {
        val settings = SelectionMenuSettings()
        assertEquals(everyAction, settings.order)
        assertTrue(everyAction.all(settings::isVisible))
        assertEquals(SelectionMenuSettings(), settings)
        assertEquals(SelectionMenuSettings().hashCode(), settings.hashCode())
    }

    @Test fun theLastVisibleActionCannotBeHidden() {
        var settings = SelectionMenuSettings()
        everyAction.filter { it != NOTE }.forEach { settings = settings.withVisibility(it, false) }
        assertEquals(listOf(NOTE), everyAction.filter(settings::isVisible))
        assertFalse(settings.canHide(NOTE))
        assertSame(settings, settings.withVisibility(NOTE, false))
        val restored = settings.withVisibility(COPY, true)
        assertTrue(restored.isVisible(COPY))
        assertTrue(restored.canHide(NOTE))
    }

    @Test fun movesStopAtBothEnds() {
        val settings = SelectionMenuSettings()
        assertSame(settings, settings.moveBy(HIGHLIGHT, -1))
        assertSame(settings, settings.moveBy(REPLACE, 1))
        assertSame(settings, settings.moveBy(NOTE, 20))
        val moved = settings.moveBy(NOTE, -1)
        assertEquals(listOf(HIGHLIGHT, NOTE, UNDERLINE), moved.order.take(3))
        assertEquals(everyAction.size, moved.order.distinct().size)
        assertEquals(REPLACE, settings.moveBy(COPY, 1).order[7])
        assertEquals(COPY, settings.moveBy(COPY, 1).order.last())
    }

    @Test fun reorderingProducesADistinctValue() {
        val settings = SelectionMenuSettings()
        val reordered = settings.moveBy(COPY, -7)
        assertNotEquals(settings, reordered)
        assertEquals(COPY, reordered.order.first())
        assertEquals(settings, reordered.moveBy(COPY, 7))
        assertNotEquals(settings, settings.withVisibility(EXCERPT, false))
    }

    @Test fun withOrderCompletesAndDeduplicates() {
        val settings = SelectionMenuSettings().withOrder(listOf(COPY, NOTE, COPY))
        assertEquals(listOf(COPY, NOTE) + everyAction.filter { it != COPY && it != NOTE }, settings.order)
    }

    @Test fun replaceIsOnlyOfferedForTxtBooks() {
        val settings = SelectionMenuSettings()
        assertFalse(REPLACE in settings.visibleActions(isTxt = false, hasHighlight = false, hasUnderline = false))
        assertEquals(REPLACE, settings.visibleActions(isTxt = true, hasHighlight = false, hasUnderline = false).last())
    }

    @Test fun existingAnnotationsAreNotOfferedAgain() {
        val settings = SelectionMenuSettings()
        val highlighted = settings.visibleActions(isTxt = false, hasHighlight = true, hasUnderline = false)
        assertFalse(HIGHLIGHT in highlighted)
        assertTrue(UNDERLINE in highlighted)
        val underlined = settings.visibleActions(isTxt = false, hasHighlight = false, hasUnderline = true)
        assertTrue(HIGHLIGHT in underlined)
        assertFalse(UNDERLINE in underlined)
    }

    @Test fun visibleActionsFollowTheConfiguredOrderAndVisibility() {
        val settings = SelectionMenuSettings().moveBy(COPY, -7).withVisibility(NOTE, false)
        val actions = settings.visibleActions(isTxt = false, hasHighlight = false, hasUnderline = false)
        assertEquals(COPY, actions.first())
        assertFalse(NOTE in actions)
    }

    @Test fun anEmptyResultFallsBackToCopy() {
        var settings = SelectionMenuSettings()
        everyAction.filter { it != HIGHLIGHT && it != REPLACE }.forEach { settings = settings.withVisibility(it, false) }
        assertEquals(listOf(COPY), settings.visibleActions(isTxt = false, hasHighlight = true, hasUnderline = false))
        assertEquals(listOf(REPLACE), settings.visibleActions(isTxt = true, hasHighlight = true, hasUnderline = false))
    }

    @Test fun creatingFromFullyHiddenDataKeepsCopyVisible() {
        val settings = SelectionMenuSettings.create(emptyList(), everyAction.toSet())
        assertEquals(listOf(COPY), everyAction.filter(settings::isVisible))
        assertEquals(everyAction, settings.order)
    }
}
