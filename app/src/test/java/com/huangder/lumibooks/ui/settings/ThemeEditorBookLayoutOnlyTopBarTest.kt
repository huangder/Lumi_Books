package com.huangder.lumibooks.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h240dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeEditorBookLayoutOnlyTopBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun publisherSuiteHidesReaderLayoutOption() {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                Box(Modifier.size(360.dp, 180.dp)) {
                    ThemeEditorTopBar(
                        layout = ReaderLayoutTarget.BOOK_LAYOUT,
                        onLayoutChange = {},
                        onExit = {},
                        showReaderLayout = false
                    )
                }
            }
        }
        compose.onNodeWithTag("theme_editor_back").assertIsDisplayed()
        compose.onNodeWithTag("theme_editor_book_layout").assertIsDisplayed()
        compose.onNodeWithTag("theme_editor_reader_layout").assertDoesNotExist()
    }
}
