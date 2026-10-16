package com.huangder.lumibooks.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortablePreferenceMergeTest {
    @Test
    fun appliesRemoteValueWhenLocalValueHasNotChangedSinceSnapshot() {
        assertTrue(
            shouldApplyIncomingPortablePreference(
                hasPreviousMetadata = true,
                hasCurrentValue = true,
                currentFingerprint = "theme-before-edit",
                previousFingerprint = "theme-before-edit",
                previousWasDeleted = false
            )
        )
    }

    @Test
    fun preservesLocalEditMadeWhileSyncWasRunning() {
        assertFalse(
            shouldApplyIncomingPortablePreference(
                hasPreviousMetadata = true,
                hasCurrentValue = true,
                currentFingerprint = "theme-after-edit",
                previousFingerprint = "theme-before-edit",
                previousWasDeleted = false
            )
        )
    }

    @Test
    fun preservesLocalDeletionMadeWhileSyncWasRunning() {
        assertFalse(
            shouldApplyIncomingPortablePreference(
                hasPreviousMetadata = true,
                hasCurrentValue = false,
                currentFingerprint = null,
                previousFingerprint = "value-before-delete",
                previousWasDeleted = false
            )
        )
    }

    @Test
    fun appliesRemoteValueWhenNoLocalBaselineExists() {
        assertTrue(
            shouldApplyIncomingPortablePreference(
                hasPreviousMetadata = false,
                hasCurrentValue = true,
                currentFingerprint = "legacy-value",
                previousFingerprint = null,
                previousWasDeleted = false
            )
        )
    }
}
