package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReadViewProgrammaticTurnTest {
    private fun reader() = ReadView(RuntimeEnvironment.getApplication()).apply {
        slotManager.backgroundPreparationEnabled = false
        setPageTransition("curl")
    }

    private fun event(reader: ReadView, action: Int) {
        val event = MotionEvent.obtain(0L, 20L, action, 200f, 200f, 0)
        try { reader.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    @Test fun keyboardRequestReleasesGestureWhoseCancelWasConsumedByAnotherMode() {
        val reader = reader()
        event(reader, MotionEvent.ACTION_DOWN)
        ReflectionHelpers.setField(reader, "rvIsHandlingPageGesture", true)
        // Guide mode owns the CANCEL, bypassing the ordinary paging branch.
        reader.setLineGuide(true, 2)
        event(reader, MotionEvent.ACTION_CANCEL)
        assertTrue(ReflectionHelpers.getField(reader, "rvIsHandlingPageGesture"))
        reader.turnToNextPage()
        assertFalse(ReflectionHelpers.getField(reader, "rvIsHandlingPageGesture"))
        assertFalse(ReflectionHelpers.getField(reader, "rvPointerStreamActive"))
    }

    @Test fun keyboardRequestKeepsOwnershipWhileFingerIsStillDown() {
        val reader = reader()
        event(reader, MotionEvent.ACTION_DOWN)
        ReflectionHelpers.setField(reader, "rvIsHandlingPageGesture", true)
        reader.turnToNextPage()
        assertTrue(ReflectionHelpers.getField(reader, "rvIsHandlingPageGesture"))
    }

    @Test fun endedGestureCleanupDoesNotAbortAnAlreadyRunningTurn() {
        val reader = reader()
        val controller = ReflectionHelpers.getField<CurlPageAnim>(reader, "animationController")
        ReflectionHelpers.setField(reader, "rvIsHandlingPageGesture", true)
        ReflectionHelpers.setField(controller, "isRunning", true)
        ReflectionHelpers.callInstanceMethod<Unit>(reader, "releaseEndedCurlGestureForProgrammaticTurn",
            ReflectionHelpers.ClassParameter.from(CurlPageAnim::class.java, controller))
        assertTrue(controller.isRunning)
        assertFalse(ReflectionHelpers.getField(reader, "rvIsHandlingPageGesture"))
    }
}
