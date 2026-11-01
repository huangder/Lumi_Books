package com.huangder.lumibooks.ui.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WelcomePagesTest {
    @Test
    fun `new installation goes from optional glass setup to support`() {
        assertEquals(
            listOf(WelcomePage.LANGUAGE_SETUP, WelcomePage.INTRODUCTION, WelcomePage.LIQUID_GLASS_PREVIEW, WelcomePage.SUPPORT),
            welcomePages(shouldShowLanguageSetup = true, isNewInstallation = true, isEInkMode = false)
        )
    }

    @Test
    fun `update goes directly from introduction to support`() {
        assertEquals(
            listOf(WelcomePage.INTRODUCTION, WelcomePage.SUPPORT),
            welcomePages(shouldShowLanguageSetup = false, isNewInstallation = false, isEInkMode = false)
        )
    }

    @Test
    fun `ink users go directly from introduction to support on fresh installs and updates`() {
        for (fresh in listOf(true, false)) {
            val pages = welcomePages(false, fresh, true)
            assertFalse(pages.contains(WelcomePage.LIQUID_GLASS_PREVIEW))
            assertEquals(listOf(WelcomePage.INTRODUCTION, WelcomePage.SUPPORT), pages)
        }
    }

    @Test
    fun `finishing language setup does not change the remaining page order`() {
        for (fresh in listOf(true, false)) {
            for (ink in listOf(true, false)) {
                assertEquals(welcomePages(false, fresh, ink), welcomePages(true, fresh, ink).drop(1))
            }
        }
    }
}
