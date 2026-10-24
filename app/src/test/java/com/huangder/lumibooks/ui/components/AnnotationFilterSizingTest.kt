package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnnotationFilterSizingTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test fun compactFiltersKeepTheirSizeDuringMenuReturn() {
        val glass = mutableStateOf(false)
        val tag = mutableStateOf<String?>(null)
        val color = mutableStateOf<String?>("#FFEB3B")
        lateinit var host: LiquidGlassMenuHostState
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
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
            val bounds = listOf(R.string.annotation_tag_all, R.string.annotation_tag_color, R.string.annotation_tag_line)
                .map { compose.onNodeWithText(label(it)).fetchSemanticsNode().boundsInRoot }
            bounds.drop(1).forEach {
                assertEquals(bounds.first().top, it.top, 0.5f)
                assertEquals(bounds.first().bottom, it.bottom, 0.5f)
                assertTrue(abs(bounds.first().width - it.width) <= 1f)
            }
            assertEquals(bounds[1].left - bounds[0].right, bounds[2].left - bounds[1].right, 0.5f)
            compose.runOnIdle {
                val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(full))
                val row = Bitmap.createBitmap(
                    full, bounds.first().left.toInt(), bounds.first().top.toInt(),
                    bounds.last().right.toInt() - bounds.first().left.toInt(),
                    bounds.first().height.toInt()
                )
                val output = File("build/reports/annotation-filters").apply { mkdirs() }
                File(output, if (liquid) "glass.png" else "lumi.png").outputStream().use {
                    row.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                row.recycle()
                full.recycle()
            }
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
