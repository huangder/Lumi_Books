package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TtsExitDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun continueLeavesWithBackgroundListening() = verifyExit(true)
    @Test fun glassStopLeavesWithoutBackgroundListening() = verifyExit(false, glass = true)
    @Test fun eInkContinueLeavesWithBackgroundListening() = verifyExit(true, glass = true, eInk = true)
    @Test fun rememberContinueChoiceIsDeliveredWithTheDecision() = verifyExit(true, remember = true)
    @Test fun rememberStopChoiceIsDeliveredWithTheDecision() = verifyExit(false, remember = true)
    @Test fun subtitlesOffAllowsBackgroundListeningWithoutOverlayPermission() =
        verifyExit(true, floatingSubtitles = false, overlayPermission = false)
    @Test fun subtitlesOffCanRememberBackgroundListeningWithoutOverlayPermission() =
        verifyExit(true, remember = true, floatingSubtitles = false, overlayPermission = false)

    private fun verifyExit(continueListening: Boolean, glass: Boolean = false, eInk: Boolean = false,
        remember: Boolean = false, floatingSubtitles: Boolean = true, overlayPermission: Boolean = true) {
        ShadowSettings.setCanDrawOverlays(overlayPermission)
        val context = RuntimeEnvironment.getApplication()
        val visible = mutableStateOf(true)
        var result: Boolean? = null
        var remembered: Boolean? = null
        compose.setContent {
            CompositionLocalProvider(LocalAppTheme provides if (glass) "liquid_glass" else "lumi",
                LocalEInkMode provides eInk, LocalMotionEnabled provides false) {
                TtsExitDialog(visible.value, Color.White, Color.Black, eInk, null,
                    onDismiss = { visible.value = false },
                    onExit = { keep, save -> result = keep; remembered = save; visible.value = false },
                    floatingSubtitlesEnabled = floatingSubtitles)
            }
        }
        val title = context.getString(R.string.tts_exit_title)
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(context.getString(if (floatingSubtitles) R.string.tts_exit_message
            else R.string.tts_exit_message_without_subtitles)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, result) }
        if (remember) compose.onNodeWithText(context.getString(R.string.tts_start_remember_choice)).performClick()
        compose.onNodeWithText(context.getString(if (continueListening)
            R.string.tts_exit_continue else R.string.tts_exit_stop)).performClick()
        compose.runOnIdle { assertEquals(continueListening, result); assertEquals(remember, remembered) }
        compose.onNodeWithText(title).assertDoesNotExist()
    }
}
