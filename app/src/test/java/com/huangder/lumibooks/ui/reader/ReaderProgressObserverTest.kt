package com.huangder.lumibooks.ui.reader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressObserverTest {
    @Test
    fun `progress save does not start another long lived palette collector`() {
        val source = readerViewModelSource()
        val saveProgress = source.functionBody("private fun saveProgress()")

        assertFalse(saveProgress.contains("customHighlightPalettes"))
        assertFalse(saveProgress.contains("activeHighlightPaletteId"))
        assertFalse(saveProgress.contains("collectLatest"))
    }

    @Test
    fun `palette collector remains owned by settings observation`() {
        val source = readerViewModelSource()
        val settingsObserver = source.substringAfter("private fun observeReaderSettings()")
            .substringBefore("    fun saveFontSize")

        assertTrue(settingsObserver.contains("customHighlightPalettes"))
        assertTrue(settingsObserver.contains("activeHighlightPaletteId"))
        assertEquals(1, source.countOccurrences("dataStoreManager.customHighlightPalettes"))
        assertEquals(1, source.countOccurrences("dataStoreManager.activeHighlightPaletteId"))
    }

    private fun readerViewModelSource(): String {
        val candidates = listOf(
            File("src/main/java/com/huangder/lumibooks/ui/reader/ReaderViewModel.kt"),
            File("app/src/main/java/com/huangder/lumibooks/ui/reader/ReaderViewModel.kt")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("ReaderViewModel.kt source not found")
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length).count { it == value }

    private fun String.functionBody(signature: String): String {
        val signatureStart = indexOf(signature)
        require(signatureStart >= 0) { "$signature not found" }
        val bodyStart = indexOf('{', signatureStart)
        require(bodyStart >= 0) { "$signature body not found" }
        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return substring(bodyStart + 1, index)
                }
            }
        }
        error("$signature body is not balanced")
    }
}
