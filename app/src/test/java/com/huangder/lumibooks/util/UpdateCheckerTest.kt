package com.huangder.lumibooks.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `reads optional photo alongside notice text`() {
        val notice = UpdateChecker.parseNotice(noticeJson().put("image_url", " https://example.com/photo.jpg "))!!

        assertEquals("通知正文", notice.message)
        assertEquals("https://example.com/photo.jpg", notice.imageUrl)
    }

    @Test
    fun `missing or invalid photo preserves text-only notice`() {
        val textOnly = UpdateChecker.parseNotice(noticeJson())!!
        assertNull(textOnly.imageUrl)
        assertEquals("通知正文", textOnly.message)

        for (invalid in listOf(JSONObject.NULL, 123, "", "https:photo.jpg", "file:///photo.jpg", "http://example.com/photo.jpg")) {
            assertEquals(textOnly, UpdateChecker.parseNotice(noticeJson().put("image_url", invalid)))
        }
    }

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

    @Test
    fun `pending app update still returns an eligible notice for archiving`() {
        val notice = UpdateChecker.parseNotice(noticeJson()
            .put("min_version_code", 17)
            .put("max_version_code", 17))!!
        val config = UpdateChecker.UpdateConfig(
            latestVersion = "2.3.12",
            latestVersionCode = 18,
            releaseUrl = "",
            updateTitle = "",
            updateMessage = "",
            changelog = "",
            forceUpdateBelowVersionCode = 18,
            termsVersion = 0,
            privacyVersion = 0,
            notice = notice
        )
        val result = UpdateChecker.evaluate(config, "2.3.11", 17, 0, 0)
        assertTrue(result.isForceUpdate)
        assertEquals(notice, result.notice)
        assertNull(UpdateChecker.evaluate(config, "2.3.10", 16, 0, 0).notice)
        assertNull(UpdateChecker.evaluate(config.copy(notice = notice.copy(enabled = false)), "2.3.11", 17, 0, 0).notice)
    }

    private fun noticeJson() = JSONObject()
        .put("id", "notice-test")
        .put("enabled", true)
        .put("title", "通知")
        .put("message", "通知正文")

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
