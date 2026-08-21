package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LiquidGlassButtonInteractionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tapInvokesClickExactlyOnce() {
        var clickCount = 0
        setButtonContent(onClick = { clickCount++ })

        composeRule.onNodeWithTag(ButtonTag).performTouchInput {
            down(center)
            up()
        }

        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun draggingOutsideCancelsClickAndNextTapStillWorks() {
        var clickCount = 0
        setButtonContent(onClick = { clickCount++ })

        composeRule.onNodeWithTag(ButtonTag).performTouchInput {
            down(center)
            moveTo(center.copy(x = center.x + 400f), delayMillis = 120)
            up()
        }
        composeRule.runOnIdle { assertEquals(0, clickCount) }

        composeRule.onNodeWithTag(ButtonTag).performTouchInput {
            down(center)
            up()
        }
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun disabledButtonDoesNotClick() {
        var clickCount = 0
        setButtonContent(enabled = false, onClick = { clickCount++ })

        composeRule.onNodeWithTag(ButtonTag).performTouchInput {
            down(center)
            up()
        }

        composeRule.runOnIdle { assertEquals(0, clickCount) }
    }

    @Test
    fun customShapeTapInvokesClickExactlyOnce() {
        var clickCount = 0
        setButtonContent(
            shape = G2ContinuousCornerShape(24f),
            onClick = { clickCount++ }
        )

        composeRule.onNodeWithTag(ButtonTag).performTouchInput {
            down(center)
            up()
        }

        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    private fun setButtonContent(
        enabled: Boolean = true,
        shape: Shape = androidx.compose.foundation.shape.CircleShape,
        onClick: () -> Unit
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppTheme provides "liquid_glass",
                LocalLiquidGlassCapability provides LiquidGlassCapability(
                    supported = true,
                    hdrSupported = false
                )
            ) {
                LiquidGlassSurface(
                    shape = shape,
                    fallbackColor = Color.White,
                    enabled = enabled,
                    onClick = onClick,
                    modifier = Modifier
                        .size(width = 120.dp, height = 48.dp)
                        .testTag(ButtonTag)
                ) {}
            }
        }
    }

    private companion object {
        const val ButtonTag = "liquidGlassButton"
    }
}
