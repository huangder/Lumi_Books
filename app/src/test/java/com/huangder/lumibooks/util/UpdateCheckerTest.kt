package com.huangder.lumibooks.util

import org.junit.Assert.assertEquals
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

    @Test
    fun `matches blocked catalog urls after normalizing host default port and trailing slash`() {
        assertTrue(
            UpdateChecker.isOpdsUrlBlocked(
                "HTTPS://Catalog.Example:443/opds/",
                listOf("https://catalog.example/opds")
            )
        )
    }

    @Test
    fun `does not block a different catalog path`() {
        assertFalse(
            UpdateChecker.isOpdsUrlBlocked(
                "https://catalog.example/other",
                listOf("https://catalog.example/opds")
            )
        )
    }

    @Test
    fun `embedded credentials do not bypass a blocked catalog url`() {
        assertTrue(
            UpdateChecker.isOpdsUrlBlocked(
                "https://reader:secret@catalog.example/opds",
                listOf("https://catalog.example/opds")
            )
        )
    }

    @Test
    fun `policy revisions prompt existing users without an app update`() {
        val result = evaluatePolicies(acceptedTerms = 2, acceptedPrivacy = 3)

        assertTrue(result.hasTermsUpdate)
        assertTrue(result.hasPrivacyUpdate)
        assertEquals(3, result.termsVersion)
        assertEquals(4, result.privacyVersion)
        assertFalse(result.hasAppUpdate)
        assertFalse(result.isForceUpdate)
    }

    @Test
    fun `accepted policy revisions do not prompt again`() {
        val result = evaluatePolicies(acceptedTerms = 3, acceptedPrivacy = 4)

        assertFalse(result.hasTermsUpdate)
        assertFalse(result.hasPrivacyUpdate)
    }

    @Test
    fun `policy acceptance is tracked independently for each document`() {
        val privacyOnly = evaluatePolicies(acceptedTerms = 3, acceptedPrivacy = 3)
        assertFalse(privacyOnly.hasTermsUpdate)
        assertTrue(privacyOnly.hasPrivacyUpdate)

        val termsOnly = evaluatePolicies(acceptedTerms = 2, acceptedPrivacy = 4)
        assertTrue(termsOnly.hasTermsUpdate)
        assertFalse(termsOnly.hasPrivacyUpdate)
    }

    private fun evaluatePolicies(acceptedTerms: Int, acceptedPrivacy: Int): UpdateChecker.CheckResult {
        return UpdateChecker.evaluate(
            config = UpdateChecker.UpdateConfig(
                latestVersion = "1.0.06",
                latestVersionCode = 0,
                releaseUrl = "",
                updateTitle = "",
                updateMessage = "",
                changelog = "",
                forceUpdateBelowVersionCode = 0L,
                termsVersion = 3,
                privacyVersion = 4,
                notice = null
            ),
            currentVersion = "1.0.06",
            acceptedTerms = acceptedTerms,
            acceptedPrivacy = acceptedPrivacy
        )
    }

    private fun evaluateAppUpdate(remoteVersion: String, currentVersion: String): Boolean {
        return UpdateChecker.evaluate(
            config = UpdateChecker.UpdateConfig(
                latestVersion = remoteVersion,
                latestVersionCode = 0,
                releaseUrl = "",
                updateTitle = "",
                updateMessage = "",
                changelog = "",
                forceUpdateBelowVersionCode = 0L,
                termsVersion = 0,
                privacyVersion = 0,
                notice = null
            ),
            currentVersion = currentVersion,
            acceptedTerms = 0,
            acceptedPrivacy = 0
        ).hasAppUpdate
    }
}
