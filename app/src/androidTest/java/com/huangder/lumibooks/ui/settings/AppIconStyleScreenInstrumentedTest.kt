package com.huangder.lumibooks.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.AppIconStyle
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconStyleScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun optionsExposeSelectionAndUpdateImmediately() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lumi2Label = context.getString(R.string.icon_style_lumi2)
        val classicLabel = context.getString(R.string.icon_style_classic)
        var selectedStyle by mutableStateOf(AppIconStyle.LUMI_2.storedValue)

        composeRule.setContent {
            EBookReaderTheme(darkTheme = false) {
                AppIconStyleOptions(
                    selectedStyle = selectedStyle,
                    onSelect = { selectedStyle = it }
                )
            }
        }

        composeRule.onNodeWithText(lumi2Label).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText(classicLabel).assertIsDisplayed().assertIsNotSelected()

        composeRule.onNodeWithText(classicLabel).performClick()

        composeRule.onNodeWithText(lumi2Label).assertIsNotSelected()
        composeRule.onNodeWithText(classicLabel).assertIsSelected()
    }
}
