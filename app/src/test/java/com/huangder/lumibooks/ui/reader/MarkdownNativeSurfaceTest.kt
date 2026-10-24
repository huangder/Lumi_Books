package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.util.markdown.native.*
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
class MarkdownNativeSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun composeHostKeepsLongPressAndAnnotationUpdatesAlive() {
        val source = MarkwonBlockSource(ApplicationProvider.getApplicationContext(),
            MarkdownNativeParser.parse("Markdown selection and annotations"), emptyMap())
        val note = com.huangder.lumibooks.domain.model.Note(bookId = "md", chapterIndex = 0,
            startPosition = 0, endPosition = 8, selectedText = "Markdown", note = "", color = "#FFFF0000", createdAt = 0)
        val notes = mutableStateOf(listOf(note))
        var root: View? = null
        var selection: EpubSelectionInfo? = null
        compose.setContent {
            root = LocalView.current
            MarkdownNativeReader(source, ReaderUiState(), Typeface.DEFAULT, android.graphics.Color.BLACK,
                android.graphics.Color.WHITE, false, true, null, notes.value, null, 0,
                onPage = { _, _, _ -> }, onSelection = { selection = it }, onLink = {}, onMenu = {},
                onNavigation = {}, onTurnReady = {}, onTextReady = {}, onPaginationReady = {},
                onError = { throw AssertionError(it) }, modifier = Modifier.fillMaxSize())
        }
        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        fun textView() = root?.rootView?.let(::descendants)?.filterIsInstance<TextView>()
            ?.firstOrNull { it.text.startsWith("Markdown") }
        try {
            compose.waitUntil(15000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                textView()?.layout != null
            }
            compose.runOnIdle { notes.value = listOf(note.copy(color = "#FF0000FF")) }
            compose.waitForIdle()
            compose.runOnIdle {
                val view = textView()!!
                val text = view.text as android.text.Spanned
                val span = text.getSpans(0, 8, com.huangder.lumibooks.ui.reader.engine.ReaderHighlightSpan::class.java).single()
                assertEquals(android.graphics.Color.BLUE, span.color)
                val screen = IntArray(2).also(view::getLocationOnScreen)
                val host = root!!.rootView
                val origin = IntArray(2).also(host::getLocationOnScreen)
                val x = (screen[0] - origin[0] + view.totalPaddingLeft + view.layout.getPrimaryHorizontal(3))
                val y = (screen[1] - origin[1] + view.totalPaddingTop + view.layout.getLineBaseline(0) / 2f)
                val down = android.os.SystemClock.uptimeMillis()
                val press = android.view.MotionEvent.obtain(down, down, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
                host.dispatchTouchEvent(press); press.recycle()
                org.robolectric.shadows.ShadowLooper.idleMainLooper(700, java.util.concurrent.TimeUnit.MILLISECONDS)
                val up = android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, x, y, 0)
                host.dispatchTouchEvent(up); up.recycle()
            }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals("Markdown", selection?.text) }
        } finally { source.close() }
    }
    @Test fun bothThemesAndModesUseNativeViewsAndKeepAnAnchor() {
        val source = MarkwonBlockSource(ApplicationProvider.getApplicationContext(),
            MarkdownNativeParser.parse("# Title\n\n" + "Paragraph 中文 text. ".repeat(900)), emptyMap())
        val scroll = mutableStateOf(false)
        val glass = mutableStateOf(false)
        var root: View? = null
        var locator = ""
        val errors = mutableListOf<String>()
        compose.setContent {
            root = LocalView.current
            CompositionLocalProvider(LocalAppTheme provides if (glass.value) "liquid_glass" else "lumi",
                LocalMotionEnabled provides false) {
                MarkdownNativeReader(source, ReaderUiState(), Typeface.DEFAULT, android.graphics.Color.BLACK,
                    android.graphics.Color.WHITE, false, scroll.value, null, emptyList(), null, 0,
                    onPage = { _, _, value -> locator = value }, onSelection = {}, onLink = {}, onMenu = {},
                    onNavigation = {}, onTurnReady = {}, onTextReady = {}, onPaginationReady = {},
                    onError = { errors.add(it) }, modifier = Modifier.fillMaxSize())
            }
        }
        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        try {
            compose.waitForIdle()
            compose.waitUntil(15000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                check(errors.isEmpty()) { errors.toString() }
                locator.isNotEmpty()
            }
            compose.runOnIdle {
                assertTrue("native reader is mounted", root != null)
                assertTrue(descendants(root!!.rootView).any { it is androidx.recyclerview.widget.RecyclerView || (it is TextView && it.text.isNotEmpty()) })
                assertFalse(descendants(root!!.rootView).any { it is WebView })
            }
            val anchor = org.json.JSONObject(locator).getInt("textPosition")
            compose.runOnIdle { glass.value = true; scroll.value = true }
            compose.waitForIdle()
            compose.runOnIdle {
                assertFalse(descendants(root!!.rootView).any { it is WebView })
                assertTrue(errors.toString(), errors.isEmpty())
                assertEquals(anchor, org.json.JSONObject(locator).getInt("textPosition"))
            }
        } finally { compose.runOnIdle { root = null }; source.close() }
    }
}
