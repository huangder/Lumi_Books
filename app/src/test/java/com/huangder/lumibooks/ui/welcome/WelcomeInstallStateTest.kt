package com.huangder.lumibooks.ui.welcome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeInstallStateTest {

    @Test
    fun `fresh install shows normal welcome once`() {
        val state = WelcomeInstallState(
            firstInstallTime = 1_000L,
            lastUpdateTime = 1_000L
        )

        assertFalse(state.isUpdate)
        assertTrue(state.shouldShowWelcome(completedInstallTime = 0L))
        assertFalse(state.shouldShowWelcome(completedInstallTime = 1_000L))
    }

    @Test
    fun `cover install shows update welcome even when app version is unchanged`() {
        val state = WelcomeInstallState(
            firstInstallTime = 1_000L,
            lastUpdateTime = 2_000L
        )

        assertTrue(state.isUpdate)
        assertTrue(state.shouldShowWelcome(completedInstallTime = 1_500L))
        assertFalse(state.shouldShowWelcome(completedInstallTime = 2_000L))
    }

    @Test
    fun `new cover install invalidates the previously completed install`() {
        val previousInstall = WelcomeInstallState(1_000L, 2_000L)
        val currentInstall = WelcomeInstallState(1_000L, 3_000L)

        assertFalse(previousInstall.shouldShowWelcome(completedInstallTime = 2_000L))
        assertTrue(currentInstall.shouldShowWelcome(completedInstallTime = 2_000L))
    }

    @Test
    fun `reinstall shows normal welcome even when an old marker was restored`() {
        val state = WelcomeInstallState(
            firstInstallTime = 4_000L,
            lastUpdateTime = 4_000L
        )

        assertFalse(state.isUpdate)
        assertTrue(state.shouldShowWelcome(completedInstallTime = 2_000L))
    }

    @Test
    fun `missing install marker never suppresses welcome`() {
        val state = WelcomeInstallState(
            firstInstallTime = 0L,
            lastUpdateTime = 0L
        )

        assertTrue(state.shouldShowWelcome(completedInstallTime = 0L))
    }
}
