package com.huangder.lumibooks.data.local

import com.huangder.lumibooks.domain.model.SelectionMenuAction
import com.huangder.lumibooks.domain.model.SelectionMenuAction.COPY
import com.huangder.lumibooks.domain.model.SelectionMenuAction.DICTIONARY
import com.huangder.lumibooks.domain.model.SelectionMenuAction.NOTE
import com.huangder.lumibooks.domain.model.SelectionMenuAction.SEARCH
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionMenuSettingsJsonTest {
    private val everyAction = SelectionMenuAction.entries

    @Test fun currentFormatRoundTripsOrderAndVisibility() {
        val settings = SelectionMenuSettings().moveBy(COPY, -7).moveBy(SEARCH, -2).withVisibility(NOTE, false)
        val encoded = encodeSelectionMenuSettings(settings)
        assertEquals(settings, decodeSelectionMenuSettings(encoded))

        val json = JSONObject(encoded)
        val order = json.getJSONArray("order")
        assertEquals(settings.order.map { it.storageKey }, (0 until order.length()).map(order::getString))
        val visibility = json.getJSONObject("visibility")
        assertEquals(everyAction.map { it.storageKey }.toSet(), visibility.keys().asSequence().toSet())
        assertFalse(visibility.getBoolean("note"))
        assertTrue(visibility.getBoolean("copy"))
    }

    @Test fun legacyFlatVisibilityUsesTheDefaultOrder() {
        val settings = decodeSelectionMenuSettings("""{"note": false, "copy": true, "dictionary": false}""")
        assertEquals(everyAction, settings.order)
        assertFalse(settings.isVisible(NOTE))
        assertFalse(settings.isVisible(DICTIONARY))
        assertTrue(settings.isVisible(COPY))
        assertTrue(settings.isVisible(SEARCH))
    }

    @Test fun missingEmptyOrBrokenDataYieldsDefaults() {
        for (raw in listOf(null, "", "   ", "{", "[]", "not json", "42", "{}")) {
            assertEquals("raw=$raw", SelectionMenuSettings(), decodeSelectionMenuSettings(raw))
        }
    }

    @Test fun unknownKeysAndNonBooleanValuesAreIgnored() {
        val raw = """{"order": ["translate", "copy", 3, null, "note"],
            "visibility": {"translate": false, "copy": "false", "note": 0, "search": false}}"""
        val settings = decodeSelectionMenuSettings(raw)
        assertEquals(listOf(COPY, NOTE), settings.order.take(2))
        assertTrue(settings.isVisible(COPY))
        assertTrue(settings.isVisible(NOTE))
        assertFalse(settings.isVisible(SEARCH))
        val legacy = decodeSelectionMenuSettings("""{"translate": false, "note": "no"}""")
        assertEquals(SelectionMenuSettings(), legacy)
    }

    @Test fun missingActionsAreAppendedAndDuplicatesDropped() {
        val settings = decodeSelectionMenuSettings("""{"order": ["search", "copy", "search"], "visibility": {}}""")
        assertEquals(listOf(SEARCH, COPY) + everyAction.filter { it != SEARCH && it != COPY }, settings.order)
        assertTrue(everyAction.all(settings::isVisible))
        val orderOnly = decodeSelectionMenuSettings("""{"order": ["note"]}""")
        assertEquals(NOTE, orderOnly.order.first())
        assertTrue(everyAction.all(orderOnly::isVisible))
    }

    @Test fun storedFullyHiddenMenuKeepsCopyVisible() {
        val hidden = everyAction.joinToString(prefix = "{", postfix = "}") { "\"${it.storageKey}\": false" }
        for (raw in listOf(hidden, """{"order": ["note"], "visibility": $hidden}""")) {
            val settings = decodeSelectionMenuSettings(raw)
            assertEquals(listOf(COPY), everyAction.filter(settings::isVisible))
        }
    }
}
