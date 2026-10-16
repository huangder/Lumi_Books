package com.huangder.lumibooks.util.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownLocatorMigrationTest {
    @Test
    fun duplicateQuoteUsesNearestOldPosition() {
        val text = "first target middle target end"
        val old = MarkdownLocatorMigration.locator(text, MarkdownLocatorMigration.Match(20, 26))
        val match = MarkdownLocatorMigration.match(text, "target", old, 20)
        assertEquals(20, match?.start)
    }

    @Test
    fun prefixAndSuffixDisambiguateChangedText() {
        val old = "before target after"
        val locator = MarkdownLocatorMigration.locator(old, MarkdownLocatorMigration.Match(7, 13))
        val updated = "before changed target after"
        val match = MarkdownLocatorMigration.match(updated, "target", locator, 7)
        assertEquals(15, match?.start)
    }

    @Test
    fun missingQuoteIsNotPaintedAtOldOffset() {
        val locator = MarkdownLocatorMigration.locator("old text", MarkdownLocatorMigration.Match(0, 3))
        assertNull(MarkdownLocatorMigration.match("new text", "old", locator, 0))
    }
}
