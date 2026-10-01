package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.theme.*
import java.io.File
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
class ReaderAnnotationPanelTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private lateinit var view: View
    private val glass = mutableStateOf(false)
    private val entries = listOf(
        mark(1, "Highlight Alpha", listOf("Research", "Long reading tag for wrapping")),
        mark(2, "Highlight Beta", emptyList()),
        mark(3, "Note Gamma", listOf("Research"), type = "note"),
        mark(4, "Underline Delta", listOf("Research"), type = "underline"),
        mark(5, "Underline Epsilon", emptyList(), type = "underline", mode = 4)
    )
    private fun mark(id: Long, quote: String, tags: List<String>, type: String = "highlight", mode: Int = 1) = Note(
        id = id, bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 10,
        selectedText = quote, note = if (type == "note") "Existing remark" else "", color = "#FFEB3B",
        createdAt = 1, type = type, syncId = "mark-$id", tags = tags,
        styleSnapshotJson = "{\"underlineMode\":$mode}"
    )

    private fun capture(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/annotation-ui/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun cardLabelsAndReaderFiltersWorkInBothThemes() {
        var edited: Note? = null
        var jumped = false
        compose.setContent {
            EBookReaderTheme(appTheme = if (glass.value) "liquid_glass" else "lumi", darkTheme = false) {
                CompositionLocalProvider(
                    LocalAppTheme provides if (glass.value) "liquid_glass" else "lumi",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                    LocalMotionEnabled provides false
                ) {
                    view = LocalView.current
                    NotesListSheet(true, notes = entries, onNoteClick = { jumped = true },
                        onShareNote = {}, onEditTags = { edited = it }, onDeleteNote = {}, onDismiss = {})
                }
            }
        }
        listOf(false, true).forEach { liquid ->
            compose.runOnIdle { glass.value = liquid }
            compose.onNodeWithText("Research").assertIsDisplayed()
            // Bitmap Canvas cannot execute Android's glass RuntimeShader. Exercise the
            // glass hierarchy and interactions below; capture the software-safe theme.
            if (!liquid) capture("reader-lumi")
            compose.onNodeWithText("Highlight Alpha").performTouchInput { swipeLeft() }
            compose.onNode(hasContentDescription(label(R.string.annotation_tags)) and isEnabled()).performClick()
            compose.runOnIdle { assertEquals(1L, edited?.id); assertFalse(jumped) }
            compose.onNodeWithText(label(R.string.annotation_tag_all)).performClick()
            if (!liquid) capture("reader-lumi-filter")
            compose.onNodeWithText(label(R.string.annotation_tag_none)).performClick()
            compose.onNodeWithText("Highlight Alpha").assertDoesNotExist()
            compose.onNodeWithText("Highlight Beta").assertIsDisplayed()
            compose.onNodeWithText(label(R.string.annotation_tag_none)).performClick()
            compose.onNodeWithText(label(R.string.annotation_tag_all)).assertIsEnabled().performClick()
            compose.onNodeWithText("Highlight Alpha").assertIsDisplayed()
        }
        compose.onNodeWithText(label(R.string.tab_note)).performClick()
        compose.onNodeWithText("Research").assertIsDisplayed()
        compose.onNodeWithText("Existing remark").assertIsDisplayed()
        compose.onNodeWithText("Note Gamma").performTouchInput { swipeLeft() }
        compose.onNode(hasContentDescription(label(R.string.annotation_tags)) and isEnabled()).performClick()
        compose.runOnIdle { assertEquals(3L, edited?.id) }
        compose.onNodeWithText(label(R.string.tab_underline)).performClick()
        compose.onNodeWithText(label(R.string.annotation_tag_line)).performClick()
        compose.onNodeWithText(label(R.string.highlight_rule_underline_dashed)).performClick()
        compose.onNodeWithText("Underline Delta").assertDoesNotExist()
        compose.onNodeWithText("Underline Epsilon").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.annotation_tag_color)).performClick()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.annotation_tag_color_number, 1))
            .performClick()
        compose.onNodeWithText("Underline Epsilon").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w640dp-h320dp-land-mdpi")
    fun landscapeLargeFontKeepsFiltersAndTagsReachable() {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f), LocalMotionEnabled provides false) {
                    view = LocalView.current
                    NotesListSheet(true, notes = entries, onNoteClick = {}, onShareNote = {}, onEditTags = {}, onDeleteNote = {}, onDismiss = {})
                }
            }
        }
        compose.onNodeWithContentDescription(label(R.string.reader_close)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.annotation_tag_all)).assertIsDisplayed().performClick()
        compose.onNodeWithText(label(R.string.annotation_tag_none)).performClick()
        compose.onNodeWithText("Highlight Beta").assertIsDisplayed()
        capture("reader-landscape-dark")
    }
}
