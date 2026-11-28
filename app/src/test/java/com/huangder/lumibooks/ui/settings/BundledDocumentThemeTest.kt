package com.huangder.lumibooks.ui.settings

import java.io.File
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledDocumentThemeTest {
    @Test fun bundledDocumentsUseTheAppPaletteBeforeRendering() {
        for (file in listOf("privacy.html", "terms.html", "licenses.html")) {
            val html = File("src/main/assets/html/$file").readText()
            val original = Jsoup.parse(html)
            for (isDark in listOf(false, true)) {
                val color = if (isDark) 0xFF121217.toInt() else 0xFFFAF8F4.toInt()
                val themed = Jsoup.parse(themeBundledDocument(html, isDark, color))
                val styles = themed.select("head style").joinToString("\n") { it.data() }

                assertFalse(styles.contains("prefers-color-scheme"))
                assertTrue(styles.contains(if (isDark) "@media all" else "@media not all"))
                val theme = if (isDark) "color-scheme: dark; --bg: #121217" else "color-scheme: light; --bg: #faf8f4"
                assertTrue(styles.contains(theme))
                assertEquals(original.body().html(), themed.body().html())
            }
        }
    }
}
