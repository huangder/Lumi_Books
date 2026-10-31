package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.view.View
import com.huangder.lumibooks.ui.layout.AdaptiveWindowInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderSpreadMeasurementTest {
    @Test fun openingLandscapeReaderMeasuresBothHalvesInFirstLayout() {
        withReader { reader ->
            reader.configure(24f, "day", 1, 0, 0, twoPageSpread = true)
            layout(reader, 1200, 800)
            assertSpreadGeometry(reader)
        }
    }

    @Test fun rotatingReaderMeasuresBothHalvesBeforeDisplayingLandscape() {
        withReader { reader ->
            reader.configure(24f, "day", 1, 0, 0, twoPageSpread = true)
            layout(reader, 800, 1200)
            assertEquals(800, reader.slotManager.getCurSlot().contentView.width)
            layout(reader, 1200, 800)
            assertSpreadGeometry(reader)
            layout(reader, 800, 1200)
            assertEquals(800, reader.slotManager.getCurSlot().contentView.width)
            assertEquals(View.GONE, reader.slotManager.getCurSlot().rightContentView!!.visibility)
        }
    }

    @Test fun resizingLandscapeReaderUpdatesBothHalvesInSameLayout() {
        withReader { reader ->
            reader.configure(24f, "day", 1, 0, 0, twoPageSpread = true)
            layout(reader, 1200, 800)
            layout(reader, 1600, 900)
            assertSpreadGeometry(reader)
        }
    }

    @Test fun foldableCoverInnerRotationAndSplitReuseTabletSpreadRules() {
        withReader { reader ->
            val windows = listOf(
                412 to 915, 673 to 841, 841 to 673, 673 to 673,
                852 to 883, 883 to 852, 800 to 1280,
                599 to 841, 600 to 841, 600 to 750, 600 to 751,
                500 to 800, 841 to 673, 412 to 915
            )
            for ((width, height) in windows) {
                val window = AdaptiveWindowInfo(width, height, smallestWidthDp = 412)
                reader.configure(24f, "day", 1, 0, 0,
                    twoPageSpread = window.isMediumWidthOrLarger)
                layout(reader, width, height)
                if (window.usesWideLayout) {
                    assertSpreadGeometry(reader)
                } else {
                    assertFalse(reader.isTwoPageSpreadActive)
                    assertEquals(width, reader.slotManager.getCurSlot().contentView.width)
                    assertEquals(View.GONE, reader.slotManager.getCurSlot().rightContentView!!.visibility)
                }
            }
            // An unfolded landscape window must still respect the user's disabled spread setting.
            reader.configure(24f, "day", 1, 0, 0, twoPageSpread = false)
            layout(reader, 841, 673)
            assertFalse(reader.isTwoPageSpreadActive)
            assertEquals(841, reader.slotManager.getCurSlot().contentView.width)
        }
    }

    @Test fun disablingSpreadInPortraitIsRetainedForTheNextLandscapeResize() {
        withReader { reader ->
            reader.configure(24f, "day", 1, 0, 0, twoPageSpread = true)
            layout(reader, 800, 1280)
            // The active layout remains single-page, so this takes configure's no-relayout path.
            reader.configure(24f, "day", 1, 0, 0, twoPageSpread = false)
            layout(reader, 841, 673)
            assertFalse(reader.isTwoPageSpreadActive)
            assertEquals(841, reader.slotManager.getCurSlot().contentView.width)
            assertEquals(View.GONE, reader.slotManager.getCurSlot().rightContentView!!.visibility)
        }
    }

    private fun assertSpreadGeometry(reader: ReadView) {
        assertTrue(reader.isTwoPageSpreadActive)
        val gutter = (16f * reader.resources.displayMetrics.density).toInt()
        val halfWidth = (reader.width - gutter) / 2
        for (slot in listOf(reader.slotManager.getPrevSlot(), reader.slotManager.getCurSlot(),
            reader.slotManager.getNextSlot())) {
            assertEquals(halfWidth, slot.contentView.width)
            assertEquals(halfWidth, slot.rightContentView!!.width)
            assertEquals(halfWidth + gutter, slot.rightContentView!!.left)
            assertEquals(View.VISIBLE, slot.rightContentView!!.visibility)
        }
    }

    private fun layout(reader: ReadView, width: Int, height: Int) {
        reader.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        reader.layout(0, 0, width, height)
    }

    private fun withReader(block: (ReadView) -> Unit) {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        reader.setContentProvider { "双页测量验证。" }
        reader.slotManager.backgroundPreparationEnabled = false
        try {
            block(reader)
        } finally {
            reader.slotManager.destroy()
        }
    }
}
