package com.huangder.lumibooks.ui.bookshelf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailsLayoutTest {
    @Test
    fun shortFieldStaysHorizontal() {
        assertFalse(shouldStackDetailRow(80f, 120f, 320f, 16f))
    }

    @Test
    fun longTitleOrUnbrokenFileNameStacks() {
        assertTrue(shouldStackDetailRow(80f, 360f, 320f, 16f))
        assertTrue(shouldStackDetailRow(160f, 160f, 320f, 16f))
    }
}
