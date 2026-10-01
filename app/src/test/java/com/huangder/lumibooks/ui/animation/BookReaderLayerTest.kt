package com.huangder.lumibooks.ui.animation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.huangder.lumibooks.ui.theme.AppShapes
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.toAppPath
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

    @Test fun `moving tab occlusion matches the scaled cover outline in every theme`() {
        lateinit var view: View
        var density = 1f
        val theme = mutableStateOf("lumi")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            state = rememberBookReaderTransitionState()
            scope = rememberCoroutineScope()
            view = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalAppTheme provides theme.value) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Box(Modifier.fillMaxSize().bookReaderWindowClip(state, outside = true).background(Color.Red))
                }
            }
        }
        compose.runOnIdle {
            state.registerCoverAnchor("cover", book.id, Rect(40f, 100f, 180f, 300f), 28f, null)
            scope.launch {
                state.startOpen(book, null, null, 28f)
                state.markReaderReady()
            }
        }
        compose.mainClock.advanceTimeBy(900)
        compose.runOnIdle { assertTrue(state.startClose()) }
        // Closing exercises the moving clip before it reaches the cover endpoint.
        for (elapsed in listOf(96L, 96L, 96L)) {
            compose.mainClock.advanceTimeBy(elapsed)
            for (name in listOf("lumi", "liquid_glass", "material3")) {
                compose.runOnIdle { theme.value = name }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    assertEquals(BookReaderTransitionPhase.Closing, state.phase)
                    val source = state.sourceBounds!!
                    val target = Rect(0f, 0f, view.width.toFloat(), view.height.toFloat())
                    val bounds = BookReaderMotion.windowBounds(source, target,
                        state.positionSnapshot.value, state.sizeSnapshot.value,
                        BookReaderMotion.CONTROL_POINT_OFFSET_DP * density)
                    val actual = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(actual))
                    val expected = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(expected)
                    canvas.drawColor(android.graphics.Color.RED)
                    // The cover shell clips at its original size, then its layer scales.
                    canvas.translate(bounds.left, bounds.top)
                    canvas.scale(bounds.width / source.width, bounds.height / source.height)
                    val path = AppShapes.rounded(state.cornerRadiusSnapshot.value.dp,
                        AppShapes.usesContinuousCorners(name))
                        .createOutline(source.size, LayoutDirection.Ltr, Density(density)).toAppPath()
                    canvas.drawPath(path.asAndroidPath(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                    })
                    var difference = 0L
                    for (y in 0 until actual.height) for (x in 0 until actual.width) {
                        difference += kotlin.math.abs(android.graphics.Color.green(actual.getPixel(x, y)) -
                            android.graphics.Color.green(expected.getPixel(x, y)))
                    }
                    assertTrue("$name moving clip differs from cover by ${difference / 255f} pixels",
                        difference / 255f < 8f + (bounds.width + bounds.height) * 0.04f)
                    actual.recycle(); expected.recycle()
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
    }

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
