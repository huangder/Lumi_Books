package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEdgeTapModeTest {

    @Test
    fun `each mode maps both screen edges to the expected action`() {
        assertActions(
            ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
            left = ReaderEdgeTapAction.PREVIOUS_PAGE,
            right = ReaderEdgeTapAction.NEXT_PAGE
        )
        assertActions(
            ReaderEdgeTapMode.LEFT_NEXT_RIGHT_PREVIOUS,
            left = ReaderEdgeTapAction.NEXT_PAGE,
            right = ReaderEdgeTapAction.PREVIOUS_PAGE
        )
        assertActions(
            ReaderEdgeTapMode.BOTH_PREVIOUS,
            left = ReaderEdgeTapAction.PREVIOUS_PAGE,
            right = ReaderEdgeTapAction.PREVIOUS_PAGE
        )
        assertActions(
            ReaderEdgeTapMode.BOTH_NEXT,
            left = ReaderEdgeTapAction.NEXT_PAGE,
            right = ReaderEdgeTapAction.NEXT_PAGE
        )
    }

    @Test
    fun `stored keys restore modes and invalid values use the current default`() {
        ReaderEdgeTapMode.entries.forEach { mode ->
            assertEquals(mode, ReaderEdgeTapMode.fromKey(mode.key))
        }
        assertEquals(
            ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
            ReaderEdgeTapMode.fromKey("unsupported")
        )
        assertEquals(
            ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
            ReaderEdgeTapMode.fromKey(null)
        )
    }

    @Test
    fun `vertical mode reverses every configured edge action without changing the mode`() {
        ReaderEdgeTapMode.entries.forEach { mode ->
            val originalLeft = mode.leftAction
            val originalRight = mode.rightAction

            assertEquals(originalLeft.reversed(), mode.leftAction.reversed())
            assertEquals(originalRight.reversed(), mode.rightAction.reversed())
            assertEquals(originalLeft, mode.leftAction)
            assertEquals(originalRight, mode.rightAction)
        }
        assertEquals(ReaderEdgeTapAction.NEXT_PAGE, ReaderEdgeTapMode.BOTH_PREVIOUS.leftAction.reversed())
        assertEquals(ReaderEdgeTapAction.NEXT_PAGE, ReaderEdgeTapMode.BOTH_PREVIOUS.rightAction.reversed())
        assertEquals(ReaderEdgeTapAction.PREVIOUS_PAGE, ReaderEdgeTapMode.BOTH_NEXT.leftAction.reversed())
        assertEquals(ReaderEdgeTapAction.PREVIOUS_PAGE, ReaderEdgeTapMode.BOTH_NEXT.rightAction.reversed())
    }

    @Test
    fun `stored writing mode keys restore and invalid values default horizontal`() {
        assertEquals(ReaderWritingMode.HORIZONTAL, ReaderWritingMode.fromKey("horizontal"))
        assertEquals(ReaderWritingMode.VERTICAL_RL, ReaderWritingMode.fromKey("vertical_rl"))
        assertEquals(ReaderWritingMode.HORIZONTAL, ReaderWritingMode.fromKey(null))
        assertEquals(ReaderWritingMode.HORIZONTAL, ReaderWritingMode.fromKey("unknown"))
        assertFalse(ReaderWritingMode.HORIZONTAL.isVertical)
        assertTrue(ReaderWritingMode.VERTICAL_RL.isVertical)
    }

    @Test
    fun `vertical mode temporarily replaces continuous transition without changing preference`() {
        val preferred = "continuous"

        assertEquals("slide", ReaderWritingMode.VERTICAL_RL.effectivePageTransition(preferred))
        assertEquals("continuous", ReaderWritingMode.HORIZONTAL.effectivePageTransition(preferred))
        assertEquals("curl", ReaderWritingMode.VERTICAL_RL.effectivePageTransition("curl"))
        assertEquals("continuous", preferred)
    }

    @Test
    fun `continuous scroll detection uses the effective writing mode transition`() {
        assertTrue(ReaderWritingMode.HORIZONTAL.usesContinuousScroll("continuous", false))
        assertFalse(ReaderWritingMode.VERTICAL_RL.usesContinuousScroll("continuous", false))
        assertFalse(ReaderWritingMode.HORIZONTAL.usesContinuousScroll("continuous", true))
        assertFalse(ReaderWritingMode.HORIZONTAL.usesContinuousScroll("slide", false))
    }

    private fun assertActions(
        mode: ReaderEdgeTapMode,
        left: ReaderEdgeTapAction,
        right: ReaderEdgeTapAction
    ) {
        assertEquals(left, mode.leftAction)
        assertEquals(right, mode.rightAction)
    }
}

