package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BookOpenTransitionTest {
    @Test
    fun `missing and invalid values fall back to the hero transition`() {
        assertEquals(BookOpenTransition.HERO, BookOpenTransition.fromStoredValue(null))
        assertEquals(BookOpenTransition.HERO, BookOpenTransition.fromStoredValue(""))
        assertEquals("hero", BookOpenTransition.normalize(null))
        assertEquals("hero", BookOpenTransition.normalize("unknown"))
    }

    @Test
    fun `valid values remain unchanged`() {
        assertEquals(BookOpenTransition.HERO, BookOpenTransition.fromStoredValue("hero"))
        assertEquals(BookOpenTransition.LOADING_PAGE, BookOpenTransition.fromStoredValue("loading_page"))
        assertEquals("hero", BookOpenTransition.normalize("hero"))
        assertEquals("loading_page", BookOpenTransition.normalize("loading_page"))
    }
}
