package com.huangder.lumibooks.ui.statistics

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.*
import java.io.File
import org.junit.Rule
import org.junit.Test
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
    @Test fun overviewAndDateDetailsWorkAcrossThemesAndLargeText() {
        val context=RuntimeEnvironment.getApplication()
        val model=StatisticsViewModel(ReadingInsightsFixtures.repository,ReadingInsightsFixtures.library,DataStoreManager(context))
        var theme by mutableStateOf("lumi")
        var dark by mutableStateOf(false)
        var eInk by mutableStateOf(false)
        var large by mutableStateOf(false)
        lateinit var view:View
        compose.setContent {
            view=LocalView.current
            EBookReaderTheme(appTheme=theme,darkTheme=dark,eInkMode=eInk,appFontSize=if(large)"large" else "normal",motionPreference=MotionPreference.REDUCED) {
                StatisticsScreen(viewModel=model)
            }
        }
        compose.waitUntil(10_000) { model.uiState.value.insights!=null }
        compose.onNodeWithText(context.getString(R.string.insights_all)).assertExists()
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
        compose.onNodeWithContentDescription(ReadingInsightsFixtures.today.toString()).performClick()
        compose.onNodeWithText(context.getString(R.string.insights_check_in)).assertExists()
    }
}
