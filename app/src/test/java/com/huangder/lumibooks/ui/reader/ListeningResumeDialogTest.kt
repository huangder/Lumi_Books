package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.TtsListeningPosition
import com.huangder.lumibooks.tts.TtsStartChoice
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListeningResumeDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun startFromCurrentPageDoesNotRememberWithoutCheckingTheBox() =
        verifyStart(TtsStartChoice.CURRENT_PAGE, remember = false)

    @Test fun darkStartDialogCanRememberResume() =
        verifyStart(TtsStartChoice.SAVED_POSITION, remember = true, dark = true)

    @Test fun glassStartDialogCanRememberCurrentPage() =
        verifyStart(TtsStartChoice.CURRENT_PAGE, remember = true, glass = true)

    @Test fun eInkStartDialogCanResumeWithoutRemembering() =
        verifyStart(TtsStartChoice.SAVED_POSITION, remember = false, glass = true, eInk = true)

    private fun verifyStart(choice: TtsStartChoice, remember: Boolean, dark: Boolean = false,
        glass: Boolean = false, eInk: Boolean = false) {
        val context = RuntimeEnvironment.getApplication()
        val prompt = mutableStateOf<TtsListeningPosition?>(TtsListeningPosition("book", 9, 2, 450, 123))
        var chosen: Pair<TtsStartChoice, Boolean>? = null
        val ink = if (dark) Color.White else Color.Black
        compose.setContent {
            CompositionLocalProvider(
                LocalAppTheme provides if (glass) "liquid_glass" else "lumi",
                LocalIsDarkTheme provides dark, LocalEInkMode provides eInk, LocalMotionEnabled provides false
            ) {
                TtsStartDialog(prompt.value, "Chapter ten", if (dark) Color.Black else Color.White,
                    ink, eInk, null, onDismiss = { prompt.value = null },
                    onChoose = { value, checked -> chosen = value to checked; prompt.value = null })
            }
        }
        val title = context.getString(R.string.tts_start_title)
        compose.onNodeWithText(title).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                getLayout(results)
                assertEquals(if (eInk) Color.Black else ink, results.single().layoutInput.style.color)
            }
        compose.onNodeWithText(context.getString(R.string.tts_start_message, "Chapter ten")).assertIsDisplayed()
        val checkbox = compose.onNodeWithText(context.getString(R.string.tts_start_remember_choice))
        checkbox.assertIsDisplayed().assertIsOff()
        if (remember) checkbox.performClick().assertIsOn()
        compose.runOnIdle { assertEquals(null, chosen) }
        compose.onNodeWithText(context.getString(if (choice == TtsStartChoice.CURRENT_PAGE)
            R.string.tts_start_current_page else R.string.tts_start_saved_position)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(choice to remember, chosen) }
        compose.onNodeWithText(title).assertDoesNotExist()
    }

}
