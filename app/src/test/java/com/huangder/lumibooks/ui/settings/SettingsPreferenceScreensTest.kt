package com.huangder.lumibooks.ui.settings

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.dataStore
import com.huangder.lumibooks.domain.model.LiquidGlassStyle
import com.huangder.lumibooks.tts.TtsReaderResumeChoice
import com.huangder.lumibooks.tts.TtsStartChoice
import com.huangder.lumibooks.tts.ListeningDisplayStyle
import com.huangder.lumibooks.tts.ListeningPageSettings
import com.huangder.lumibooks.tts.SubtitleSeekGesture
import com.huangder.lumibooks.ui.theme.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPreferenceScreensTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun label(id: Int) = context.getString(id)

    @Test fun listeningPageDropdownsSelectDisplayAndGestureIndependently() {
        var settings by mutableStateOf(ListeningPageSettings())
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                ListeningPageSettingsScreen(settings,
                    onStyle = { settings = settings.copy(defaultDisplayStyle = it) },
                    onGesture = { settings = settings.copy(seekGesture = it) }, onBack = {})
            }
        }
        compose.onNodeWithText(label(R.string.tts_page_style_cover)).performClick()
        compose.onNodeWithText(label(R.string.tts_page_style_subtitles)).performClick()
        compose.onNodeWithText(label(R.string.tts_page_seek_double_tap)).performClick()
        compose.onNodeWithText(label(R.string.tts_page_seek_single_tap)).performClick()
        compose.runOnIdle {
            assertEquals(ListeningDisplayStyle.SUBTITLES, settings.defaultDisplayStyle)
            assertEquals(SubtitleSeekGesture.SINGLE_TAP, settings.seekGesture)
        }
        compose.onNodeWithText(label(R.string.tts_page_style_subtitles)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.tts_page_seek_single_tap)).assertIsDisplayed()
        capture(view, "listening-page-settings")
    }

    @Test fun listeningChoicesPersistWhenThePageReopensAndCanBeResetToAsk() = runBlocking {
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        manager.saveTtsStartChoice(TtsStartChoice.SAVED_POSITION)
        manager.saveTtsReaderResumeChoice(TtsReaderResumeChoice.KEEP_READING)
        manager.saveTtsContinueOnReaderExit(true)
        var showPreferences by mutableStateOf(true)
        compose.setContent {
            val start by manager.ttsStartChoice.collectAsState(TtsStartChoice.SAVED_POSITION)
            val resume by manager.ttsReaderResumeChoice.collectAsState(TtsReaderResumeChoice.KEEP_READING)
            val exit by manager.ttsContinueOnReaderExit.collectAsState(true)
            val scope = rememberCoroutineScope()
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                if (showPreferences) {
                    DetailPage(label(R.string.settings_listening_preferences), {}) {
                        ListeningPreferencesContent(
                            SettingsUiState(ttsStartChoice = start, ttsReaderResumeChoice = resume, ttsContinueOnReaderExit = exit),
                            { value -> scope.launch { manager.saveTtsStartChoice(value) } },
                            { value -> scope.launch { manager.saveTtsReaderResumeChoice(value) } },
                            { value -> scope.launch { manager.saveTtsContinueOnReaderExit(value) } }
                        )
                    }
                } else Text("Other page")
            }
        }
        compose.onNodeWithText(label(R.string.tts_start_setting)).performClick()
        compose.onNodeWithText(label(R.string.tts_start_current_page)).performClick()
        compose.onNodeWithText(label(R.string.tts_listening_resume_setting)).performClick()
        compose.onNodeWithText(label(R.string.tts_listening_resume_jump)).performClick()
        compose.onNodeWithText(label(R.string.tts_exit_setting)).performClick()
        compose.onNodeWithText(label(R.string.tts_exit_stop)).performClick()
        compose.waitForIdle()
        val reloaded = DataStoreManager(context)
        assertEquals(TtsStartChoice.CURRENT_PAGE, reloaded.ttsStartChoice.first())
        assertEquals(TtsReaderResumeChoice.LISTENING_POSITION, reloaded.ttsReaderResumeChoice.first())
        assertEquals(false, reloaded.ttsContinueOnReaderExit.first())
        compose.runOnIdle { showPreferences = false }
        compose.onNodeWithText("Other page").assertIsDisplayed()
        compose.runOnIdle { showPreferences = true }
        compose.onNodeWithText(label(R.string.tts_start_current_page)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.tts_listening_resume_jump)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.tts_exit_stop)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.tts_start_setting)).performClick()
        compose.onNodeWithText(label(R.string.tts_start_ask_each_time)).performClick()
        compose.waitForIdle()
        // DataStore resumes the save coroutine on the main dispatcher. Keep pumping
        // Compose while waiting for disk persistence instead of blocking that dispatcher.
        compose.waitUntil(timeoutMillis = 5_000) {
            runBlocking { reloaded.ttsStartChoice.first() == null }
        }
        assertNull(reloaded.ttsStartChoice.first())
    }

    @Test fun glassControlsFollowCapabilityAndEInkWithoutResettingStoredOptions() {
        var supported by mutableStateOf(true)
        var eInk by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var hdr by mutableStateOf(true)
        var state by mutableStateOf(SettingsUiState(appTheme = "liquid_glass", liquidGlassTransparency = .7f))
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme = "lumi", darkTheme = dark, eInkMode = eInk, motionPreference = MotionPreference.REDUCED) {
                CompositionLocalProvider(
                    LocalLiquidGlassCapability provides LiquidGlassCapability(supported = supported, hdrSupported = hdr),
                    LocalDensity provides Density(1f, 1.5f)
                ) {
                    DetailPage(label(R.string.settings_visual_motion), {}) {
                        VisualMotionContent(
                            state.copy(eInkModeEnabled = eInk),
                            { state = state.copy(liquidGlassStyle = LiquidGlassStyle.fromStoredValue(it)) },
                            { state = state.copy(liquidGlassTransparency = it) },
                            { state = state.copy(liquidGlassTransparency = it) },
                            { state = state.copy(liquidGlassHdrHighlightEnabled = it) },
                            { state = state.copy(cardOutlinesEnabled = it) },
                            { state = state.copy(reduceTransparencyEnabled = it) },
                            { state = state.copy(predictiveBackEnabled = it) },
                            { state = state.copy(motionPreference = it) },
                            { state = state.copy(menuAnimationStyle = it) },
                            { state = state.copy(bookOpenTransition = it) }
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(label(R.string.liquid_glass_style)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.liquid_glass_sampling_quality)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.liquid_glass_hdr_highlight)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(state.liquidGlassHdrHighlightEnabled) }
        capture(view, "visual-large-font-light")
        compose.runOnIdle { dark = true }
        capture(view, "visual-large-font-dark")
        compose.runOnIdle { hdr = false }
        compose.onNodeWithText(label(R.string.liquid_glass_hdr_highlight)).assertDoesNotExist()
        compose.runOnIdle { supported = false }
        compose.onNodeWithText(label(R.string.liquid_glass_style)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.liquid_glass_sampling_quality)).assertDoesNotExist()
        compose.runOnIdle { supported = true; eInk = true }
        compose.onNodeWithText(label(R.string.liquid_glass_style)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.liquid_glass_sampling_quality)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.reduce_transparency_label)).performScrollTo().assertIsDisplayed()
        capture(view, "visual-large-font-eink")
        compose.runOnIdle {
            assertEquals(.7f, state.liquidGlassTransparency)
            assertTrue(state.liquidGlassHdrHighlightEnabled)
            eInk = false
        }
        compose.onNodeWithText(label(R.string.liquid_glass_style)).performScrollTo().assertIsDisplayed()
    }

    private fun capture(view: View, name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/settings-hierarchy/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
