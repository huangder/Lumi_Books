package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PdfInkAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun centeredShortCoverCommitsInPageCoordinates() = checkAlignment(false)

    @Test fun zoomedCroppedScrolledCoverCommitsInPageCoordinates() = checkAlignment(true)

    private fun checkAlignment(transformed: Boolean) {
        val layouts = mutableStateMapOf<Int, LayoutCoordinates>()
        val crop = if (transformed) RasterHorizontalCrop(.15f, .85f) else RasterHorizontalCrop.FULL
        val crops = mutableStateMapOf(0 to crop)
        var strokes by mutableStateOf<List<PdfInkStroke>>(emptyList())
        compose.setContent {
            val list = rememberLazyListState(initialFirstVisibleItemScrollOffset = if (transformed) 60 else 0)
            val scale = if (transformed) 1.25f else 1f
            CompositionLocalProvider(LocalRasterInkPages provides layouts) {
                Box(Modifier.size(240.dp, 360.dp).testTag("document").graphicsLayer {
                    scaleX = scale; scaleY = scale
                }) {
                    LazyColumn(state = list, userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
                        item {
                            Box(Modifier.fillMaxWidth().height(if (transformed) 500.dp else 360.dp),
                                contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxWidth().height(100.dp)) {
                                    PdfPageInkCanvas(0, crop, 2.4f / crop.width, false,
                                        PdfInkTool.PEN, "#FF0000", strokes, false,
                                        { strokes = strokes + it }, {})
                                }
                            }
                        }
                    }
                    PdfDocumentInkCanvas(1, list, crops, transformed, PdfInkTool.PEN, "#FF0000",
                        strokes, { strokes = strokes + it }, {})
                }
            }
        }
        compose.waitForIdle()
        val bounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        var start = Offset.Zero
        var end = Offset.Zero
        compose.runOnIdle {
            val page = layouts.getValue(0)
            start = page.localToRoot(Offset(page.size.width * .25f, page.size.height * .25f)) - bounds.topLeft
            end = page.localToRoot(Offset(page.size.width * .75f, page.size.height * .3f)) - bounds.topLeft
        }
        compose.onRoot().performTouchInput {
            down(start)
            moveTo(end, delayMillis = 100)
            up()
        }
        compose.runOnIdle {
            val saved = strokes.single()
            assertEquals(crop.left + crop.width * .25f, saved.points.first().x, .002f)
            assertEquals(.25f, saved.points.first().y, .002f)
            assertEquals(.3f, saved.points.last().y, .002f)
            // Reopening uses the same persisted points and page-canvas transform.
            val restored = PdfInkStrokeLocatorV1.decode(PdfInkStrokeLocatorV1.encode(saved))!!
            assertEquals(saved.points, restored.points)
        }
    }

    @Test fun liveAndSavedGeometryMatchWithLetterboxingCropAndScroll() {
        val crop = RasterHorizontalCrop(.2f, .9f)
        for (top in listOf(160f, -35f)) {
            val geometry = RasterDocumentInkPage(Offset(12f, top), 280f, 120f, crop)
            val touch = Offset(82f, top + 30f)
            val stored = geometry.toOriginal(touch)
            assertEquals(.25f, stored.y, .0001f)
            assertEquals(touch, geometry.toDisplay(stored))
            val savedDisplay = RasterInkTransform(crop, 2f, 280f, 120f, false).toDisplay(stored)
            assertEquals(touch.x, geometry.origin.x + savedDisplay.x * geometry.width, .0001f)
            assertEquals(touch.y, geometry.origin.y + savedDisplay.y * geometry.height, .0001f)
        }
    }
}
