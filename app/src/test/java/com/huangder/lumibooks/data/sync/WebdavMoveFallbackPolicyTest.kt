package com.huangder.lumibooks.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebdavMoveFallbackPolicyTest {
    @Test
    fun internalServerErrorFallsBackToDirectManifestPut() {
        assertTrue(isMoveFallbackStatus(500))
    }

    @Test
    fun knownUnsupportedStatusesStillFallBack() {
        assertTrue(isMoveFallbackStatus(403))
        assertTrue(isMoveFallbackStatus(405))
        assertTrue(isMoveFallbackStatus(501))
    }

    @Test
    fun unrelatedMoveFailuresDoNotFallBack() {
        assertFalse(isMoveFallbackStatus(409))
        assertFalse(isMoveFallbackStatus(502))
        assertFalse(isMoveFallbackStatus(null))
    }
}
