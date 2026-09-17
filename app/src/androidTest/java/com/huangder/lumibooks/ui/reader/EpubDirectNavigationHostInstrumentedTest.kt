package com.huangder.lumibooks.ui.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubDirectNavigationHostInstrumentedTest {
    @Test
    fun preparedNextViewReplacesActiveOnlyWhenPromoted() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val host = EpubPageTurnHost(context)
            host.layout(0, 0, 1080, 1920)
            host.allWebViews().forEach { it.layout(0, 0, 1080, 1920) }

            val originalActive = host.activeWebView
            val staged = host.nextWebView
            val source = EpubPageTarget(chapterIndex = 2, pageIndex = 4)
            val target = EpubPageTarget(chapterIndex = 7, pageIndex = 1)
            host.setCurrentPage(source.chapterIndex, source.pageIndex, pageCount = 9)
            host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, target, generation = 12)

            assertSame(originalActive, host.activeWebView)

            host.markPreloadReady(
                slot = EpubPageTurnHost.PreloadSlot.NEXT,
                requested = target,
                generation = 12,
                actualPageIndex = target.pageIndex,
                actualPageCount = 6,
                sourceView = staged
            )
            assertTrue(host.promotePreparedPage(EpubPageTurnHost.PreloadSlot.NEXT, target, 6))

            assertSame(staged, host.activeWebView)
            assertSame(originalActive, host.previousWebView)
            assertEquals(target, host.currentPageTarget())
            assertEquals(6, host.currentPageCount())
            assertEquals(source, host.preloadTarget(EpubPageTurnHost.PreloadSlot.PREVIOUS))
        }
    }
}
