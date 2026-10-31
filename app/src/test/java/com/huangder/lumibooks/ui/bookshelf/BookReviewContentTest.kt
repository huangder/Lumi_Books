package com.huangder.lumibooks.ui.bookshelf

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookReviewContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longReviewEditorIsBoundedAndCanEditTheEndOfTheText() {
        val original = (1..60).joinToString("\n") { "书评第${it}段：这是用于验证长文字编辑和滚动的内容。" }
        val review = mutableStateOf(original)
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    ReviewEditor(review.value, { review.value = it }, editorHeight = 240.dp)
                }
            }
        }
        val editor = compose.onNode(hasSetTextAction())
        editor.assertHeightIsEqualTo(240.dp)
        editor.performTextInputSelection(androidx.compose.ui.text.TextRange(original.length))
        editor.performTextInput("末尾补充")
        compose.runOnIdle { assertEquals(original + "末尾补充", review.value) }
        editor.assertHeightIsEqualTo(240.dp)
    }

    @Test fun longReviewCardCanExpandAndCollapseWithoutLosingParagraphs() {
        val review = (1..50).joinToString("\n") { "第${it}段书评，保留全文和段落。" }
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                CompositionLocalProvider(LocalMotionEnabled provides true) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        ReviewEntryCard(review, 4f, {})
                    }
                }
            }
        }
        compose.onNodeWithText(reviewPreviewText(review)).performClick()
        compose.onNodeWithText(review).assertExists().performClick()
        compose.onNodeWithText(reviewPreviewText(review)).assertExists()
    }
}
