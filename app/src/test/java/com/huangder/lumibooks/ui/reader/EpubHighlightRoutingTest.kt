package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubHighlightRoutingTest {
    @Test
    fun styledNotesUseWebViewWhilePlainNotesKeepNativeRendering() {
        val styled = note().copy(styleSnapshotJson = """{"fontType":"kaiti"}""")
        assertFalse(nativeEpubDecorationsSupported(listOf(styled), 0))
        assertTrue(nativeEpubDecorationsSupported(listOf(note()), 0))
        assertTrue(nativeEpubDecorationsSupported(listOf(styled.copy(isStale = true)), 0))
        assertTrue(nativeEpubDecorationsSupported(listOf(styled), 1))
        assertTrue(nativeEpubDecorationsSupported(emptyList(), 0))
    }

    @Test
    fun bundledAndCustomRuleFontsCarryTheirRegisteredFontUrls() {
        val fonts = mapOf("serif" to "LumiRuleSerif", "kaiti" to "LumiRuleKaiTi",
            "fangsong" to "LumiRuleFangSong", "custom:font-id" to "LumiRule_font-id")
        fonts.forEach { (type, family) ->
            val styled = note().copy(styleSnapshotJson = """{"fontType":"$type"}""")
            val url = "https://reader.local/$type.ttf"
            val style = highlightsJson(listOf(styled), mapOf(type to url))
                .getJSONObject(0).getJSONObject("ruleStyle")
            assertEquals(family, style.getString("fontFamily"))
            assertEquals(url, style.getString("fontUrl"))
        }
    }

    @Test
    fun routesOnlyNotesForTheRenderedChapterAndKeepsOrder() {
        val notes = listOf(
            note(id = 1, chapterIndex = 3, text = "first"),
            note(id = 2, chapterIndex = 1, text = "other"),
            note(id = 3, chapterIndex = 3, text = "second")
        )

        assertEquals(
            listOf(1L, 3L),
            epubNotesForChapter(notes, chapterIndex = 3).map { it.id }
        )
    }

    @Test
    fun missingChapterHasNoNotes() {
        assertEquals(emptyList<Note>(), epubNotesForChapter(listOf(note()), chapterIndex = 9))
    }

    @Test
    fun ruleMarksKeepTheirRenderingButCarryTheirOriginToTheTapHandler() {
        val generated = note().copy(
            origin = Note.ORIGIN_HIGHLIGHT_RULE,
            sourceRuleId = "rule",
            sourceMatchKey = "match",
            styleSnapshotJson = """{"textColor":-65536,"underlineMode":1}""",
            tags = listOf("tag")
        )
        val payload = highlightsJson(listOf(generated, generated.copy(styleSnapshotJson = null)))

        assertEquals(2, payload.length())
        assertTrue(payload.getJSONObject(0).getBoolean("generatedByRule"))
        assertEquals(generated.selectedText, payload.getJSONObject(0).getString("exact"))
        assertEquals(1, payload.getJSONObject(0).getJSONObject("ruleStyle").getInt("underlineMode"))
        assertEquals("#ff0000ff", payload.getJSONObject(0).getJSONObject("ruleStyle").getString("textColor"))
        assertTrue(payload.getJSONObject(1).getBoolean("generatedByRule"))
    }

    @Test
    fun manualUnderlinesAndDetachedRuleMarksRemainTappableWithStyleSnapshots() {
        val manual = note().copy(
            type = "underline",
            styleSnapshotJson = """{"textColor":-65536,"underlineMode":2}"""
        )
        val detached = manual.copy(
            origin = Note.ORIGIN_HIGHLIGHT_RULE, sourceRuleId = "rule", sourceMatchKey = "match"
        ).detachFromHighlightRule()
        val payload = highlightsJson(listOf(note(), manual, detached))

        for (index in 0 until payload.length()) {
            assertFalse(payload.getJSONObject(index).getBoolean("generatedByRule"))
        }
        assertEquals(2, payload.getJSONObject(1).getJSONObject("ruleStyle").getInt("underlineMode"))
        assertEquals(2, payload.getJSONObject(2).getJSONObject("ruleStyle").getInt("underlineMode"))
        assertFalse(payload.getJSONObject(1).getJSONObject("ruleStyle").has("textColor"))
        assertFalse(payload.getJSONObject(2).getJSONObject("ruleStyle").has("textColor"))
    }

    private fun note(
        id: Long = 1,
        chapterIndex: Int = 0,
        text: String = "text"
    ) = Note(
        id = id,
        bookId = "book",
        chapterIndex = chapterIndex,
        startPosition = 0,
        endPosition = text.length,
        selectedText = text,
        note = "",
        color = "#FFEB3B",
        createdAt = 1
    )
}
