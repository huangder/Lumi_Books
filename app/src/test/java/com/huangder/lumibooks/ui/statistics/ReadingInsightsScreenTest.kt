package com.huangder.lumibooks.ui.statistics

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.AppFontSize
import com.huangder.lumibooks.ui.theme.*
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
import com.kyant.backdrop.Backdrop
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="en-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingInsightsScreenTest {
    @get:Rule val compose=createComposeRule()

    @Test @Config(sdk=[31])
    fun glassStatisticsNeverSamplesTheNavigationLayerContainingIt() {
        val context = RuntimeEnvironment.getApplication()
        val model = StatisticsViewModel(ReadingInsightsFixtures.repository, ReadingInsightsFixtures.library, DataStoreManager(context))
        var parentSamples = 0
        val parent = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?) {
                parentSamples++
                drawRect(Color.Red)
            }
        }
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme="liquid_glass", motionPreference=MotionPreference.REDUCED) {
                CompositionLocalProvider(
                    LocalAppTheme provides "liquid_glass",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                    LocalLiquidGlassBackdrop provides parent
                ) { StatisticsScreen(viewModel=model) }
            }
        }
        compose.waitUntil(10_000) { model.uiState.value.insights != null }
        for (scope in listOf(R.string.insights_all, R.string.tab_week, R.string.tab_month, R.string.tab_year)) {
            compose.onNodeWithText(context.getString(scope)).performClick().assertIsSelected()
            compose.waitForIdle()
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                bitmap.recycle()
            }
        }
        compose.runOnIdle { assertEquals("Controls must sample a sibling, never the captured navigation layer", 0, parentSamples) }
    }

    // RuntimeShader highlights require a hardware canvas on API 33+; use the
    // supported blur path for software screenshots and verify refraction on-device.
    @Test @Config(sdk=[31]) fun overviewWorksAcrossThemesAndLargeText() {
        val context=RuntimeEnvironment.getApplication()
        val model=StatisticsViewModel(ReadingInsightsFixtures.repository,ReadingInsightsFixtures.library,DataStoreManager(context))
        var theme by mutableStateOf("lumi")
        var dark by mutableStateOf(false)
        var eInk by mutableStateOf(false)
        var large by mutableStateOf(false)
        lateinit var view:View
        compose.setContent {
            view=LocalView.current
            EBookReaderTheme(appTheme=theme,darkTheme=dark,eInkMode=eInk,appFontSize=if(large) AppFontSize.EXTRA_LARGE else AppFontSize.STANDARD,motionPreference=MotionPreference.REDUCED) {
                StatisticsScreen(viewModel=model)
            }
        }
        compose.waitUntil(10_000) { model.uiState.value.insights!=null }
        compose.onNodeWithText(context.getString(R.string.insights_all)).assertIsSelected()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(4)
        val output=File("build/reading-insights-qa").apply { mkdirs() }
        for(name in listOf("lumi","material3","liquid_glass","dark","eink","large")) {
            compose.runOnIdle {
                theme=if(name in listOf("lumi","material3","liquid_glass"))name else "lumi"
                dark=name=="dark";eInk=name=="eink";large=name=="large"
            }
            compose.waitForIdle()
            compose.runOnIdle {
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(output,"statistics-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        }
        compose.runOnIdle { large=false }
        compose.onNodeWithContentDescription(ReadingInsightsFixtures.today.toString()).assertHasClickAction()
        compose.onNodeWithText(context.getString(R.string.tab_month)).performClick()
        compose.onNodeWithText(context.getString(R.string.tab_month)).assertIsSelected()
        compose.waitUntil(10_000) { model.uiState.value.selection.scope == StatisticsScope.MONTH && model.uiState.value.insights != null }
        compose.onNodeWithText(context.getString(R.string.insights_overview)).assertExists()
    }

    @Test
    @Config(qualifiers="en-w960dp-h480dp-mdpi")
    fun landscapeStatisticsCanNavigateToAnHistoricalPeriod() {
        val context = RuntimeEnvironment.getApplication()
        val model = StatisticsViewModel(ReadingInsightsFixtures.repository, ReadingInsightsFixtures.library, DataStoreManager(context))
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme="lumi", motionPreference=MotionPreference.REDUCED) {
                StatisticsScreen(viewModel=model)
            }
        }
        compose.waitUntil(10_000) { model.uiState.value.insights != null }
        compose.onNodeWithText(context.getString(R.string.tab_month)).performClick()
        compose.waitUntil(10_000) { model.uiState.value.selection.scope == StatisticsScope.MONTH && model.uiState.value.insights != null }
        compose.onNodeWithContentDescription(context.getString(R.string.insights_previous_period)).performClick()
        val previous = ReadingInsightsFixtures.today.minusMonths(1)
        compose.waitUntil(10_000) { model.uiState.value.selection.year == previous.year &&
            model.uiState.value.selection.month0 == previous.monthValue - 1 && model.uiState.value.insights != null }
        compose.onNodeWithText(periodLabel(StatisticsPeriod.month(previous.year, previous.monthValue - 1))).assertIsDisplayed()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reading-insights-qa").apply { mkdirs() }
            File(output,"statistics-landscape.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
}
