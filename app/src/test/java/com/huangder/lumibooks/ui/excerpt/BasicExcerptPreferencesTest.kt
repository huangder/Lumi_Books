package com.huangder.lumibooks.ui.excerpt

import com.huangder.lumibooks.data.local.StoredExcerptPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicExcerptPreferencesTest {
    @Test
    fun unlockedWithoutSavedChoiceStartsAtFreeDefaults() {
        assertEquals(ExcerptSelection(), initialExcerptSelection(isPremium = true, stored = null))
    }

    @Test
    fun lockedWithoutSavedChoiceStartsAtFreeDefaults() {
        assertEquals(ExcerptSelection(), initialExcerptSelection(isPremium = false, stored = null))
    }
}
