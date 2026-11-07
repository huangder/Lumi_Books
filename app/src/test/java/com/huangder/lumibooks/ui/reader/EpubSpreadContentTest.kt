package com.huangder.lumibooks.ui.reader

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.webkit.WebViewAssetLoader
import com.huangder.lumibooks.util.epub.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpubSpreadContentTest {
    @get:Rule val compose = createComposeRule()

    /** Exercises the Compose boundary: manually sizing the host cannot detect a collapsed AndroidView. */
    @Test fun spreadFillsTheReadingViewportWithoutAnIntrinsicWebViewSize() {
        lateinit var root: View
        compose.setContent {
            root = LocalView.current.rootView
            Box(Modifier.fillMaxSize()) {
                EpubWebViewReader(
                    session = EmptySession, twoPageSpread = true, chapterIndex = 0,
                    fontSizeSp = 16f, fontType = "original", fontFilePath = null,
                    textColorOverride = null, theme = "day", coverBackgroundColor = null,
                    restoreLocatorJson = null, restoreProgression = 0f,
                    onPageTextProviderReady = {}, onPageTurnHandlerReady = {},
                    onPageChanged = { _, _, _, _ -> }, onCenterTap = {}, onImagePreviewOpen = {},
                    onChapterTurn = {}, onInternalLink = { _, _ -> }, onExternalLink = {},
                    onSelection = {}, onSelectionCleared = {}, onSearchResolved = { _, _ -> },
                    onRenderUnavailable = {}, modifier = Modifier.fillMaxSize()
                )
            }
        }
        compose.runOnIdle {
            val host = descendants(root).filterIsInstance<EpubSpreadTurnHost>().single()
            assertTrue(host.width > 0)
            assertTrue(host.height > 0)
            assertEquals((host.parent as View).width, host.width)
            assertEquals((host.parent as View).height, host.height)
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private object EmptySession : BookRenderSession {
        override val chapterCount = 1
        override val assetLoader = WebViewAssetLoader.Builder().build()
        override fun chapterUrl(chapterIndex: Int, fragment: String?) = "https://appassets.androidplatform.net/book.xhtml"
        override fun chapterHref(chapterIndex: Int) = "book.xhtml"
        override fun chapterIndexForUrl(url: String) = if (url.contains("book.xhtml")) 0 else null
        override fun imageUrl(sourceChapterIndex: Int, source: String): String? = null
        override fun readImageUrl(url: String): EpubResource? = null
        override fun resolveInternalLink(sourceChapterIndex: Int, href: String): Pair<Int, String?>? = null
        override fun readerFontUrl(filePath: String?): String? = null
        override fun readerBackgroundUrl(filePath: String?): String? = null
        override fun renditionLayout(chapterIndex: Int) = EpubRenditionLayout.REFLOWABLE
        override fun pageProgressionDirection(chapterIndex: Int) = EpubPageProgressionDirection.LTR
        override fun searchText(chapterIndex: Int) = ""
        override fun close() = Unit
    }
}
