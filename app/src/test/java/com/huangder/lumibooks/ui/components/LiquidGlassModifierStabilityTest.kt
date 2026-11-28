package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A glass modifier that differs between recompositions makes the backdrop node rebuild its
 * whole blur/lens effect chain. Unrelated recompositions must hand back an equal modifier,
 * and a real input change must still produce a different one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiquidGlassModifierStabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sheetSurfaceModifierIsStableUntilAnInputChanges() {
        val tick = mutableIntStateOf(0)
        val dark = mutableStateOf(false)
        val seen = mutableListOf<Modifier>()
        compose.setContent {
            CompositionLocalProvider(LocalAppTheme provides "liquid_glass", LocalIsDarkTheme provides dark.value) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    tick.intValue
                    val surface = Modifier.liquidGlassSheetSurface(
                        fallbackColor = Color.White,
                        shape = AppRoundedCornerShape(24.dp),
                        backdrop = backdrop,
                        cardSheet = true
                    )
                    seen += surface
                    Box(Modifier.size(120.dp).then(surface))
                }
            }
        }
        compose.waitForIdle()
        tick.intValue = 1
        compose.waitForIdle()
        tick.intValue = 2
        compose.waitForIdle()
        assertTrue("expected recompositions, saw ${seen.size}", seen.size >= 3)
        seen.drop(1).forEach { assertEquals(seen.first(), it) }

        val before = seen.last()
        dark.value = true
        compose.waitForIdle()
        assertNotEquals(before, seen.last())
    }
}
