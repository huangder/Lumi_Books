package com.huangder.lumibooks.ui.navigation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.animation.PageEntranceItem
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h850dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainTabRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun homeCardsKeepTheirPixelsAfterTabClickAndSwipeBack() {
        lateinit var view: View
        lateinit var pager: PagerState
        val cardColor = mutableStateOf(Color.Red)
        compose.setContent {
            view = LocalView.current
            pager = rememberPagerState { mainTabRoutes.size }
            val selection = remember { MainTabSelection() }
            val scope = rememberCoroutineScope()
            Column(Modifier.fillMaxSize().background(Color.White)) {
                MainTabsPager(pager, true, Modifier.weight(1f).testTag("pager"), selection) { page ->
                    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
                        items(4) { index ->
                            PageEntranceItem(play = false, index = index) {
                                if (index == 2) {
                                    LazyRow(Modifier.fillMaxWidth().height(100.dp)) {
                                        item { Box(Modifier.fillParentMaxWidth().height(100.dp).background(Color.Blue)) }
                                    }
                                } else {
                                    Box(Modifier.fillMaxWidth().height(100.dp).background(
                                        if (page == 0) cardColor.value else Color.Green
                                    ))
                                }
                            }
                        }
                    }
                }
                Text("Library", Modifier.height(48.dp).testTag("library").clickable {
                    scope.launch { selection.select(pager, 1, true) }
                })
            }
        }
        fun assertHomePixels(color: Int) {
            compose.runOnIdle {
                assertEquals(0, pager.settledPage)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                try {
                    for (y in listOf(50, 150, 350)) {
                        assertEquals("Home card at y=$y must be drawn", color, bitmap.getPixel(200, y))
                    }
                    assertEquals(android.graphics.Color.BLUE, bitmap.getPixel(200, 250))
                } finally {
                    bitmap.recycle()
                }
            }
        }
        assertHomePixels(android.graphics.Color.RED)
        repeat(2) {
            compose.onNodeWithTag("library").performClick()
            compose.runOnIdle { cardColor.value = Color.Magenta }
            compose.onNodeWithTag("pager").performTouchInput { swipeRight() }
            assertHomePixels(android.graphics.Color.MAGENTA)
        }
    }
}
