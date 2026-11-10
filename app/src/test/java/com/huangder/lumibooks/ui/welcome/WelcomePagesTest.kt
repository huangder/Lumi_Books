package com.huangder.lumibooks.ui.welcome

import org.junit.Assert.assertEquals
import org.junit.Test

class WelcomePagesTest {
    @Test
    fun `new installation goes from introduction through the updates to the final page`() {
        assertEquals(
            listOf(WelcomePage.LANGUAGE_SETUP, WelcomePage.INTRODUCTION, WelcomePage.FEATURES, WelcomePage.SUPPORT),
            welcomePages(shouldShowLanguageSetup = true, isNewInstallation = true, isEInkMode = false)
        )
    }

    @Test
    fun `updates and ink users see the same flow without a theme step`() {
        for (fresh in listOf(true, false)) for (ink in listOf(true, false)) {
            assertEquals(
                listOf(WelcomePage.INTRODUCTION, WelcomePage.FEATURES, WelcomePage.SUPPORT),
                welcomePages(shouldShowLanguageSetup = false, isNewInstallation = fresh, isEInkMode = ink)
            )
        }
    }

    @Test
    fun `page order matches enum order so transitions slide the right way`() {
        for (language in listOf(true, false)) for (fresh in listOf(true, false)) for (ink in listOf(true, false)) {
            val pages = welcomePages(language, fresh, ink)
            assertEquals(pages.sortedBy { it.ordinal }, pages)
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
