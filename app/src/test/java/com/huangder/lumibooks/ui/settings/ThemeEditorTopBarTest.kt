package com.huangder.lumibooks.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h240dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeEditorTopBarTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test fun toolbarUsesOneFullWidthRowWithEqualSelectorsAtBothFontScales() {
        val fontScale = mutableStateOf(1f)
        val layout = mutableStateOf(ReaderLayoutTarget.READER_LAYOUT)
        var exits = 0
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value)) {
                    view = LocalView.current
                    Box(Modifier.size(360.dp, 180.dp).background(Color(0xFFFBFBFC))) {
                        ThemeEditorTopBar(layout.value, { layout.value = it }, { exits++ })
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { fontScale.value = scale }
            val back = compose.onNodeWithTag("theme_editor_back").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val reader = compose.onNodeWithTag("theme_editor_reader_layout").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val book = compose.onNodeWithTag("theme_editor_book_layout").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("Back-to-reader gap", 8f, reader.left - back.right, 0.5f)
            assertEquals("Selector gap", 8f, book.left - reader.right, 0.5f)
            assertEquals("Equal selector widths", reader.width, book.width, 0.5f)
            assertEquals("Left edge", 8f, back.left, 0.5f)
            assertEquals("Right edge", 352f, book.right, 0.5f)
            assertEquals("Back and reader align", back.center.y, reader.center.y, 0.5f)
            assertEquals("Both selectors align", reader.center.y, book.center.y, 0.5f)
            val context = RuntimeEnvironment.getApplication()
            compose.onNodeWithText(context.getString(R.string.reader_layout_editor_reader)).assertExists()
            compose.onNodeWithText(context.getString(R.string.reader_layout_editor_book)).assertExists()
            compose.onNodeWithText(context.getString(R.string.reader_layout_editor_reader_hint)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.reader_layout_editor_book_hint)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.theme_suite_day_name)).assertDoesNotExist()
            saveScreenshot("font-${scale.toInt()}.png")
        }
        compose.onNodeWithTag("theme_editor_book_layout").performClick()
        compose.runOnIdle { assertEquals(ReaderLayoutTarget.BOOK_LAYOUT, layout.value) }
        compose.onNodeWithTag("theme_editor_back").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    private fun saveScreenshot(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val directory = File("build/theme-editor-topbar").apply { mkdirs() }
                File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
