package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h740dp-mdpi")
class FullscreenTagOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pageOwnedSheetCoversNavigationAndHeaderAndKeepsConfirmClickable() {
        var show by mutableStateOf(true)
        var backgroundClicks = 0
        var saves = 0
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                FullscreenOverlayHost {
                    Box(Modifier.fillMaxSize()) {
                        if (show) AnnotationTagSheet(emptyList(), listOf("Alpha"),
                            onSave = { saves++ }, onDismiss = { show = false })
                        Text("Header", Modifier.align(Alignment.TopStart).padding(top = 64.dp).size(100.dp, 48.dp)
                            .clickable { backgroundClicks++ })
                        Text("Navigation", Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp)
                            .clickable { backgroundClicks++ })
                    }
                }
            }
        }
        val context = RuntimeEnvironment.getApplication()
        compose.onNodeWithText(context.getString(R.string.confirm)).assertIsDisplayed().performClick()
        compose.waitUntil { !show }
        compose.runOnIdle { assertEquals(1, saves); assertEquals(0, backgroundClicks) }
        compose.waitForIdle()
        compose.runOnIdle { show = true }
        compose.onNodeWithText(context.getString(R.string.confirm)).assertIsDisplayed()
        compose.onNodeWithText("Header").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, backgroundClicks) }
        if (show) compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.waitUntil { !show }
        compose.onNodeWithText("Header").performClick()
        compose.runOnIdle { assertTrue(backgroundClicks == 1) }
    }
}
