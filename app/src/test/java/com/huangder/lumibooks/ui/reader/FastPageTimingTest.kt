package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.reader.engine.ReadView
import com.huangder.lumibooks.ui.reader.engine.curlExpeditedDurationMs
import com.huangder.lumibooks.ui.reader.engine.curlSettleDurationMs
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FastPageTimingTest {
    @After fun restoreMotion() { VisualMotion.setPreference(MotionPreference.STANDARD) }

    private fun field(instance: Any, name: String): Any = instance.javaClass
        .getDeclaredField(name).apply { isAccessible = true }.get(instance)!!

    @Test fun `native and epub controllers retain fast durations below storage steps and minimums`() {
        VisualMotion.setPreference(MotionPreference.FAST)
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val reader = ReadView(activity.get())
        val epub = EpubPageTurnHost(activity.get())
        try {
            for ((mode, base) in listOf("slide" to 260, "slide" to 100, "scroll" to 100,
                "fade" to 400, "curl" to 800, "curl" to 300)) {
                val runtime = VisualMotion.durationMillis(base)
                reader.setPageTransitionTiming(mode, runtime)
                reader.setPageTransition(mode)
                assertEquals(runtime, field(field(reader, "animationController"), "baseDurationMs"))
                reader.setPageTransitionTiming(mode, runtime + 1)
                assertEquals(runtime + 1, field(field(reader, "animationController"), "baseDurationMs"))
                if (mode != "fade") {
                    epub.setTransition(mode, runtime)
                    assertEquals(runtime, field(epub, "transitionDurationMs"))
                    assertEquals(runtime, field(field(epub, "controller"), "baseDurationMs"))
                    epub.setTransition(mode, runtime + 1)
                    assertEquals(runtime + 1, field(field(epub, "controller"), "baseDurationMs"))
                }
                assertEquals(base, ReaderPageAnimationSettings.sanitizeDuration(mode, base))
            }
            assertEquals(80, curlSettleDurationMs(533, 1f, 400f))
            assertEquals(160, curlExpeditedDurationMs(533))
        } finally {
            epub.release()
            activity.pause().stop().destroy()
        }
    }
}
