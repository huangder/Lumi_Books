package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TocReturnButtonInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalThemeButtonHasDescriptionAndClicks() = verifyTheme("lumi")

    @Test
    fun liquidGlassButtonHasDescriptionAndClicks() = verifyTheme("liquid_glass")

    private fun verifyTheme(theme: String) {
        var clicks = 0
        val description = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.reader_toc_return_to_current)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppTheme provides theme,
                LocalLiquidGlassCapability provides LiquidGlassCapability(
                    supported = true,
                    hdrSupported = false
                )
            ) {
                TocReturnToCurrentButton(
                    visible = true,
                    motionEnabled = false,
                    onClick = { clicks++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription(description).performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
