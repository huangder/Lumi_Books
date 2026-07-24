package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagNameValidatorTest {
    @Test
    fun acceptsTrimmedNameAtMaximumLength() {
        val name = " ${"a".repeat(TagNameValidator.MAX_LENGTH)} "

        assertTrue(TagNameValidator.isValid(name))
        assertEquals("a".repeat(TagNameValidator.MAX_LENGTH), TagNameValidator.clean(name))
    }

    @Test
    fun rejectsBlankAndOverlongNames() {
        assertFalse(TagNameValidator.isValid("   "))
        assertFalse(TagNameValidator.isValid("a".repeat(TagNameValidator.MAX_LENGTH + 1)))
    }

    @Test
    fun normalizesWhitespaceAndCaseForDuplicateDetection() {
        assertEquals("to read", TagNameValidator.normalized("  To Read  "))
    }
}
