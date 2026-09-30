package com.huangder.lumibooks.ui.animation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookReaderLayerTest {
    @get:Rule val compose = createComposeRule()
    private val book = Book("a", "A", "Author", "a.txt", null, BookFormat.TXT, 0, 0f, createdAt = 0)
    private lateinit var state: BookReaderTransitionState
    private lateinit var scope: CoroutineScope

    @Test fun `return uses full cover bounds inside the scaled library`() {
        val top = mutableFloatStateOf(140f)
        var density = 1f
        compose.mainClock.autoAdvance = false
        compose.setContent {
            state = rememberBookReaderTransitionState()
            scope = rememberCoroutineScope()
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalBookReaderAnchorScope provides BookReaderAnchorScope(state)) {
                BookReaderLibraryLayer(state, blurEnabled = false) {
                    Box(Modifier.offset(x = (-20).dp, y = top.floatValue.dp).size(100.dp, 150.dp)
                        .bookCoverTransitionAnchor(book.id, 8f).background(Color.Red))
                }
            }
        }
        compose.runOnIdle {
            scope.launch {
                assertTrue(state.startOpen(book, null, null, 8f))
                state.markReaderReady()
            }
        }
        compose.mainClock.advanceTimeBy(900)
        compose.runOnIdle {
            assertEquals(BookReaderTransitionPhase.Reader, state.phase)
            assertEquals(-20f * density, state.sourceBounds!!.left, 0.1f)
            assertEquals(100f * density, state.sourceBounds!!.width, 0.1f)
            top.floatValue = 190f
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue(state.startClose()) }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle {
            assertEquals(-20f * density, state.sourceBounds!!.left, 0.1f)
            assertEquals(190f * density, state.sourceBounds!!.top, 0.1f)
            assertEquals(100f * density, state.sourceBounds!!.width, 0.1f)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals(BookReaderTransitionPhase.Library, state.phase) }
    }

    @Test fun `retained tab layer stays invisible after the reader handoff`() {
        lateinit var view: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            state = rememberBookReaderTransitionState()
            scope = rememberCoroutineScope()
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.White)) {
                // Deliberately retain the bar beyond AnimatedVisibility's disposal.
                Box(Modifier.fillMaxSize().bookReaderWindowClip(state, outside = true).background(Color.Red))
            }
        }
        compose.runOnIdle {
            state.registerCoverAnchor("cover", book.id, Rect(20f, 30f, 120f, 180f), 8f, null)
            scope.launch {
                state.startOpen(book, null, null, 8f)
                state.markReaderReady()
            }
        }
        compose.mainClock.advanceTimeBy(900)
        compose.runOnIdle {
            assertEquals(BookReaderTransitionPhase.Reader, state.phase)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(view.width / 2, view.height / 2))
            bitmap.recycle()
        }
    }
}
