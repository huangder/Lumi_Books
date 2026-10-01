package com.huangder.lumibooks.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class WebdavWorkerDecisionTest {
    @Test
    fun missingLocalNetworkPermissionEndsWithoutRetry() {
        val result = SyncResult(
            message = "permission required",
            success = false,
            failureCategory = WebdavFailureCategory.LOCAL_NETWORK_PERMISSION
        )
        assertEquals(
            WebdavWorkerDecision.SUCCESS,
            webdavWorkerDecision(result, runAttemptCount = 0, maxRetries = 4)
        )
    }

    @Test
    fun transientFailureRetriesUntilLimit() {
        val result = SyncResult("network", success = false)
        assertEquals(
            WebdavWorkerDecision.RETRY,
            webdavWorkerDecision(result, runAttemptCount = 3, maxRetries = 4)
        )
        assertEquals(
            WebdavWorkerDecision.FAILURE,
            webdavWorkerDecision(result, runAttemptCount = 4, maxRetries = 4)
        )
    }

    @Test
    fun serviceUnavailableRetriesUntilLimit() {
        val result = SyncResult(
            "service unavailable",
            success = false,
            failureCategory = WebdavFailureCategory.SERVICE_UNAVAILABLE
        )
        assertEquals(
            WebdavWorkerDecision.RETRY,
            webdavWorkerDecision(result, runAttemptCount = 0, maxRetries = 4)
        )
    }

    @Test
    fun configurationFailureDoesNotRetry() {
        val result = SyncResult(
            "authentication failed",
            success = false,
            failureCategory = WebdavFailureCategory.AUTH
        )
        assertEquals(
            WebdavWorkerDecision.FAILURE,
            webdavWorkerDecision(result, runAttemptCount = 0, maxRetries = 4)
        )
    }
}
