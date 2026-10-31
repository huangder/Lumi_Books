package com.huangder.lumibooks.ui.reader

import android.app.Application
import com.huangder.lumibooks.domain.model.Note
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class PdfTextPageTest {
    private fun quad(left: Float, top: Float, right: Float, bottom: Float) = PdfTextQuad(listOf(
        PdfTextPoint(left, top), PdfTextPoint(right, top), PdfTextPoint(right, bottom), PdfTextPoint(left, bottom)))
    private fun word(text: String, left: Float, top: Float, right: Float = left + 0.1f) =
        PdfPositionedText(text, quad(left, top, right, top + 0.02f), 0)
    private fun page() = buildPdfTextPage(2, "same-file", listOf(
        word("中", 0.1f, 0.1f, 0.12f), word("文", 0.12f, 0.1f, 0.14f),
        word("fi", 0.1f, 0.14f, 0.13f), word("😀", 0.13f, 0.14f, 0.15f)))

    @Test fun chineseAndMulticharacterGlyphsKeepUtf16OffsetsAcrossLines() {
        assertEquals("fi①Ａ，", normalizePdfGlyph("ﬁ①Ａ，"))
        val model = page()
        assertEquals("中文\nfi😀", model.text)
        assertEquals(0 until 2, model.lineRange(model.glyphs.first()))
        assertEquals(3 until 5, model.snap(4, 5))
        assertEquals(5 until 7, model.snap(6, 7))
        assertEquals("文\nfi😀", model.locator(1, 7)?.quote)
        assertEquals(2, model.quads(1, 7).size)
        assertEquals(0.12f, model.quads(1, 7).first().left, 0.0001f)
        assertNull(model.hit(0.9f, 0.9f))
        assertEquals(model.glyphs.last(), model.hit(0.9f, 0.9f, true))
    }

    @Test fun columnsAreReadDownBeforeCrossingTheGutter() {
        val model = buildPdfTextPage(0, "file", listOf(
            word("A", 0.1f, 0.1f), word("C", 0.6f, 0.1f),
            word("B", 0.1f, 0.13f), word("D", 0.6f, 0.13f)))
        assertEquals("A\nB\nC\nD", model.text)
        assertEquals(4, model.quads(0, model.text.length).size)
    }

    @Test fun locatorRoundTripRejectsInkMalformedAndChangedFiles() {
        val model = page()
        val locator = model.locator(1, 7)!!
        assertEquals(locator, PdfTextLocator.decode(locator.encode()))
        assertTrue(locator.matches(model))
        // Old saved locators used x-height boxes; rendering now rebuilds full-height boxes.
        assertTrue(locator.copy(quads = listOf(quad(0.12f, 0.11f, 0.14f, 0.115f))).matches(model))
        assertFalse(locator.matches(model.copy(fingerprint = "changed-file")))
        assertFalse(locator.matches(model.copy(text = "changed text")))
        assertNull(PdfTextLocator.decode("{\"version\":1,\"points\":[]}"))
        assertNull(PdfTextLocator.decode("invalid"))
        assertNull(PdfTextLocator.decode(JSONObject(locator.encode()).put("version", 99).toString()))
        assertNull(PdfTextLocator.decode(JSONObject(locator.encode()).put("end", 100).toString()))
    }

    @Test fun displayAndHitCoordinatesAgreeWithFitLetterboxingAndBothCrops() {
        for (fit in listOf(true, false)) for (ratio in listOf(0.6f, 1.5f)) {
            val transform = PdfTextTransform(ratio, 0.1f, 0.85f, 600f, 1000f, fit)
            for (point in listOf(PdfTextPoint(0.1f, 0f), PdfTextPoint(0.4f, 0.8f), PdfTextPoint(0.85f, 1f))) {
                val actual = transform.original(transform.display(point))
                assertEquals(point.x, actual.x, 0.00001f)
                assertEquals(point.y, actual.y, 0.00001f)
            }
            assertFalse(transform.visible(PdfTextPoint(0.05f, 0.5f)))
        }
        val transform = PdfTextTransform(0.5f, 0f, 1f, 600f, 1000f, true)
        assertEquals(PdfTextPoint(50f, 0f), transform.display(PdfTextPoint(0f, 0f)))
        assertEquals(PdfTextPoint(550f, 1000f), transform.display(PdfTextPoint(1f, 1f)))
    }

    @Test fun pageRotationsUseTheSameOriginAsPdfRenderer() {
        val expected = listOf(PdfTextPoint(0.25f, 0.75f), PdfTextPoint(0.25f, 0.25f),
            PdfTextPoint(0.75f, 0.25f), PdfTextPoint(0.75f, 0.75f))
        listOf(0, 90, 180, 270).forEachIndexed { i, rotation ->
            assertEquals(expected[i], pdfRotatedPoint(50f, 100f, 200f, 400f, rotation))
        }
    }

    @Test fun rangeEditsReanchorRemaindersAndPreserveNoteIntentAndTags() {
        val model = buildPdfTextPage(0, "file", (0..5).map { i ->
            word(('a' + i).toString(), 0.1f + i * 0.02f, 0.1f, 0.12f + i * 0.02f) })
        val encoded = model.locator(0, 6)!!.encode()
        val old = Note(id = 7, bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 6,
            startLocatorJson = encoded, endLocatorJson = encoded, selectedText = "abcdef",
            note = "thoughts", color = "#ffff00", createdAt = 1, isNote = true, tags = listOf("study"))
        val plan = pdfAnnotationEditPlan(model, listOf(old), "book", 2, 4, "highlight", "#00ff00", now = 2)
        assertEquals("ab", plan.updates.single().selectedText)
        assertEquals("thoughts", plan.updates.single().note)
        assertEquals(listOf("study"), plan.updates.single().tags)
        assertEquals(listOf("cd", "ef"), plan.inserts.map { it.selectedText })
        assertFalse(plan.inserts.last().isNoteEntry)
        (plan.updates + plan.inserts).forEach { note ->
            val locator = PdfTextLocator.decode(note.startLocatorJson)!!
            assertEquals(note.selectedText, locator.quote)
            assertTrue(locator.matches(model))
            assertEquals(note.endPosition - note.startPosition, note.selectedText.length)
        }
        val remove = pdfAnnotationEditPlan(model, listOf(old), "book", 0, 6, "highlight", "", remove = true)
        assertEquals(listOf(old), remove.deletes)
        assertTrue(remove.inserts.isEmpty())
        val legacy = old.copy(id = 8, startLocatorJson = null)
        assertTrue(pdfAnnotationEditPlan(model, listOf(legacy), "book", 0, 6, "highlight", "", remove = true).isEmpty)
    }
}
