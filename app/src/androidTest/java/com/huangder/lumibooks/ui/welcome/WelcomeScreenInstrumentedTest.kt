package com.huangder.lumibooks.ui.welcome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun introductionShowsCodenameWordmarkAndUpdateNotice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        showWelcome(isNewInstallation = false, startOnIntroduction = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_update_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Lumi ${com.huangder.lumibooks.ui.brand.LumiCodename.CURRENT}").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_title)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.welcome_exit)).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.welcome_app_icon_description))
            .assertDoesNotExist()
    }

    @Test
    fun newInstallGoesThroughUpdatesWithoutThemeStep() {
        // The official build replaces the last page with the Hilt-backed trial invitation.
        assumeFalse(BuildConfig.PREMIUM_EDITION)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        showWelcome(isNewInstallation = true, startOnIntroduction = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).performClick()
        walkThroughStories(context)
        composeRule.onNodeWithText(context.getString(R.string.welcome_support_title)).assertIsDisplayed()
    }

    @Test
    fun updateWalkthroughGoesDirectlyToSupport() {
        // The official build replaces this page with the Hilt-backed trial invitation.
        assumeFalse(BuildConfig.PREMIUM_EDITION)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        showWelcome(isNewInstallation = false, startOnIntroduction = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).performClick()
        walkThroughStories(context)
        composeRule.onNodeWithText(context.getString(R.string.welcome_support_title)).assertIsDisplayed()
    }

    @Test
    fun supportPageContainsCopyAndActionsWithoutDecorativeEmoji() {
        // The official build replaces this page with the Hilt-backed trial invitation.
        assumeFalse(BuildConfig.PREMIUM_EDITION)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        showWelcome(isNewInstallation = false, startOnSupport = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_support_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_open_sponsor)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).assertIsDisplayed()
        composeRule.onNodeWithText("☕").assertDoesNotExist()
        composeRule.onNodeWithText("✨").assertDoesNotExist()
        composeRule.onNodeWithText("💗").assertDoesNotExist()
    }

    private fun walkThroughStories(context: android.content.Context) {
        composeRule.onNodeWithText(context.getString(R.string.welcome_story_journal_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("welcome-story-next").performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_story_listening_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("welcome-story-next").performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_story_stats_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("welcome-story-next").performClick()
    }

    private fun showWelcome(
        isNewInstallation: Boolean,
        startOnIntroduction: Boolean = false,
        startOnSupport: Boolean = false
    ) {
        composeRule.setContent {
            EBookReaderTheme(
                darkTheme = false,
                eInkMode = false
            ) {
                WelcomeScreen(
                    isNewInstallation = isNewInstallation,
                    shouldShowLanguageSetup = false,
                    initialLanguage = "zh-CN",
                    initialEInkMode = false,
                    isEInkMode = false,
                    isDark = false,
                    isLiquidGlass = false,
                    onFinished = {},
                    onOpenSponsor = {},
                    onLanguageSetupComplete = { _, _ -> },
                    startOnIntroduction = startOnIntroduction,
                    startOnSupport = startOnSupport
                )
            }
        }
    }
}
