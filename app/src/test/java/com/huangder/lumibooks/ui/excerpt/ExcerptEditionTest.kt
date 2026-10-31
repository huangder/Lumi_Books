package com.huangder.lumibooks.ui.excerpt

import com.huangder.lumibooks.data.local.StoredExcerptPreferences
import org.junit.Assert.*
import org.junit.Test

class ExcerptEditionTest {
    @Test fun baseStylesRemainAvailableInTheirOriginalOrder() {
        assertEquals(ExcerptStyle.entries.take(6), ExcerptEdition.styles.take(6))
    }

    @Test fun restoredStyleIsAlwaysAvailableInTheActiveEdition() {
        for (style in ExcerptStyle.entries) {
            val restored = StoredExcerptPreferences(style.name, ExcerptAspectRatio.SQUARE.name, "Reader")
                .toExcerptSelection()
            assertTrue(restored.style in ExcerptEdition.styles)
            if (ExcerptEdition.styles.size == 6) {
                assertEquals(style.takeIf { it in ExcerptEdition.styles } ?: ExcerptStyle.READER, restored.style)
                assertEquals(ExcerptAspectRatio.FREE, restored.aspectRatio)
                assertEquals("", restored.signature)
            } else {
                assertEquals(style, restored.style)
                assertEquals(ExcerptAspectRatio.SQUARE, restored.aspectRatio)
                assertEquals("Reader", restored.signature)
            }
        }
    }
}
