package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDeleteModeTest {
    @Test
    fun forceDeleteAttemptsCloudWithoutBlockingLocalDeletion() {
        val mode = BookDeleteMode.FORCE_LOCAL_AND_CLOUD

        assertTrue(mode.attemptsCloudDelete)
        assertTrue(mode.forcesLocalDelete)
        assertFalse(mode.cloudFailureBlocksLocalDelete)
    }

    @Test
    fun regularCloudDeleteStillRequiresRemoteSuccessFirst() {
        val mode = BookDeleteMode.LOCAL_AND_CLOUD

        assertTrue(mode.attemptsCloudDelete)
        assertFalse(mode.forcesLocalDelete)
        assertTrue(mode.cloudFailureBlocksLocalDelete)
    }
}
