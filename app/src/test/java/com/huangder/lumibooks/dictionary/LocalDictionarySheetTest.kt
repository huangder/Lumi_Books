package com.huangder.lumibooks.dictionary

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.huangder.lumibooks.ui.reader.LocalDictionarySheetContent
import com.huangder.lumibooks.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import com.huangder.lumibooks.translation.TranslationState
import com.huangder.lumibooks.translation.TranslationError

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalDictionarySheetTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var closed = false
    private var sourceBackdropProvided = false
    private val dictionary = DictionaryDescriptor("wordnet", "Open English WordNet", "", "1", "WordNet",
        "https://github.com/globalwordnet/english-wordnet", "CC BY 4.0", "WordNet contributors", "Filtered", "2025", "", 0, 0, "", 1, 1)
    private val entry = DictionaryEntry("flexibility", "flexibility", pronunciation = "/ˌfleksəˈbɪləti/", senses = listOf(
        DictionarySense("1", "The ability to change or be changed easily according to the situation.", "noun",
            listOf(DictionaryExample("The schedule offers flexibility.", "时间安排具有灵活性。")), listOf("adaptability"))), partiallyFiltered = true)

    private fun mount(result: DictionaryLookupResult?, dark: Boolean = false, fontScale: Float = 1f, eink: Boolean = false, glass: Boolean = false,
        translation: TranslationState = TranslationState.Hidden, onRetry: () -> Unit = {}) {
        sourceBackdropProvided = false
        compose.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalMotionEnabled provides false, LocalIsDarkTheme provides dark,
                LocalEInkMode provides eink, LocalAppTheme provides if (glass) "liquid_glass" else "lumi",
                LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.fillMaxSize().background(AppColors.WindowBg)) {
                    LocalDictionarySheetContent("flexibility", result, null, !glass, { closed = true }, {}, {},
                        translation = translation, onRetryTranslation = onRetry) { _, backdrop, _ ->
                        sourceBackdropProvided = backdrop != null
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun save(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/dictionaries/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun opensDetailsAndReturnsToResultsWithoutClosing() {
        mount(DictionaryLookupResult(listOf(DictionaryResult(dictionary, listOf(entry))), true, enabledCount = 1))
        compose.onNodeWithText(dictionary.name).assertIsDisplayed().performClick()
        compose.onNodeWithText("The schedule offers flexibility.").assertIsDisplayed()
        save("details-light")
        compose.onNodeWithContentDescription("Back to dictionary results").performClick()
        compose.onNodeWithText(dictionary.name).assertIsDisplayed()
        assertFalse(closed)
        save("results-light")
    }
    @Test fun allFilteredHasDistinctExplanationAndFeedbackAtLargeFont() {
        mount(DictionaryLookupResult(filtered = true, enabledCount = 1), dark = true, fontScale = 1.6f)
        compose.onNodeWithText("No definitions can be shown under the current content rules.").assertIsDisplayed()
        compose.onNodeWithTag("dictionary-results").performScrollToNode(hasText("Suggest a dictionary / Report an issue"))
        compose.onNodeWithText("Suggest a dictionary / Report an issue").assertIsDisplayed()
        save("filtered-dark-large")
    }
    @Test fun sourcesUseTheSheetBackdropInGlassTheme() {
        mount(DictionaryLookupResult(listOf(DictionaryResult(dictionary, listOf(entry))), enabledCount = 1), glass = true)
        compose.onNodeWithText("Sources and licenses").performClick()
        compose.runOnIdle { assertTrue(sourceBackdropProvided) }
    }
    @Test @Config(qualifiers = "en-rUS-w960dp-h600dp-xhdpi")
    fun tabletUsesReadableSheetAndEinkFallback() {
        mount(DictionaryLookupResult(listOf(DictionaryResult(dictionary, listOf(entry))), enabledCount = 1), eink = true, glass = true)
        compose.onNodeWithText(dictionary.name).assertIsDisplayed()
        save("tablet-eink")
    }

    @Test fun translationFollowsDictionaryResultsAndSurvivesDetailNavigation() {
        mount(DictionaryLookupResult(listOf(DictionaryResult(dictionary, listOf(entry))), enabledCount = 1),
            translation = TranslationState.Success("灵活性；适应变化的能力。"))
        compose.onNodeWithText(dictionary.name).performClick()
        compose.onNodeWithText("AI translation").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to dictionary results").performClick()
        compose.onNodeWithTag("dictionary-results").performScrollToNode(hasText("AI translation"))
        compose.onNodeWithText("灵活性；适应变化的能力。").assertExists()
        save("translation-after-dictionary")
        compose.onNodeWithTag("dictionary-results").performScrollToNode(hasText("Sources and licenses"))
        compose.onNodeWithText("Sources and licenses").assertIsDisplayed()
    }

    @Test fun translationWorksWithoutInstalledDictionariesInDarkLargeText() {
        mount(DictionaryLookupResult(enabledCount = 0), dark = true, fontScale = 1.6f,
            translation = TranslationState.Success("这是一段句子的译文，即使没有安装词典也可以显示。"))
        compose.onNodeWithTag("dictionary-results").performScrollToNode(hasText("AI translation"))
        compose.onNodeWithText("AI translation").assertIsDisplayed()
        compose.onNodeWithText("这是一段句子的译文，即使没有安装词典也可以显示。").assertExists()
        save("translation-dark-large")
    }

    @Test fun translationLoadingIsIndependentOfLocalLookup() {
        mount(null, translation = TranslationState.Loading)
        compose.onNodeWithContentDescription("Translating…").assertIsDisplayed()
        val title = compose.onNodeWithText("AI translation").fetchSemanticsNode().boundsInRoot
        val spinner = compose.onNodeWithContentDescription("Translating…").fetchSemanticsNode().boundsInRoot
        assertTrue(spinner.left > title.right)
        assertTrue(spinner.height < title.height)
        save("translation-loading")
    }

    @Test fun translationFailureCanRetryWithoutLocalMatches() {
        var retries = 0
        mount(DictionaryLookupResult(enabledCount = 1), eink = true, glass = true,
            translation = TranslationState.Failure(TranslationError.NETWORK), onRetry = { retries++ })
        compose.onNodeWithTag("dictionary-results").performScrollToNode(hasText("Retry translation"))
        compose.onNodeWithText("Retry translation").performClick()
        assertEquals(1, retries)
        save("translation-eink-failure")
    }

    @Test fun longTranslationScrollsToCopyAndSettings() {
        val longText = (1..25).joinToString("\n") { "第 $it 句译文，保留完整内容。" }
        mount(DictionaryLookupResult(enabledCount = 1), glass = true, translation = TranslationState.Success(longText))
        compose.onNodeWithText(longText).assertExists()
        compose.onNodeWithTag("dictionary-results").performScrollToNode(hasText("Copy translation"))
        compose.onNodeWithText("Copy translation").assertIsDisplayed()
        compose.onNodeWithTag("dictionary-results").performScrollToNode(hasText("Translation settings"))
        compose.onNodeWithText("Translation settings").assertIsDisplayed()
        save("translation-long-glass")
    }

    @Test fun arrivingLocalResultsShowFromTopInsteadOfAnchoringToAi() {
        val lookup = mutableStateOf<DictionaryLookupResult?>(null)
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                LocalDictionarySheetContent("flexibility", lookup.value, null, true, {}, {}, {},
                    translation = TranslationState.Success("AI translation result")) { _, _, _ -> }
            }
        }
        compose.onNodeWithText("AI translation result").assertIsDisplayed()
        compose.runOnIdle {
            lookup.value = DictionaryLookupResult((1..6).map { index ->
                DictionaryResult(dictionary.copy(id = "dict-$index", name = "Local dictionary $index"), listOf(entry))
            }, enabledCount = 6)
        }
        compose.onNodeWithText("Local dictionary 1").assertIsDisplayed()
        compose.onNodeWithText("AI translation result").assertIsNotDisplayed()
        save("local-results-stay-at-top")
    }

    @Test fun translationActionsShareOneRow() {
        mount(DictionaryLookupResult(), translation = TranslationState.Success("译文"))
        val copy = compose.onNodeWithText("Copy translation").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithText("Translation settings").fetchSemanticsNode().boundsInRoot
        assertEquals(copy.center.y, settings.center.y, 1f)
        assertTrue(copy.right <= settings.left)
    }

    @Test fun manualTranslationEntryRemainsWhenAutomaticTranslationIsOff() {
        var clicks = 0
        mount(DictionaryLookupResult(), translation = TranslationState.Manual, onRetry = { clicks++ })
        compose.onNodeWithText("AI translation").assertIsDisplayed()
        compose.onNodeWithText("Translate").performClick()
        assertEquals(1, clicks)
        compose.onNodeWithContentDescription("Translating…").assertDoesNotExist()
        save("translation-manual")
    }
}
