package com.huangder.lumibooks.domain.model

import com.huangder.lumibooks.ui.settings.annotationPreviewNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAnnotationGeometryCodecTest {
    private val tuned = ReaderThemeSettings(
        highlightWidthDp = 2.5f,
        highlightHeightDp = -1.5f,
        highlightOffsetYDp = 3f,
        underlineThicknessDp = 2f,
        underlineOffsetYDp = -4f
    )

    @Test fun geometrySurvivesPersistenceAndPortableExportInEveryMode() {
        val suite = ReaderThemeSuite(
            id = "geometry", customName = "Geometry", settings = tuned,
            bookLayoutSettings = tuned.copy(highlightOffsetYDp = -2f),
            lightSettings = tuned.copy(underlineThicknessDp = 3f),
            darkSettings = tuned.copy(underlineOffsetYDp = 6f)
        )
        assertEquals(suite, ReaderThemeSuiteCodec.decode(ReaderThemeSuiteCodec.encode(listOf(suite)))
            .first { it.id == suite.id })
        assertEquals(suite, LumiThemeBundleCodec.decode(LumiThemeBundleCodec.encode(listOf(suite))).single())
    }

    @Test fun legacyPayloadWithoutGeometryKeepsOriginalLook() {
        val legacy = ReaderThemeSuiteCodec.decode("""[{"id":"day","settings":{"background":"day"}}]""").single()
        val defaults = ReaderThemeSettings()
        assertEquals(0f, legacy.settings.highlightWidthDp)
        assertEquals(0f, legacy.settings.highlightHeightDp)
        assertEquals(0f, legacy.settings.highlightOffsetYDp)
        assertEquals(ReaderThemeSettings.DEFAULT_UNDERLINE_THICKNESS_DP, legacy.settings.underlineThicknessDp)
        assertEquals(0f, legacy.settings.underlineOffsetYDp)
        assertEquals(defaults, defaults.withDefaultAnnotationGeometry())
        assertEquals(defaults, tuned.withDefaultAnnotationGeometry())
    }

    @Test fun outOfRangeGeometryIsClampedOnSaveAndRejectedOnImport() {
        val extreme = ReaderThemeSettings(
            highlightWidthDp = 99f, highlightHeightDp = -99f, highlightOffsetYDp = Float.NaN,
            underlineThicknessDp = 0f, underlineOffsetYDp = 99f
        )
        val normalized = ReaderThemeSuites.normalized(listOf(
            ReaderThemeSuite(id = "extreme", customName = "Extreme", settings = extreme)
        )).first { it.id == "extreme" }.settings
        assertEquals(ReaderThemeSettings.HIGHLIGHT_WIDTH_RANGE.endInclusive, normalized.highlightWidthDp)
        assertEquals(ReaderThemeSettings.HIGHLIGHT_HEIGHT_RANGE.start, normalized.highlightHeightDp)
        assertEquals(0f, normalized.highlightOffsetYDp)
        assertEquals(ReaderThemeSettings.UNDERLINE_THICKNESS_RANGE.start, normalized.underlineThicknessDp)
        assertEquals(ReaderThemeSettings.UNDERLINE_OFFSET_Y_RANGE.endInclusive, normalized.underlineOffsetYDp)

        assertThrows(IllegalArgumentException::class.java) {
            LumiThemeBundleCodec.decode(
                """[{"id":"bad","settings":{"background":"day","underlineThicknessDp":9}}]"""
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LumiThemeBundleCodec.decode(
                """[{"id":"bad","settings":{"background":"day","highlightOffsetYDp":-9}}]"""
            )
        }
    }

    @Test fun previewSamplesLandOnFirstBodyParagraph() {
        val sample = "第一章 光落在书页上\n\n清晨的风越过窗台，纸张轻轻翻动。阅读让时间慢下来，也让遥远的声音在此刻变得清晰。"
        val notes = annotationPreviewNotes(sample)
        assertEquals(listOf("highlight", "underline"), notes.map { it.type })
        val bodyStart = sample.indexOf("清晨")
        notes.forEach { note ->
            assertTrue(note.startPosition >= bodyStart && note.endPosition > note.startPosition)
            assertEquals(sample.substring(note.startPosition, note.endPosition), note.selectedText)
        }
        assertEquals(emptyList<Any>(), annotationPreviewNotes("只有标题"))
    }
}
