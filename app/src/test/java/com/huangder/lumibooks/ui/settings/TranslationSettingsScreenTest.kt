package com.huangder.lumibooks.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.domain.model.MenuAnimationStyle
import com.huangder.lumibooks.translation.*
import com.huangder.lumibooks.ui.theme.*
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TranslationSettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val modelCatalog = mutableStateOf<TranslationModelCatalog>(TranslationModelCatalog.Idle)

    private fun mount(dark: Boolean = false, restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            view = LocalView.current
            CompositionLocalProvider(LocalMotionEnabled provides true, LocalAppTheme provides "lumi",
                LocalIsDarkTheme provides dark, LocalMenuAnimationStyle provides MenuAnimationStyle.LIQUID) {
                DetailPage("AI translation", {}) {
                    TranslationSettingsContent(
                        TranslationConfiguration(TranslationSettings(model = "saved-model"), false),
                        TranslationSettingsAction(), { _, _ -> }, { _, _ -> }, {},
                        modelCatalog.value,
                        onFetchModels = { _, _ -> modelCatalog.value = TranslationModelCatalog.Ready(listOf("model-a", "model-b")) },
                        onConnectionEdited = { modelCatalog.value = TranslationModelCatalog.Idle })
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.waitForIdle()
        save(if (dark) "settings-dark" else "settings-lumi")
    }

    private fun save(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/translation/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun providerUsesLumiMenuAndUpdatesEndpoint() {
        mount()
        compose.onNodeWithText("MiMo").performClick()
        compose.onNodeWithText("DeepSeek").assertIsDisplayed()
        save("provider-menu-lumi")
        compose.onNodeWithText("DeepSeek").performClick()
        compose.onNodeWithTag("translation-base-url").assertTextEquals("https://api.deepseek.com/v1")
        compose.onNodeWithTag("translation-model").assertTextEquals("")
        save("settings-deepseek")
    }

    @Test fun selectingCurrentProviderKeepsModelAndInputsPreserveDraftCharacters() {
        mount(dark = true)
        compose.onNodeWithText("MiMo").performClick()
        compose.onNodeWithText("MiMo").performClick()
        compose.onNodeWithTag("translation-model").assertTextEquals("saved-model")
        compose.onNodeWithTag("translation-base-url").performTextReplacement("https://example.com/")
        compose.onNodeWithTag("translation-base-url").assertTextEquals("https://example.com/")
        save("settings-dark-focused")
    }

    @Test fun apiKeyIsNotRestoredWithInstanceStateButNonsecretDraftIs() {
        val restoration = StateRestorationTester(compose)
        mount(restoration = restoration)
        compose.onNodeWithTag("translation-model").performTextReplacement("my-model")
        compose.onNodeWithTag("translation-api-key").performTextReplacement("test-secret")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("translation-model").assertTextEquals("my-model")
        compose.onNodeWithTag("translation-api-key").assertTextEquals("")
    }

    @Test fun modelFieldAllowsBothTypingAndSelectingFetchedModels() {
        mount()
        compose.onNodeWithContentDescription("Choose model").assertDoesNotExist()
        compose.onNodeWithContentDescription("Fetch model names").performClick()
        compose.onNodeWithContentDescription("Choose model").assertIsDisplayed()
        save("model-field-fetched")
        compose.onNodeWithContentDescription("Choose model").performClick()
        compose.onNodeWithText("model-b").assertIsDisplayed()
        save("model-menu-lumi")
        compose.onNodeWithText("model-b").performClick()
        compose.onNodeWithTag("translation-model").assertTextEquals("model-b")
        compose.onNodeWithTag("translation-model").performTextReplacement("manual-model")
        compose.onNodeWithTag("translation-model").assertTextEquals("manual-model")
        compose.onNodeWithContentDescription("Choose model").assertIsDisplayed()
        compose.onNodeWithTag("translation-base-url").performTextReplacement("https://another.example/v1")
        compose.onNodeWithContentDescription("Choose model").assertDoesNotExist()
    }
}
