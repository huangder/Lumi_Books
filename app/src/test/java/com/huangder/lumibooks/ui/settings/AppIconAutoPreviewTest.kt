package com.huangder.lumibooks.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppIconAutoPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "notnight")
    fun lightAutoPreviewLoadsInCompose() = showAutoPreview()

    @Test
    @Config(qualifiers = "night")
    fun darkAutoPreviewLoadsInCompose() = showAutoPreview()

    private fun showAutoPreview() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            EBookReaderTheme {
                AppIconStyleOptions(selectedStyle = "lumi2_auto", onSelect = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.icon_style_lumi2)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.icon_style_appearance_auto_hint))
            .assertIsDisplayed()
    }
}
