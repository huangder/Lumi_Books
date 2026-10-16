package com.huangder.lumibooks.ui.animation

import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualMotionTest {
    @After
    fun restoreStandardMotion() {
        VisualMotion.setPreference(MotionPreference.STANDARD)
    }

    @Test
    fun `fast preference parses and keeps motion enabled`() {
        assertEquals(MotionPreference.FAST, MotionPreference.fromStoredValue("fast"))
        assertTrue(MotionPreference.FAST.motionEnabled)
    }

    @Test
    fun `fast preference scales visual hero stages by two thirds`() {
        VisualMotion.setPreference(MotionPreference.FAST)

        assertEquals(433, VisualMotion.durationMillis(BookReaderMotion.WINDOW_DURATION_MS))
        assertEquals(130, VisualMotion.durationMillis(BookReaderMotion.COVER_STAGE_MS))
        assertEquals(120, VisualMotion.durationMillis(BookReaderMotion.CONTENT_REVEAL_MS))
    }

    @Test
    fun `fast preference only changes runtime page turn duration`() {
        val settings = ReaderPageAnimationSettings().withDuration("slide", 260)
        VisualMotion.setPreference(MotionPreference.FAST)

        assertEquals(260, settings.durationFor("slide"))
        assertEquals(173, VisualMotion.durationMillis(settings.durationFor("slide")))
        assertEquals(533, VisualMotion.durationMillis(settings.durationFor("curl")))
    }

    @Test
    fun `shared springs are rebuilt at the selected visual speed`() {
        VisualMotion.setPreference(MotionPreference.STANDARD)
        assertEquals(380f, LumiMotion.GestureSpring.stiffness, 0.001f)

        VisualMotion.setPreference(MotionPreference.FAST)
        assertEquals(855f, LumiMotion.GestureSpring.stiffness, 0.001f)
        assertEquals(120L, VisualMotion.delayMillis(180L))
    }
}
