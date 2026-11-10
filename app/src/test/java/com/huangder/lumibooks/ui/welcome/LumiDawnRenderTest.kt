package com.huangder.lumibooks.ui.welcome

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.brand.LumiCodename
import com.huangder.lumibooks.ui.splash.SplashScreen
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the "Lumi Dawn" splash, introduction and the three feature stories to
 * build/reports/lumi-dawn/ (resting frames, motion reduced) and checks the key content.
 * Robolectric cannot run the glass lens, so "glass" renders show the fallback surface.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LumiDawnRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun show(
        dark: Boolean = false,
        ink: Boolean = false,
        theme: String = "lumi",
        accent: String? = null,
        content: @Composable () -> Unit
    ) {
        compose.setContent {
            view = LocalView.current
            if (accent == null) {
                EBookReaderTheme(appTheme = theme, darkTheme = dark, eInkMode = ink,
                    motionPreference = MotionPreference.REDUCED) { content() }
            } else {
                EBookReaderTheme(appTheme = theme, darkTheme = dark, eInkMode = ink, appAccentColor = accent,
                    motionPreference = MotionPreference.REDUCED) { content() }
            }
        }
    }

    private fun save(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = File("build/reports/lumi-dawn").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun splash(dark: Boolean) {
        show(dark) { SplashScreen(isDark = dark, iconStyle = "lumi2") }
        compose.onNodeWithContentDescription("Lumi ${LumiCodename.CURRENT}").assertIsDisplayed()
        save("01-splash-${if (dark) "dark" else "light"}")
    }

    private fun intro(newInstall: Boolean, dark: Boolean = false, theme: String = "lumi", suffix: String = "") {
        show(dark, theme = theme) {
            WelcomeScreen(
                isNewInstallation = newInstall,
                shouldShowLanguageSetup = false,
                initialLanguage = "zh-CN",
                initialEInkMode = false,
                isEInkMode = false,
                isDark = dark,
                isLiquidGlass = theme == "liquid_glass",
                onFinished = {},
                onOpenSponsor = {},
                onLanguageSetupComplete = { _, _ -> },
                startOnIntroduction = true
            )
        }
        val greeting = if (newInstall) R.string.welcome_title else R.string.welcome_update_title
        compose.onNodeWithText(context.getString(greeting)).assertIsDisplayed()
        val button = compose.onNodeWithText(context.getString(R.string.welcome_start_using)).assertIsDisplayed()
        assertTrue(button.fetchSemanticsNode().boundsInRoot.bottom <= view.height)
        save("02-intro-${if (newInstall) "new" else "update"}-${if (dark) "dark" else "light"}$suffix")
    }

    private fun story(
        index: Int,
        dark: Boolean = false,
        ink: Boolean = false,
        theme: String = "lumi",
        accent: String? = null,
        journal: Boolean = true,
        suffix: String = ""
    ) {
        show(dark, ink, theme, accent) {
            com.huangder.lumibooks.ui.brand.LumiDawnStage {
                WelcomeFeatureStoriesPage(hasReadingJournal = journal, onContinue = {}, initialStory = index)
            }
        }
        val title = when (WelcomeStory.entries[index]) {
            WelcomeStory.JOURNAL -> if (journal) R.string.welcome_story_journal_title else R.string.welcome_story_insights_title
            WelcomeStory.LISTENING -> R.string.welcome_story_listening_title
            WelcomeStory.STATS -> R.string.welcome_story_stats_title
        }
        compose.onNodeWithText(context.getString(title)).assertIsDisplayed()
        val next = compose.onNodeWithTag("welcome-story-next").assertIsDisplayed()
        assertTrue(next.fetchSemanticsNode().boundsInRoot.bottom <= view.height)
        val mode = when { ink -> "ink"; dark -> "dark"; else -> "light" }
        save("03-story-${index + 1}-${WelcomeStory.entries[index].name.lowercase()}-$mode$suffix")
    }

    @Test fun splashLight() = splash(dark = false)
    @Test fun splashDark() = splash(dark = true)
    @Test fun introUpdateLight() = intro(newInstall = false)
    @Test fun introNewDark() = intro(newInstall = true, dark = true)
    @Test fun introGlass() = intro(newInstall = true, theme = "liquid_glass", suffix = "-glass")
    @Test fun storyJournalLight() = story(0)
    @Test fun storyListeningLight() = story(1)
    @Test fun storyStatsLight() = story(2)
    @Test fun storyJournalDark() = story(0, dark = true)
    @Test fun storyStatsDark() = story(2, dark = true)
    @Test fun storyInsightsWithoutJournal() = story(0, journal = false, suffix = "-oss")
    @Test fun storyListeningInk() = story(1, ink = true)
    @Test fun storyStatsGreenAccent() = story(2, accent = "#34C759", suffix = "-green")
    @Test fun storyJournalBlueAccentDark() = story(0, dark = true, accent = "#007AFF", suffix = "-blue")

    @Test @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi")
    fun storyStatsEnglish() = story(2, suffix = "-en")

    @Test @Config(qualifiers = "zh-rCN-w320dp-h568dp-xhdpi")
    fun storyJournalSmallScreen() = story(0, suffix = "-small")
}
