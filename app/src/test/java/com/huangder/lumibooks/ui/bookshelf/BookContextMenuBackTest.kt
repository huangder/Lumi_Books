package com.huangder.lumibooks.ui.bookshelf

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.LocalPredictiveBackEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BookContextMenuBackTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun backDismissesContextMenuBeforeBackgroundNavigation() = verifyBackPriority(
        predictiveBackEnabled = false
    )

    @Test
    fun predictiveBackDismissesContextMenuBeforeBackgroundNavigation() = verifyBackPriority(
        predictiveBackEnabled = true
    )

    private fun verifyBackPriority(predictiveBackEnabled: Boolean) {
        val book = Book(
            id = "book",
            title = "Book",
            author = "Author",
            filePath = "book.txt",
            coverPath = null,
            format = BookFormat.TXT,
            lastReadTime = 0,
            readingProgress = 0f,
            createdAt = 0
        )
        lateinit var menuState: BookContextMenuState
        lateinit var backDispatcher: OnBackPressedDispatcher
        var backgroundBackCount = 0

        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            menuState = remember(scope) { BookContextMenuState(scope) }
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!
                .onBackPressedDispatcher
            CompositionLocalProvider(LocalPredictiveBackEnabled provides predictiveBackEnabled) {
                ConfigurableBackHandler { backgroundBackCount++ }
                BookContextMenuOverlay(state = menuState)
            }
        }

        compose.runOnIdle {
            menuState.onLongPressConfirmed(
                book = book,
                bounds = Rect(40f, 80f, 200f, 320f),
                onHaptic = {}
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals(ContextMenuPhase.Visible, menuState.phase)
            backDispatcher.onBackPressed()
            assertEquals(ContextMenuPhase.Dismissing, menuState.phase)
            assertEquals(0, backgroundBackCount)
        }

        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals(ContextMenuPhase.Idle, menuState.phase)
            backDispatcher.onBackPressed()
            assertEquals(1, backgroundBackCount)
        }
    }
}
