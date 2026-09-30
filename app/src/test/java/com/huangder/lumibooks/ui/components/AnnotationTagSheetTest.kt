package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.huangder.lumibooks.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w320dp-h480dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnnotationTagSheetTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test fun narrowLargeFontAllowsScrollingAndSavesTypedNewTag() {
        var saved: List<String>? = null
        val tags = (1..20).map { "Reading tag %02d with a very long descriptive name".format(it) }
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                    AnnotationTagSheet(listOf(tags.first()), tags, { saved = it }, {})
                }
            }
        }
        compose.onNodeWithText(label(R.string.confirm)).assertIsDisplayed()
        compose.onNodeWithText(tags.first()).performClick()
        compose.onNodeWithText(label(R.string.annotation_tag_new)).performScrollTo().performTextInput(" New tag ")
        compose.onNodeWithText(label(R.string.confirm)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("New tag"), saved) }
    }

    @Test
    @Config(qualifiers = "en-w640dp-h320dp-land-mdpi")
    fun landscapeKeepsConfirmVisibleAndCancelDoesNotSaveDraft() {
        var saves = 0
        var dismissed = false
        compose.setContent {
            EBookReaderTheme(appTheme = "liquid_glass", darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f),
                    LocalAppTheme provides "liquid_glass",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                    LocalMotionEnabled provides false) {
                    AnnotationTagSheet(emptyList(), (1..30).map { "Tag $it" }, { saves++ }, { dismissed = true })
                }
            }
        }
        compose.onNodeWithText(label(R.string.annotation_tag_new)).performScrollTo().performTextInput("Draft")
        compose.onNodeWithText(label(R.string.confirm)).assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithText(label(R.string.cancel)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(dismissed); assertEquals(0, saves) }
    }

    @Test fun globalRenameAndDeleteKeepTheCurrentDraftConsistent() {
        val available = mutableStateOf(listOf("Alpha"))
        var rename: Pair<String, String>? = null
        var removed: String? = null
        var saved: List<String>? = null
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                AnnotationTagSheet(listOf("Alpha"), available.value, { saved = it }, {},
                    onRename = { old, next -> rename = old to next; available.value = listOf(next) },
                    onDelete = { removed = it; available.value = emptyList() })
            }
        }
        compose.onNodeWithContentDescription(label(R.string.annotation_tag_manage)).performClick()
        compose.onNodeWithText(label(R.string.annotation_tag_rename)).performClick()
        compose.onNode(hasSetTextAction() and hasText("Alpha")).performTextReplacement("Beta")
        compose.onAllNodesWithText(label(R.string.confirm)).onLast().performClick()
        compose.runOnIdle { assertEquals("Alpha" to "Beta", rename) }
        compose.onNodeWithText("Beta").assertIsOn()
        compose.onNodeWithContentDescription(label(R.string.annotation_tag_manage)).performClick()
        compose.onNodeWithText(label(R.string.delete)).performClick()
        compose.onNodeWithText(label(R.string.delete)).performClick()
        compose.runOnIdle { assertEquals("Beta", removed) }
        compose.onNodeWithText(label(R.string.confirm)).performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), saved) }
    }
}
