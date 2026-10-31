package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import android.view.WindowManager.LayoutParams.*
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderImeWindowLeaseTest {
    @Test fun independentNoteWindowKeepsReaderSizeUntilItsImeHasClosed() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val window = activity.get().window
        val original = SOFT_INPUT_ADJUST_RESIZE or SOFT_INPUT_STATE_ALWAYS_HIDDEN
        window.setSoftInputMode(original)
        val first = ReaderImeWindowLease.acquire(window)
        assertEquals(readerWindowImeMode(original), window.attributes.softInputMode)
        first.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(SOFT_INPUT_ADJUST_NOTHING, window.attributes.softInputMode and SOFT_INPUT_MASK_ADJUST)
        val second = ReaderImeWindowLease.acquire(window)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertEquals(SOFT_INPUT_ADJUST_NOTHING, window.attributes.softInputMode and SOFT_INPUT_MASK_ADJUST)
        second.close()
        second.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertEquals(original, window.attributes.softInputMode)
        activity.pause().stop().destroy()
    }
}
