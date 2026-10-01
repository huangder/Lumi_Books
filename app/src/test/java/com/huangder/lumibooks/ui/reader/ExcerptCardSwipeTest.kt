package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExcerptCardSwipeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun highlightCardRevealsExcerptAndDelete() = verify("highlight")
    @Test fun underlineCardRevealsExcerptAndDelete() = verify("underline")
    @Test fun noteCardRevealsExcerptAndDelete() = verify("note")

    private fun verify(type: String) {
        val context = RuntimeEnvironment.getApplication()
        var shares = 0
        var deletes = 0
        var navigations = 0
        val note = Note(bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 8,
            selectedText = "The selected passage", note = if (type == "note") "A note" else "",
            color = "#FFEB3B", createdAt = 0, type = type)
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi") {
                Box(Modifier.width(320.dp)) {
                    HighlightNoteItem(note, onClick = { navigations++ }, onShare = { shares++ },
                        onDelete = { deletes++ }, onEditTags = {}, modifier = Modifier.testTag("card"))
                }
            }
        }
        compose.onNodeWithTag("card").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.delete)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.annotation_tags)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.excerpt_share))
            .assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, shares)
            assertEquals(0, deletes)
            assertEquals(0, navigations)
        }
    }
}
