package com.huangder.lumibooks.ui.welcome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun introductionUsesUnifiedVersionTwoDesign() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        showWelcome(isNewInstallation = false, startOnIntroduction = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_brand_name)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_major_version)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_update_title)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.welcome_exit)).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.welcome_app_icon_description))
            .assertDoesNotExist()
    }

    @Test
    fun startButtonKeepsExistingInstallSpecificNextStep() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        showWelcome(isNewInstallation = true, startOnIntroduction = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.welcome_liquid_glass_title)).assertIsDisplayed()
    }

    @Test
    fun supportPageContainsCopyAndActionsWithoutDecorativeEmoji() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        showWelcome(isNewInstallation = false, startOnSupport = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_support_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_open_sponsor)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).assertIsDisplayed()
        composeRule.onNodeWithText("☕").assertDoesNotExist()
        composeRule.onNodeWithText("✨").assertDoesNotExist()
        composeRule.onNodeWithText("💗").assertDoesNotExist()
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
                    onEnableLiquidGlass = {},
                    startOnIntroduction = startOnIntroduction,
                    startOnSupport = startOnSupport
                )
            }
        }
    }
}
