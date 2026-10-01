package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnnotationFilterSizingTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test fun compactFiltersKeepTheirSizeDuringMenuReturn() {
        val glass = mutableStateOf(false)
        val tag = mutableStateOf<String?>(null)
        val color = mutableStateOf<String?>("#FFEB3B")
        lateinit var host: LiquidGlassMenuHostState
        compose.setContent {
            EBookReaderTheme(appTheme = if (glass.value) "liquid_glass" else "lumi", darkTheme = false) {
                CompositionLocalProvider(LocalAppTheme provides if (glass.value) "liquid_glass" else "lumi") {
                    LiquidGlassMenuHost(Modifier.fillMaxSize()) {
                        host = LocalLiquidGlassMenuHost.current!!
                        AnnotationListFilters(listOf("Research"), tag.value, { tag.value = it },
                            listOf("#FFEB3B"), color.value, { color.value = it }, null, {}, true)
                    }
                }
            }
        }
        listOf(false, true).forEach { liquid ->
            compose.runOnIdle { glass.value = liquid; tag.value = null; color.value = "#FFEB3B" }
            val trigger = compose.onNodeWithText(label(R.string.annotation_tag_all))
            trigger.assertHeightIsEqualTo(32.dp)
            compose.onNodeWithText(label(R.string.annotation_tag_color)).assertHeightIsEqualTo(32.dp)
            compose.onNodeWithText(label(R.string.annotation_tag_line)).assertHeightIsEqualTo(32.dp)
            trigger.performClick()
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(label(R.string.annotation_tag_none)).performClick()
            compose.runOnIdle {
                assertNotNull(host.displayedMenu)
                assertNull("The old trigger must remain until its menu returns", tag.value)
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.runOnIdle { assertEquals("", tag.value); assertNull(host.displayedMenu) }
            compose.onNodeWithText(label(R.string.annotation_tag_none)).assertHeightIsEqualTo(32.dp)

            compose.onNodeWithText(label(R.string.annotation_tag_color)).performClick()
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(label(R.string.annotation_tag_all_colors)).performClick()
            compose.runOnIdle { assertEquals("#FFEB3B", color.value) }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.runOnIdle { assertNull(color.value); assertNull(host.displayedMenu) }
            compose.onNodeWithText(label(R.string.annotation_tag_color)).assertHeightIsEqualTo(32.dp)
        }
    }
}
