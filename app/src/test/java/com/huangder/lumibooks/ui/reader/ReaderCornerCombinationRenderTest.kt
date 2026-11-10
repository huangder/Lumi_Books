package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderCornerCombinationRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chapterSuffixStaysVisibleInEveryCornerAtLargeFontScale() {
        val title = "A very long chapter title that must give the suffix enough room to remain visible"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Column(Modifier.width(320.dp)) {
                    repeat(2) { row ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(2) { column ->
                                Box(Modifier.weight(1f).testTag("corner-$row-$column")) {
                                    ReaderCornerContentValue(
                                        content = ReaderCornerContent.CHAPTER_INFO_PAGE_NUMBER,
                                        chapterTitle = title, bookProgressPercent = 12.5f,
                                        globalPageLabel = "42 / 200", currentPage = 3,
                                        chapterPageCount = 12, contentColor = Color.Black,
                                        alignEnd = column == 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onAllNodesWithText("3 / 12").assertCountEquals(4)
        compose.onAllNodesWithText("3 / 12").fetchSemanticsNodes().forEach {
            val parent = it.parent!!.boundsInRoot
            assertTrue(it.boundsInRoot.left >= parent.left)
            assertTrue(it.boundsInRoot.right <= parent.right)
            assertTrue(it.boundsInRoot.width > 0f)
        }
        compose.onAllNodesWithText(title).fetchSemanticsNodes().forEach {
            assertTrue(it.boundsInRoot.width > 0f)
        }
        repeat(4) { index ->
            val titleNode = compose.onAllNodesWithText(title)[index]
            val suffixNode = compose.onAllNodesWithText("3 / 12")[index]
            assertTrue(titleNode.fetchSemanticsNode().boundsInRoot.right <=
                suffixNode.fetchSemanticsNode().boundsInRoot.left)
            val layouts = mutableListOf<TextLayoutResult>()
            titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertTrue(layouts.single().isLineEllipsized(0))
        }
    }

    @Test fun numericCombinationsFollowPageAndProgressUpdates() {
        val page = mutableStateOf(3)
        val progress = mutableStateOf(12.5f)
        compose.setContent {
            Column(Modifier.width(320.dp)) {
                ReaderCornerContentValue(ReaderCornerContent.PAGE_NUMBER_BOOK_PROGRESS,
                    "Chapter", progress.value, "42 / 200", currentPage = page.value,
                    chapterPageCount = 12, rightPageIndex = page.value,
                    contentColor = Color.Black, alignEnd = false)
                ReaderCornerContentValue(ReaderCornerContent.GLOBAL_PAGE_NUMBER_BOOK_PROGRESS,
                    "Chapter", progress.value, "42 / 200", currentPage = page.value,
                    chapterPageCount = 12, contentColor = Color.Black, alignEnd = true)
                ReaderCornerContentValue(ReaderCornerContent.CHAPTER_INFO_BOOK_PROGRESS,
                    "Chapter", progress.value, "42 / 200", currentPage = page.value,
                    chapterPageCount = 12, contentColor = Color.Black, alignEnd = true)
                ReaderCornerContentValue(ReaderCornerContent.PAGE_NUMBER_BOOK_PROGRESS,
                    "Chapter", progress.value, "… / …", currentPage = 0,
                    chapterPageCount = 0, contentColor = Color.Black, alignEnd = true)
            }
        }
        compose.onNodeWithText("3–4 / 12 · 12.50%").assertIsDisplayed()
        compose.onNodeWithText("42 / 200 · 12.50%").assertIsDisplayed()
        compose.onNodeWithText("… / … · 12.50%").assertIsDisplayed()
        compose.runOnIdle { page.value = 5; progress.value = 50f }
        compose.onNodeWithText("5–6 / 12 · 50.00%").assertIsDisplayed()
        compose.onNodeWithText("42 / 200 · 50.00%").assertIsDisplayed()
        compose.onNodeWithText("50.00%").assertIsDisplayed()
    }

    @Test fun timeBatteryCombinationUsesTheExistingClockAndBattery() {
        compose.setContent {
            Box(Modifier.width(160.dp)) {
                ReaderCornerContentValue(ReaderCornerContent.TIME_BATTERY,
                    "", 0f, "", currentPage = 1, chapterPageCount = 1,
                    contentColor = Color.Black, alignEnd = true)
            }
        }
        compose.onNodeWithText(" · ").assertIsDisplayed()
        compose.onAllNodes(hasText("%", substring = true), useUnmergedTree = true).assertCountEquals(1)
        compose.onNode(hasText(":", substring = true)).assertIsDisplayed()
    }

    @Test fun clockRespectsSystem12And24HourPreference() {
        val context = RuntimeEnvironment.getApplication()
        val timestamp = Calendar.getInstance().apply { set(2026, 10, 10, 13, 5, 0) }.timeInMillis
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        assertEquals("13:05", formatReaderClock(context, timestamp))
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertEquals("01:05 PM", formatReaderClock(context, timestamp))
    }
}