class ReaderThemeSuiteTest {
    @Test
    fun `defaults include four built-ins with serif eye care`() {
        val defaults = ReaderThemeSuites.defaults()

        assertEquals(ReaderThemeSuites.BUILT_IN_IDS, defaults.map { it.id })
        assertTrue(defaults.all(ReaderThemeSuite::isBuiltIn))
        assertEquals("serif", defaults.first { it.id == ReaderThemeSuites.SEPIA_ID }.settings.fontType)
    }

    @Test
    fun `normalization preserves custom position and repairs built-ins`() {
        val custom = ReaderThemeSuites.newCustom("custom-id", "  Focus  ")
        val normalized = ReaderThemeSuites.normalized(
            listOf(custom, ReaderThemeSuites.defaults().first())
        )

        assertEquals("custom-id", normalized.first().id)
        assertEquals("Focus", normalized.first().customName)
        assertEquals(
            ReaderThemeSuites.BUILT_IN_IDS.toSet(),
            normalized.filter(ReaderThemeSuite::isBuiltIn).map { it.id }.toSet()
        )
    }

    @Test
    fun `normalization preserves reordered built-in suites`() {
        val reordered = ReaderThemeSuites.defaults().reversed()

        assertEquals(reordered, ReaderThemeSuites.normalized(reordered))
    }

    @Test
    fun `normalization drops invalid and duplicate custom suites`() {
        val valid = ReaderThemeSuites.newCustom("same-id", "Valid")
        val duplicate = ReaderThemeSuites.newCustom("same-id", "Duplicate")
        val blank = ReaderThemeSuite("blank-name", "  ", ReaderThemeSettings())

        val normalized = ReaderThemeSuites.normalized(listOf(valid, duplicate, blank))

        assertEquals(1, normalized.count { !it.isBuiltIn })
        assertEquals("Valid", normalized.first { !it.isBuiltIn }.customName)
        assertFalse(normalized.any { it.id == "blank-name" })
    }

    @Test
    fun `legacy migration keeps current settings in matching suite`() {
        val legacy = ReaderThemeSettings(
            backgroundSelection = ReaderThemeSuites.NIGHT_ID,
            fontSize = 21f,
            letterSpacing = 3.5f
        )

        val migrated = ReaderThemeSuites.fromLegacy(legacy)

        assertEquals(ReaderThemeSuites.NIGHT_ID, migrated.activeSuiteId)
        assertEquals(legacy, migrated.suites.first { it.id == ReaderThemeSuites.NIGHT_ID }.settings)
        assertEquals(16f, migrated.suites.first { it.id == ReaderThemeSuites.DAY_ID }.settings.fontSize)
    }

    @Test
    fun `legacy custom background uses day suite without losing selection`() {
        val migrated = ReaderThemeSuites.fromLegacy(
            ReaderThemeSettings(backgroundSelection = "custom:paper")
        )

        assertEquals(ReaderThemeSuites.DAY_ID, migrated.activeSuiteId)
        assertEquals(
            "custom:paper",
            migrated.suites.first { it.id == ReaderThemeSuites.DAY_ID }.settings.backgroundSelection
        )
    }

    @Test
    fun `name length counts Unicode code points`() {
        assertEquals("主题", normalizeReaderThemeSuiteName("  主题  "))
        assertEquals(2, readerThemeSuiteNameCodePointCount("主题"))
        assertEquals(1, readerThemeSuiteNameCodePointCount("\uD83D\uDCD6"))
    }
}
