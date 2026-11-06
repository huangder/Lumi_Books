package com.huangder.lumibooks.ui.statistics

import android.app.Application
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.bookshare.BookShareActivity
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w393dp-h852dp-mdpi")
class BookReadingArchiveScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun archiveShowsRetainedDatesAndCalendarCellsHaveMeasurableWidth() {
        val context = RuntimeEnvironment.getApplication()
        val model = BookArchiveViewModel(ReadingInsightsFixtures.repository)
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                LazyColumn { item { BookReadingArchive("alpha", model) } }
            }
        }
        compose.waitUntil(10_000) { model.state.value.daily.isNotEmpty() }
        val first = model.state.value.daily.keys.minOrNull()!!
        val last = model.state.value.daily.keys.maxOrNull()!!
        compose.onNodeWithText(context.getString(R.string.insights_first_read_label)).assertExists()
        compose.onAllNodesWithText(shortDate(first)).onFirst().assertExists()
        compose.onNodeWithContentDescription(last.toString()).performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText(context.getString(R.string.insights_day_list)).performScrollTo().performClick()
        compose.onNodeWithText(dayTitle(last)).performScrollTo().assertIsDisplayed()
    }

    @Test fun dayDetailsLaunchCheckInWithTheSelectedBookAndDate() {
        val context = RuntimeEnvironment.getApplication()
        val today = ReadingInsightsFixtures.today
        val model = ReadingDayViewModel(ReadingInsightsFixtures.repository, ReadingInsightsFixtures.library)
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                ReadingDaySheet(today, model = model, onDismiss = {})
            }
        }
        compose.waitUntil(10_000) { model.entries.value.isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.insights_check_in)).performClick()
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(BookShareActivity::class.java.name, intent.component!!.className)
        assertEquals("beta", intent.getStringExtra("bookId"))
        assertEquals(today.toString(), intent.getStringExtra("date"))
    }
}
