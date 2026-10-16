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
