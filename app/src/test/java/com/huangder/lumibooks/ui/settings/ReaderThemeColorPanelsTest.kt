package com.huangder.lumibooks.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w320dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderThemeColorPanelsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun backgroundPaletteKeepsReadingOrderAndCustomColorsAccessible() {
        val settings = mutableStateOf(ReaderThemeSettings())
        val custom = listOf(
            ReaderBackgroundPreset("first", ReaderBackgroundType.COLOR, "#EDCBA9", name = "First color"),
            ReaderBackgroundPreset("second", ReaderBackgroundType.COLOR, "#ABCDEF", name = "Second color")
        )
        var view: View? = null
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                Box(Modifier.width(320.dp).height(360.dp).background(Color.White)) {
                    BackgroundPanel(settings.value, custom, false, false, false,
                        onThemeModeChange = {}, onPreviewUpdate = {}, onUpdate = {},
                        onSelectColor = { settings.value = settings.value.copy(backgroundColorSelection = it) },
                        onAddColor = {}, onAddPhoto = {}, onRemovePhoto = {})
                }
            }
        }
        val labels = listOf(R.string.theme_day, R.string.theme_night, R.string.theme_sepia, R.string.theme_green)
        val positions = labels.map {
            compose.onNodeWithContentDescription(context.getString(it)).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot.left
        }
        assertEquals(positions.sorted(), positions)
        save(view!!, "background-builtins")
        compose.onNodeWithContentDescription("Second color").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(custom[1].selectionKey, settings.value.backgroundColorSelection) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        compose.onNodeWithContentDescription(context.getString(R.string.background_custom_color))
            .assertIsDisplayed().performClick()
        compose.onNodeWithText(context.getString(R.string.background_custom_color_title)).assertIsDisplayed()
    }

    @Test fun publisherLayoutKeepsTextPaletteLocked() {
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                TextPanel(ReaderThemeSettings(), emptyList(), emptyList(), true, false,
                    onThemeModeChange = {}, lockTextColor = true,
                    onPreviewUpdate = {}, onUpdate = {})
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.text_color_custom)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.reader_theme_publisher_hint)).assertIsDisplayed()
    }

    private fun save(view: View, name: String) = compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/reports/reader-colors").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
