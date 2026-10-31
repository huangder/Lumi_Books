package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LongPressSelectionRange
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.SelectionMenuAction
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PdfTextSelectionTest {
    @get:Rule val compose = createComposeRule()

    private fun controller(): PdfTextAnnotationController {
        val context = RuntimeEnvironment.getApplication()
        PDFBoxResourceLoader.init(context)
        val file = File.createTempFile("pdf-selection", ".pdf", context.cacheDir)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(300f, 400f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 16f)
                stream.newLineAtOffset(30f, 300f)
                stream.showText("Hello world")
                stream.newLineAtOffset(0f, -30f)
                stream.showText("Second line")
                stream.endText()
            }
            document.save(file)
        }
        return PdfTextAnnotationController(PdfTextSession(context, file.path))
    }

    @Test fun longPressSelectsWordAndDraggingHandleExtendsAcrossLines() {
        val controller = controller()
        val model = runBlocking { controller.session.page(0)!! }
        controller.range = LongPressSelectionRange.WORD
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPdfTextAnnotations provides controller) {
                    Box(Modifier.size(300.dp, 400.dp).pointerInput(Unit) { detectTapGestures { } }) {
                        PdfPageTextLayer(0, true, RasterHorizontalCrop.FULL, 0.75f, true)
                    }
                }
            }
        }
        compose.waitForIdle()
        val glyph = model.glyphs.first()
        compose.onNodeWithTag("pdf-text-page-0").performTouchInput {
            longClick(Offset((glyph.quad.left + glyph.quad.right) / 2f * width,
                (glyph.quad.top + glyph.quad.bottom) / 2f * height))
        }
        compose.waitUntil(5000) { controller.selection != null }
        compose.runOnIdle { assertEquals("Hello", controller.selection?.text) }
        val pageBounds = compose.onNodeWithTag("pdf-text-page-0").fetchSemanticsNode().boundsInRoot
        val endBounds = compose.onNodeWithTag("pdf-selection-end").fetchSemanticsNode().boundsInRoot
        val targetGlyph = model.glyphs.last()
        val target = Offset(pageBounds.left + (targetGlyph.quad.left + targetGlyph.quad.right) / 2f * pageBounds.width,
            pageBounds.top + (targetGlyph.quad.top + targetGlyph.quad.bottom) / 2f * pageBounds.height)
        compose.onNodeWithTag("pdf-selection-end").performTouchInput {
            down(center)
            moveTo(center + target - endBounds.center, delayMillis = 100)
            up()
        }
        compose.runOnIdle {
            assertEquals(model.text, controller.selection?.text)
            assertFalse(controller.dragging)
            assertEquals(2, controller.selection!!.locator!!.quads.size)
        }
        controller.session.close()
    }

    @Test fun annotationTapOpensTheSavedSelectionAndInkModePassesThrough() {
        val controller = controller()
        val model = runBlocking { controller.session.page(0)!! }
        var readerTaps = 0
        val locator = model.locator(0, 5)!!.encode()
        controller.notes = listOf(Note(id = 1, bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 5,
            startLocatorJson = locator, endLocatorJson = locator, selectedText = "Hello", note = "comment",
            color = "#ffff00", createdAt = 1))
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPdfTextAnnotations provides controller) {
                    Box(Modifier.size(300.dp, 400.dp).pointerInput(Unit) { detectTapGestures { readerTaps++ } }) {
                        PdfPageTextLayer(0, true, RasterHorizontalCrop.FULL, 0.75f, true)
                    }
                }
            }
        }
        compose.waitForIdle()
        val glyph = model.glyphs.first()
        fun tap() = compose.onNodeWithTag("pdf-text-page-0").performTouchInput {
            click(Offset((glyph.quad.left + glyph.quad.right) / 2f * width,
                (glyph.quad.top + glyph.quad.bottom) / 2f * height))
        }
        tap()
        compose.waitUntil(5000) { controller.selection != null }
        compose.runOnIdle {
            assertEquals("Hello", controller.selection?.text)
            assertTrue(controller.menuState()!!.hasNote)
            assertEquals(0, readerTaps)
            controller.beginEdit()
            assertEquals("comment", controller.noteText)
            controller.editing = false
            controller.clear()
            controller.enabled = false
        }
        tap()
        compose.runOnIdle { assertNull(controller.selection); assertEquals(1, readerTaps) }
        controller.session.close()
    }

    @Test fun pdfMenuShowsOnlyImplementedActionsAndRetainsOrdinaryReaderDefaults() {
        val context = RuntimeEnvironment.getApplication()
        val state = SelectionState(0, 0, 0, 5, "Hello", 100f, 100f,
            selTopY = 100f, selBottomY = 120f, selStartX = 50f, selEndX = 150f)
        var copies = 0
        compose.setContent {
            MaterialTheme {
                SelectionMenuOverlay(state, "light", isDragging = false, reappearKey = 0,
                    selectionMenuItems = SelectionMenuSettings(),
                    supportedActions = PdfSelectionSupportedActions,
                    onDismiss = {}, onHighlight = {}, onNote = {}, onExcerpt = {}, onSearch = {},
                    onWebSearch = {}, onDictionary = {}, onDictionaryAppSelected = {},
                    onCopy = { copies++ }, onViewNote = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.menu_copy)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, copies) }
        compose.onNodeWithText(context.getString(R.string.excerpt_title)).assertExists()
        assertTrue(PdfSelectionSupportedActions.containsAll(setOf(SelectionMenuAction.SEARCH,
            SelectionMenuAction.WEB_SEARCH, SelectionMenuAction.DICTIONARY)))
        assertFalse(SelectionMenuAction.REPLACE in PdfSelectionSupportedActions)
    }
    @Test fun zoomedLongPressToleratesFingerJitterAndNearbyTextAndKeepsHandlesSmall() {
        val controller = controller()
        val model = runBlocking { controller.session.page(0)!! }
        controller.range = LongPressSelectionRange.WORD
        var zoom by mutableFloatStateOf(1f)
        var screenDensity = 1f
        var view: android.view.View? = null
        compose.setContent {
            screenDensity = LocalDensity.current.density
            view = LocalView.current
            MaterialTheme {
                CompositionLocalProvider(LocalPdfTextAnnotations provides controller) {
                    Box(Modifier.size(300.dp, 400.dp).graphicsLayer { scaleX = zoom; scaleY = zoom; transformOrigin = TransformOrigin(0f, 0f) }) {
                        PdfPageTextLayer(0, true, RasterHorizontalCrop.FULL, 0.75f, true, zoom)
                    }
                }
            }
        }
        compose.waitForIdle()
        val glyph = model.glyphs.first()
        // Start just outside the glyph, with a small hand movement during the hold.
        compose.onNodeWithTag("pdf-text-page-0").performTouchInput {
            val target = Offset(glyph.quad.left * width - 3f * screenDensity,
                (glyph.quad.top + glyph.quad.bottom) / 2f * height)
            down(target)
            moveTo(target + Offset(screenDensity, 0f), delayMillis = 100)
            advanceEventTime(700)
            up()
        }
        compose.waitUntil(5000) { controller.selection != null }
        compose.runOnIdle {
            assertEquals("Hello", controller.selection?.text)
            assertEquals(android.view.HapticFeedbackConstants.LONG_PRESS,
                org.robolectric.Shadows.shadowOf(view!!).lastHapticFeedbackPerformed())
        }
        val original = compose.onNodeWithTag("pdf-selection-end").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle { zoom = 2f }
        compose.waitForIdle()
        val enlarged = compose.onNodeWithTag("pdf-selection-end").fetchSemanticsNode().boundsInRoot.width
        assertEquals(original, enlarged, 2f)
        // A swipe moves the page rather than dismissing a temporary selection.
        compose.onNodeWithTag("pdf-text-page-0").performTouchInput {
            swipe(Offset(width * 0.5f, height * 0.5f), Offset(width * 0.6f, height * 0.6f))
        }
        compose.runOnIdle { assertNotNull(controller.selection) }
        compose.onNodeWithTag("pdf-text-page-0").performTouchInput { click(Offset(width * 0.6f, height * 0.6f)) }
        compose.runOnIdle { assertNull(controller.selection) }
        controller.session.close()
    }

    @Test fun savedUnderlineMenuAllowsChangingColorAndStyle() {
        val context = RuntimeEnvironment.getApplication()
        val note = Note(bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 5,
            selectedText = "Hello", note = "", type = "underline",
            color = readerHighlightColorReference(0, "underline"), createdAt = 1)
        val state = SelectionState(0, 0, 0, 5, "Hello", 100f, 100f,
            overlappingUnderlines = listOf(note), selTopY = 100f, selBottomY = 120f,
            selStartX = 50f, selEndX = 150f)
        var slot = -1
        var style = -1
        compose.setContent {
            MaterialTheme {
                SelectionMenuOverlay(state, "light", isDragging = false, reappearKey = 0,
                    supportedActions = PdfSelectionSupportedActions,
                    onDismiss = {}, onHighlight = {}, onNote = {}, onExcerpt = {}, onSearch = {},
                    onWebSearch = {}, onDictionary = {}, onDictionaryAppSelected = {}, onCopy = {},
                    onViewNote = {}, onChangeUnderlineColor = { slot = it },
                    onChangeUnderlineStyle = { style = it })
            }
        }
        compose.onNodeWithContentDescription("Straight underline").performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.annotation_tag_color_number, 2))
            .performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, style); assertEquals(1, slot) }
    }

    @Test fun scrollingAcrossSavedMarksNeverOpensTheirMenus() {
        val controller = controller()
        val model = runBlocking { controller.session.page(0)!! }
        val locator = model.locator(0, 5)!!.encode()
        var scrollPass by mutableStateOf(PointerEventPass.Initial)
        var scrollMovements = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPdfTextAnnotations provides controller) {
                    Box(Modifier.size(300.dp, 400.dp).pointerInput(scrollPass) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = scrollPass)
                            do {
                                val event = awaitPointerEvent(scrollPass)
                                event.changes.forEach {
                                    if (it.positionChangedIgnoreConsumed()) {
                                        scrollMovements++
                                        it.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }) {
                        PdfPageTextLayer(0, true, RasterHorizontalCrop.FULL, .75f, true)
                    }
                }
            }
        }
        // Zoomed panning and lazy scrolling consume movement in different passes.
        for (pass in listOf(PointerEventPass.Initial, PointerEventPass.Main)) {
            for (type in listOf("highlight", "underline")) {
                compose.runOnIdle {
                    scrollPass = pass
                    controller.notes = listOf(Note(bookId = "book", chapterIndex = 0,
                        startPosition = 0, endPosition = 5, startLocatorJson = locator,
                        endLocatorJson = locator, selectedText = "Hello", note = "", type = type,
                        color = readerHighlightColorReference(0, type), createdAt = 1))
                    controller.clear()
                }
                val glyph = model.glyphs.first()
                compose.onNodeWithTag("pdf-text-page-0").performTouchInput {
                    val start = Offset((glyph.quad.left + glyph.quad.right) / 2 * width,
                        (glyph.quad.top + glyph.quad.bottom) / 2 * height)
                    down(start)
                    // The page can follow the finger, keeping local movement
                    // below touch slop even though the parent is already scrolling.
                    moveTo(start + Offset(4f, 0f), delayMillis = 50)
                    up()
                }
                compose.runOnIdle {
                    assertNull("$type swipe in $pass opened a selection", controller.selection)
                    assertFalse(controller.editing)
                    assertFalse(controller.holding)
                }
            }
        }
        compose.runOnIdle { assertEquals(4, scrollMovements) }
        controller.session.close()
    }

}
