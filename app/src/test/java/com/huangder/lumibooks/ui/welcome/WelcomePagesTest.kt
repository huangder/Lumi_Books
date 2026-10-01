package com.huangder.lumibooks.ui.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WelcomePagesTest {
    private val features = listOf(
        WelcomePage.EXCERPT_PREVIEW,
        WelcomePage.G2_PREVIEW,
        WelcomePage.DICTIONARY_PREVIEW
    )

    @Test
    fun `new installation shows features after optional glass setup`() {
        assertEquals(
            listOf(WelcomePage.LANGUAGE_SETUP, WelcomePage.INTRODUCTION, WelcomePage.LIQUID_GLASS_PREVIEW) +
                features + WelcomePage.SUPPORT,
            welcomePages(shouldShowLanguageSetup = true, isNewInstallation = true, isEInkMode = false)
        )
    }

    @Test
    fun `update shows all features without offering to change the existing theme`() {
        assertEquals(
            listOf(WelcomePage.INTRODUCTION) + features + WelcomePage.SUPPORT,
            welcomePages(shouldShowLanguageSetup = false, isNewInstallation = false, isEInkMode = false)
        )
    }

    @Test
    fun `ink users still see features on both fresh installs and updates`() {
        for (fresh in listOf(true, false)) {
            val pages = welcomePages(false, fresh, true)
            assertFalse(pages.contains(WelcomePage.LIQUID_GLASS_PREVIEW))
            assertEquals(listOf(WelcomePage.INTRODUCTION) + features + WelcomePage.SUPPORT, pages)
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
