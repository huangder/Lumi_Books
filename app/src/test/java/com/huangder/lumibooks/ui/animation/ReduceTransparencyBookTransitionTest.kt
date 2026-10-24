package com.huangder.lumibooks.ui.animation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReduceTransparencyBookTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun togglingThePreferenceClearsBlurWithoutChangingLibraryTransformsOrClosing() {
        val previousInspectorSetting = isDebugInspectorInfoEnabled
        isDebugInspectorInfoEnabled = true
        try {
            val reduced = mutableStateOf(false)
            lateinit var transition: BookReaderTransitionState
            lateinit var scope: CoroutineScope
            var libraryModifier: Modifier = Modifier
            var appliedReduction = false
            compose.mainClock.autoAdvance = false
            compose.setContent {
                transition = rememberBookReaderTransitionState()
                scope = rememberCoroutineScope()
                CompositionLocalProvider(LocalReduceTransparency provides reduced.value) {
                    appliedReduction = LocalReduceTransparency.current
                    libraryModifier = Modifier.bookReaderLibraryLayer(transition, blurEnabled = true)
                    Box(Modifier.size(100.dp).then(libraryModifier))
                }
            }
            compose.runOnIdle {
                val book = Book("test", "Title", "Author", "book.txt", null, BookFormat.TXT, 0, 0f, createdAt = 0)
                transition.registerCoverAnchor("cover", book.id, Rect(10f, 20f, 70f, 100f), 8f, null)
                scope.launch {
                    transition.startOpen(book, null, null, 8f)
                    transition.markReaderReady()
                }
            }
            compose.mainClock.advanceTimeBy(900)
            // Let snapshot notifications and recomposition settle while the reader is stationary.
            compose.mainClock.autoAdvance = true
            lateinit var original: GraphicsLayerScope
            compose.runOnIdle {
                assertEquals(BookReaderTransitionPhase.Reader, transition.phase)
                original = evaluateLayer(libraryModifier)
                assertNotNull(original.renderEffect)
                assertTrue(original.scaleX < 1f)
                assertTrue(original.alpha < 1f)
                reduced.value = true
            }
            compose.runOnIdle {
                assertTrue(appliedReduction)
                val reducedLayer = evaluateLayer(libraryModifier)
                assertNull(reducedLayer.renderEffect)
                assertEquals(original.scaleX, reducedLayer.scaleX, 0.0001f)
                assertEquals(original.alpha, reducedLayer.alpha, 0.0001f)
                reduced.value = false
            }
            compose.runOnIdle {
                assertNotNull(evaluateLayer(libraryModifier).renderEffect)
                reduced.value = true
            }
            compose.runOnIdle { assertNull(evaluateLayer(libraryModifier).renderEffect) }
            compose.mainClock.autoAdvance = false
            compose.runOnIdle {
                assertTrue(transition.startClose())
            }
            compose.mainClock.advanceTimeBy(96)
            compose.runOnIdle { assertNull(evaluateLayer(libraryModifier).renderEffect) }
            compose.mainClock.advanceTimeBy(BookReaderMotion.CONTENT_EXIT_MS.toLong())
            compose.runOnIdle {
                assertTrue(transition.consumeCloseNavigation())
                transition.completeCloseAfterNavigation()
            }
            compose.mainClock.advanceTimeBy(900)
            compose.runOnIdle { assertEquals(BookReaderTransitionPhase.Library, transition.phase) }
        } finally {
            isDebugInspectorInfoEnabled = previousInspectorSetting
        }
    }

    private fun evaluateLayer(modifier: Modifier): GraphicsLayerScope {
        var layerBlock: (GraphicsLayerScope.() -> Unit)? = null
        modifier.foldIn(Unit) { _, element ->
            val block = (element as? InspectableValue)?.inspectableElements
                ?.firstOrNull { it.name == "block" }?.value
            if (block != null) {
                @Suppress("UNCHECKED_CAST")
                layerBlock = block as GraphicsLayerScope.() -> Unit
            }
        }
        return GraphicsLayerScope().apply { checkNotNull(layerBlock).invoke(this) }
    }
}
