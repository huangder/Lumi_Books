package com.huangder.lumibooks.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `does not report a remote older version as an update`() {
        assertFalse(evaluateAppUpdate(remoteVersion = "1.0.03", currentVersion = "1.0.06"))
    }

    @Test
    fun `does not report an equal version as an update`() {
        assertFalse(evaluateAppUpdate(remoteVersion = "1.0.06", currentVersion = "1.0.06"))
    }

    @Test
    fun `reports a remote newer version as an update`() {
        assertTrue(evaluateAppUpdate(remoteVersion = "1.0.07", currentVersion = "1.0.06"))
    }

    @Test
    fun `accepts a v-prefixed remote version`() {
        assertTrue(evaluateAppUpdate(remoteVersion = "v1.1.0", currentVersion = "1.0.99"))
    }

    private fun evaluateAppUpdate(remoteVersion: String, currentVersion: String): Boolean {
        return UpdateChecker.evaluate(
            config = UpdateChecker.UpdateConfig(
                latestVersion = remoteVersion,
                latestVersionCode = 0,
                releaseUrl = "",
                termsVersion = 0,
                privacyVersion = 0
            ),
            currentVersion = currentVersion,
            acceptedTerms = 0,
            acceptedPrivacy = 0
        ).hasAppUpdate
    }
}
