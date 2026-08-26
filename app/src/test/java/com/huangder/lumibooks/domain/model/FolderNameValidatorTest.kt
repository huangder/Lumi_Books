package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderNameValidatorTest {
    @Test
    fun acceptsAndCleansNameAtMaximumLength() {
        val name = " ${"a".repeat(FolderNameValidator.MAX_LENGTH)} "

        assertTrue(FolderNameValidator.isValid(name))
        assertEquals("a".repeat(FolderNameValidator.MAX_LENGTH), FolderNameValidator.clean(name))
    }

    @Test
    fun rejectsBlankAndOverlongNames() {
        assertFalse(FolderNameValidator.isValid("   "))
        assertFalse(
            FolderNameValidator.isValid("a".repeat(FolderNameValidator.MAX_LENGTH + 1))
        )
    }

    @Test
    fun normalizesWhitespaceAndCaseForSiblingDuplicateDetection() {
        assertEquals("science fiction", FolderNameValidator.normalized("  Science Fiction  "))
    }
}
