package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EpubSpreadInteractionTest {
    @Test fun rightLeafSelectionIncludesItsPhysicalWindowOffset() {
        leaf(windowX = 648, windowY = 72).useView { view ->
            val selection = epubSpreadSelection(view, 7, 3, payload(), "OPS/seven.xhtml#section")

            assertEquals(708f, selection.centerX, 0.001f)
            assertEquals(152f, selection.centerY, 0.001f)
            assertEquals(668f, selection.left, 0.001f)
            assertEquals(112f, selection.top, 0.001f)
            assertEquals(748f, selection.right, 0.001f)
            assertEquals(192f, selection.bottom, 0.001f)
            assertEquals(7, selection.chapterIndex)
            assertEquals(3, selection.pageIndex)
            assertEquals("selection", selection.text)
            assertEquals(110, selection.startPosition)
            assertEquals(119, selection.endPosition)
            for (locator in listOf(selection.startLocatorJson, selection.endLocatorJson)) {
                assertEquals("OPS/seven.xhtml#section", JSONObject(locator).getString("href"))
                assertEquals(1, JSONObject(locator).getInt("version"))
            }
            assertEquals(110, JSONObject(selection.startLocatorJson).getInt("textPosition"))
            assertEquals(119, JSONObject(selection.endLocatorJson).getInt("textPosition"))
            assertEquals("selection", JSONObject(selection.startLocatorJson).getString("exact"))
        }
    }

    @Test fun rtlFirstLeafOnRightKeepsWindowCoordinatesAndItsOwnChapter() {
        leaf(windowX = 648, windowY = 72).useView { right ->
            leaf(windowX = 24, windowY = 72).useView { left ->
                right.layoutDirection = View.LAYOUT_DIRECTION_RTL
                left.layoutDirection = View.LAYOUT_DIRECTION_RTL
                val firstLogical = epubSpreadSelection(right, 2, 8, payload(), "OPS/rtl-first.xhtml")
                val secondLogical = epubSpreadSelection(left, 3, 0, payload(), "OPS/rtl-next.xhtml")

                assertEquals(624f, firstLogical.left - secondLogical.left, 0.001f)
                assertEquals(firstLogical.top, secondLogical.top, 0.001f)
                assertEquals(2, firstLogical.chapterIndex)
                assertEquals(8, firstLogical.pageIndex)
                assertEquals(3, secondLogical.chapterIndex)
                assertEquals(0, secondLogical.pageIndex)
                assertEquals("OPS/rtl-first.xhtml", JSONObject(firstLogical.endLocatorJson).getString("href"))
                assertEquals("OPS/rtl-next.xhtml", JSONObject(secondLogical.endLocatorJson).getString("href"))
            }
        }
    }

    @Test fun missingBoundsUseCenterAndAnnotationMetadataSurvives() {
        leaf(windowX = 45, windowY = 90).useView { view ->
            val data = payload().apply {
                listOf("left", "right", "top", "bottom").forEach { remove(it) }
                put("pixelRatio", 0.5)
                put("annotationOnly", true)
            }
            val selection = epubSpreadSelection(view, 4, null, data, "OPS/four.xhtml")

            assertEquals(75f, selection.left, 0.001f)
            assertEquals(75f, selection.right, 0.001f)
            assertEquals(130f, selection.top, 0.001f)
            assertEquals(130f, selection.bottom, 0.001f)
            assertTrue(selection.annotationOnly)
            assertEquals(4, selection.chapterIndex)
            assertNull(selection.pageIndex)
        }
    }

    @Test fun missingPixelRatioUsesLeafDensity() {
        leaf(windowX = 81, windowY = 27).useView { view ->
            val selection = epubSpreadSelection(view, 1, 2, payload().apply { remove("pixelRatio") }, "OPS/one.xhtml")
            val density = view.resources.displayMetrics.density.coerceAtLeast(1f)

            assertEquals(81f + 30f * density, selection.centerX, 0.001f)
            assertEquals(27f + 40f * density, selection.centerY, 0.001f)
            assertEquals(81f + 10f * density, selection.left, 0.001f)
            assertEquals(27f + 60f * density, selection.bottom, 0.001f)
        }
    }

    private fun payload() = JSONObject("""
        {"text":"selection","start":{"href":"stale.xhtml","textPosition":110,"exact":"selection"},
         "end":{"href":"stale.xhtml","textPosition":119},"pixelRatio":2,
         "x":30,"y":40,"left":10,"top":20,"right":50,"bottom":60}
    """.trimIndent())

    private fun leaf(windowX: Int, windowY: Int): WebView =
        object : WebView(ApplicationProvider.getApplicationContext()) {
            override fun getLocationInWindow(outLocation: IntArray) {
                outLocation[0] = windowX
                outLocation[1] = windowY
            }
        }

    private inline fun WebView.useView(block: (WebView) -> Unit) {
        try { block(this) } finally { destroy() }
    }
}
