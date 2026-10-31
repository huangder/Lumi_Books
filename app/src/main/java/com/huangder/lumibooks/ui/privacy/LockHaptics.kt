package com.huangder.lumibooks.ui.privacy

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Platform haptic constants for the lock flows. The newer gesture constants are picked by
 * API level; each event fires exactly one effect and honours the system touch-feedback setting.
 */
internal object LockHaptics {
    fun keyTap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Pull reached the threshold: releasing now opens the passcode panel. */
    fun arm(view: View) {
        view.performHapticFeedback(
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
                else -> HapticFeedbackConstants.LONG_PRESS
            }
        )
    }

    fun disarm(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        )
    }

    fun reject(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
        )
    }

    fun confirm(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.CONTEXT_CLICK
        )
    }
}
