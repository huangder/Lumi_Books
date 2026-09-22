package com.huangder.lumibooks.ui.welcome

import com.huangder.lumibooks.util.WelcomeLaunchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeLaunchStateResolverTest {
    private val install = WelcomeInstallState(1_000L, 2_000L)
    private val completed = WelcomeLaunchSnapshot(2_000L, false, true)

    @Test
    fun `stale complete mirror must not flash welcome on direct cold start`() {
        val stale = WelcomeLaunchSnapshot(1_000L, true, false)
        var reads = 0
        val resolved = resolveWelcomeLaunchSnapshot(install, stale) {
            reads++
            completed
        }

        assertEquals(1, reads)
        assertEquals(completed, resolved)
        assertFalse(install.shouldShowWelcome(resolved.completedInstallTime))
        assertFalse(resolved.splashEnabled)
        assertTrue(resolved.hasCompletedLanguageSetup)
    }

    @Test
    fun `default mirror cannot authorize welcome before persisted state is read`() {
        val resolved = resolveWelcomeLaunchSnapshot(install, WelcomeLaunchSnapshot()) { completed }
        assertFalse(install.shouldShowWelcome(resolved.completedInstallTime))
    }

    @Test
    fun `missing mirror is hydrated before routing`() {
        val resolved = resolveWelcomeLaunchSnapshot(install, null) { completed }
        assertEquals(completed, resolved)
    }

    @Test
    fun `completed mirror keeps normal cold start on fast path`() {
        for (splashEnabled in listOf(false, true)) {
            val cached = completed.copy(splashEnabled = splashEnabled)
            val resolved = resolveWelcomeLaunchSnapshot(install, cached) {
                error("Completed mirror should not block on DataStore")
            }
            assertEquals(cached, resolved)
            assertFalse(install.shouldShowWelcome(resolved.completedInstallTime))
        }
    }

    @Test
    fun `fresh install still shows welcome after confirmation`() {
        val fresh = WelcomeInstallState(3_000L, 3_000L)
        val persisted = WelcomeLaunchSnapshot()
        val resolved = resolveWelcomeLaunchSnapshot(fresh, null) { persisted }
        assertTrue(fresh.shouldShowWelcome(resolved.completedInstallTime))
    }

    @Test
    fun `update still shows welcome when persisted marker is from previous install`() {
        val updated = WelcomeInstallState(1_000L, 3_000L)
        var readPersisted = false
        val resolved = resolveWelcomeLaunchSnapshot(updated, completed) {
            readPersisted = true
            completed
        }
        assertTrue(readPersisted)
        assertTrue(updated.shouldShowWelcome(resolved.completedInstallTime))
    }
}
