package com.huangder.lumibooks.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h740dp-mdpi")
class CollapsingSettingsListTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeTitleAndLazyRowsHaveSeparateVerticalSpaceAtLargeFont() {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                    CollapsingSettingsScaffold("Tag management", {}, modifier = Modifier.testTag("page"),
                        lazyListState = rememberLazyListState(), lazyContent = {
                            items((1..500).toList()) { index ->
                                Text("Row $index", Modifier.fillMaxWidth().heightIn(min = 64.dp))
                            }
                        })
                }
            }
        }
        val titleBottom = compose.onAllNodesWithText("Tag management").fetchSemanticsNodes()
            .maxOf { it.boundsInRoot.bottom }
        val firstTop = compose.onNodeWithText("Row 1").getUnclippedBoundsInRoot().top.value
        assertTrue("Rows must start below the large title", firstTop >= titleBottom)
        compose.onNodeWithText("Row 500").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(500)
        compose.onNodeWithText("Row 500").assertIsDisplayed()
    }
}
