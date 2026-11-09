package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EpubScrollViewportTest {
    @Test
    fun scrollingKeepsMarginsOutsideTheContentAndRestoresFullSizeForPaging() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val host = EpubPageTurnHost(activity.get())
        activity.get().setContentView(host)
        fun layout() {
            host.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
            )
            host.layout(0, 0, 400, 800)
        }
        fun assertViewport(top: Int, bottom: Int) {
            host.allWebViews().forEach { view ->
                val margins = view.layoutParams as ViewGroup.MarginLayoutParams
                assertEquals(top, margins.topMargin)
                assertEquals(bottom, margins.bottomMargin)
                assertEquals(400, view.measuredWidth)
                assertEquals(800 - top - bottom, view.measuredHeight)
            }
            assertEquals(800, host.measuredHeight)
            assertEquals(0, host.paddingTop)
            assertEquals(0, host.paddingBottom)
        }
        try {
            host.setScrollViewportInsets(40, 60)
            layout()
            assertViewport(40, 60)
            assertTrue(host.ensureWarmPage())
            layout()
            assertViewport(40, 60)
            host.activeWebView.scrollTo(0, 350)
            assertViewport(40, 60)
            // A live settings change updates both the visible and prepared chapters.
            host.setScrollViewportInsets(70, 30)
            layout()
            assertViewport(70, 30)
            // Publisher paper is inside the WebView, so foreground-only margins
            // must leave every visible/prepared surface at the full host size.
            host.setScrollViewportInsets(70, 30, contentOnly = true)
            layout()
            assertViewport(0, 0)
            host.setScrollViewportInsets(30, 80, contentOnly = true)
            layout()
            assertViewport(0, 0)
            host.setScrollViewportInsets(30, 80)
            layout()
            assertViewport(30, 80)
            host.setScrollViewportInsets(0, 0)
            layout()
            assertViewport(0, 0)
        } finally {
            host.release()
            activity.pause().stop().destroy()
        }
    }
}
