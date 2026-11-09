package com.huangder.lumibooks.service

import android.app.Application
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], application = Application::class, manifest = Config.NONE)
class TtsMediaButtonPendingIntentTest {
    @Test fun systemCanFillInWiredHeadsetKeyEvent() {
        val context = RuntimeEnvironment.getApplication()
        val pendingIntent = ttsMediaButtonPendingIntent(context)
        try {
            pendingIntent.send(context, 0,
                Intent().putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)))
            val delivered = shadowOf(context).broadcastIntents.last()
            val event = IntentCompat.getParcelableExtra(delivered, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            assertNotNull(event)
            assertEquals(KeyEvent.KEYCODE_HEADSETHOOK, event?.keyCode)
            assertEquals(TtsMediaButtonReceiver::class.java.name, delivered.component?.className)
        } finally { pendingIntent.cancel() }
    }
}
