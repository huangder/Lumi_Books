package com.huangder.lumibooks.service

import android.app.Application
import android.content.Intent
import com.huangder.lumibooks.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], application = Application::class, manifest = Config.NONE)
class TtsOpenBookPendingIntentTest {
    @Test fun mediaEntryPointOpensTheCurrentBookAndClearsTheTargetWhenPlaybackStops() {
        val context = RuntimeEnvironment.getApplication()
        val pendingIntent = ttsOpenBookPendingIntent(context, "first-book")
        try {
            ttsOpenBookPendingIntent(context, "second-book")
            pendingIntent.send()
            val opened = shadowOf(context).nextStartedActivity
            assertEquals(MainActivity::class.java.name, opened.component?.className)
            assertEquals(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP, opened.flags)
            assertEquals("second-book", opened.getStringExtra(MainActivity.EXTRA_OPEN_BOOK_ID))

            ttsOpenBookPendingIntent(context, null)
            pendingIntent.send()
            assertNull(shadowOf(context).nextStartedActivity.getStringExtra(MainActivity.EXTRA_OPEN_BOOK_ID))
        } finally {
            pendingIntent.cancel()
        }
    }
}
