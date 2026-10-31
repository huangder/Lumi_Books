package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w1280dp-h1280dp-mdpi")
class ReaderMenuLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var viewport by mutableStateOf(DpSize(1280.dp, 800.dp))
    private val labels = mutableMapOf<Int, String>()
    private var clicks = 0

    private fun mount() {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                val context = LocalContext.current
                listOf(R.string.reader_notes, R.string.reader_search, R.string.reader_theme,
                    R.string.tts_listen, R.string.reader_bookmark, R.string.more_options,
                    R.string.reader_toc).forEach { labels[it] = context.getString(it) }
                Box(Modifier.fillMaxSize()) {
                    // Only the measured viewport changes: Configuration intentionally stays square.
                    BoxWithConstraints(Modifier.size(viewport).testTag("viewport")) {
                        val layout = resolveReaderMenuLayout(
                            DpSize(maxWidth, maxHeight), DpSize(48.dp, 64.dp),
                            WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                        )
                        ReaderTopBar(
                            title = "Book", onBack = {}, bookFormat = "EPUB",
                            sideRailItemSize = layout.sideRailItemSize,
                            horizontalActions = layout.horizontalTopActions,
                            forceSolidButtons = true
                        )
                        FloatingReaderMenu(
                            visible = true, chapterTitle = "chapter-test", chapterTitles = listOf("chapter-test"),
                            chapterCount = 1, bookProgressPercent = 32f, globalPageLabel = "3 / 10",
                            currentPage = 2, chapterPageCount = 10,
                            capsuleBgColor = Color.LightGray, capsuleContentColor = Color.Black,
                            catalogProgressColor = Color.Gray, glassContentScrimColor = Color.Transparent,
                            forceSolidCapsules = true, canGoToPreviousChapter = true, canGoToNextChapter = true,
                            onCatalogClick = {}, onPreviousChapterClick = {}, onNextChapterClick = {},
                            onBookmarkClick = { clicks++ }, onSearchClick = { clicks++ }, onThemeClick = { clicks++ },
                            sideRailItemSize = layout.sideRailItemSize,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun action(res: Int) = compose.onNodeWithText(labels.getValue(res))
    private fun header(res: Int) = compose.onNodeWithContentDescription(labels.getValue(res))

    private fun assertBottomActions() {
        val notes = action(R.string.reader_notes).fetchSemanticsNode().boundsInRoot
        val search = action(R.string.reader_search).fetchSemanticsNode().boundsInRoot
        val theme = action(R.string.reader_theme).fetchSemanticsNode().boundsInRoot
        assertTrue(notes.right <= search.left && search.right <= theme.left)
        assertEquals(notes.center.y, theme.center.y, 1f)
        val more = header(R.string.more_options).fetchSemanticsNode().boundsInRoot
        val catalog = action(R.string.reader_toc).fetchSemanticsNode().boundsInRoot
        assertTrue("Header and bottom menu must not overlap", more.bottom < catalog.top)
        val root = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertTrue(theme.bottom <= root.bottom && notes.left >= root.left && theme.right <= root.right)
        listOf(R.string.reader_notes, R.string.reader_search, R.string.reader_theme).forEach { action(it).performClick() }
        assertEquals(3, clicks)
    }

    @Test
    fun shortLandscapeUsesSeparateHeaderAndBottomActions() {
        viewport = DpSize(840.dp, 375.dp)
        mount()
        assertBottomActions()
        val listen = header(R.string.tts_listen).fetchSemanticsNode().boundsInRoot
        val more = header(R.string.more_options).fetchSemanticsNode().boundsInRoot
        assertEquals(listen.center.y, more.center.y, 1f)
        assertTrue(listen.right < more.left)
    }

    @Test
    fun rotatingTheViewportRestoresPhoneLayoutWithoutAConfigurationUpdate() {
        mount()
        val more = header(R.string.more_options).fetchSemanticsNode().boundsInRoot
        val notes = action(R.string.reader_notes).fetchSemanticsNode().boundsInRoot
        val theme = action(R.string.reader_theme).fetchSemanticsNode().boundsInRoot
        assertEquals(48f, notes.width, 1f)
        assertEquals(64f, notes.height, 1f)
        assertTrue("Both side groups must fit without overlap", more.bottom < notes.top && notes.bottom < theme.top)
        compose.runOnIdle { viewport = DpSize(375.dp, 840.dp) }
        compose.waitForIdle()
        assertBottomActions()
        assertEquals(36f, header(R.string.tts_listen).fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.runOnIdle { viewport = DpSize(1280.dp, 800.dp) }
        compose.waitForIdle()
        assertEquals(48f, action(R.string.reader_notes).fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test
    fun railSpaceIncludesInsetsAndLargerLabels() {
        val regular = DpSize(48.dp, 64.dp)
        assertNotNull(resolveReaderMenuLayout(DpSize(900.dp, 600.dp), regular, 24.dp).sideRailItemSize)
        assertNull(resolveReaderMenuLayout(DpSize(900.dp, 580.dp), regular, 24.dp).sideRailItemSize)
        assertNull(resolveReaderMenuLayout(DpSize(900.dp, 600.dp), DpSize(80.dp, 96.dp), 24.dp).sideRailItemSize)
        assertFalse(resolveReaderMenuLayout(DpSize(800.dp, 1280.dp), regular, 0.dp).horizontalTopActions)
    }

    @Test
    fun nearSquareFoldableKeepsSideActionsAcrossRotationThenReturnsToTallPortraitLayout() {
        viewport = DpSize(852.dp, 883.dp)
        mount()
        for (size in listOf(DpSize(852.dp, 883.dp), DpSize(883.dp, 852.dp),
            DpSize(673.dp, 673.dp), DpSize(673.dp, 841.dp))) {
            compose.runOnIdle { viewport = size }
            compose.waitForIdle()
            val notes = action(R.string.reader_notes).fetchSemanticsNode().boundsInRoot
            val theme = action(R.string.reader_theme).fetchSemanticsNode().boundsInRoot
            val more = header(R.string.more_options).fetchSemanticsNode().boundsInRoot
            val root = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            assertEquals(48f, notes.width, 1f)
            assertEquals(64f, notes.height, 1f)
            assertEquals(notes.center.x, theme.center.x, 1f)
            assertTrue(more.bottom < notes.top && notes.bottom < theme.top)
            assertTrue(theme.right <= root.right && theme.bottom <= root.bottom)
        }
        compose.runOnIdle { viewport = DpSize(800.dp, 1280.dp) }
        compose.waitForIdle()
        assertBottomActions()
    }

    @Test
    fun nearSquareSideRailStillRequiresEnoughHeightForInsetsAndLabels() {
        val regular = DpSize(48.dp, 64.dp)
        assertNotNull(resolveReaderMenuLayout(DpSize(600.dp, 750.dp), regular, 24.dp).sideRailItemSize)
        assertNull(resolveReaderMenuLayout(DpSize(600.dp, 751.dp), regular, 24.dp).sideRailItemSize)
        assertNull(resolveReaderMenuLayout(DpSize(599.dp, 599.dp), regular, 24.dp).sideRailItemSize)
        assertNull(resolveReaderMenuLayout(DpSize(600.dp, 600.dp), DpSize(80.dp, 96.dp), 24.dp).sideRailItemSize)
    }
}
