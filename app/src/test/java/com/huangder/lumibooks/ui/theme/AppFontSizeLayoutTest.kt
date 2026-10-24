@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.huangder.lumibooks.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.domain.model.AppFontSize
import com.huangder.lumibooks.ui.components.FloatingTabBar
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.Material3BottomNavigationBar
import com.huangder.lumibooks.ui.components.tabs
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.reader.ActionCapsule
import com.huangder.lumibooks.ui.settings.DropdownSettingRow
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w320dp-h850dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFontSizeLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val labels = listOf("极小", "小", "标准", "中", "大", "超大")
    private data class Appearance(
        val size: AppFontSize = AppFontSize.STANDARD,
        val systemScale: Float = 1f,
        val theme: String = "lumi",
        val dark: Boolean = false,
        val ink: Boolean = false
    )
    private var appearance by mutableStateOf(Appearance())
    private var clicks = 0
    private var showDialog by mutableStateOf(false)

    private fun mount() {
        compose.setContent {
            view = LocalView.current
            val current = appearance
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, current.systemScale)) {
                EBookReaderTheme(appFontSize = current.size, appTheme = current.theme,
                    darkTheme = current.dark, dynamicColor = current.theme == "material3",
                    eInkMode = current.ink, motionPreference = MotionPreference.REDUCED) {
                    // Exercise both shared-button branches even on software-only rendering hosts.
                    CompositionLocalProvider(LocalAppTheme provides if (current.ink) "lumi" else current.theme) {
                        Column(Modifier.fillMaxSize().background(AppColors.PageBg)) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("显示与外观", color = AppColors.TextPrimary, fontSize = 20.sp)
                                DropdownSettingRow(label = "软件字号",
                                    options = AppFontSize.entries.zip(labels).map { (size, label) -> size.storedValue to label },
                                    selected = current.size.storedValue,
                                    onSelect = { appearance = appearance.copy(size = AppFontSize.fromStoredValue(it)) })
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("取消", "确定", "编辑").forEach { label ->
                                        LiquidGlassTextButton(text = label, onClick = { clicks++ })
                                    }
                                    LiquidGlassButton(onClick = { clicks++ }, compact = true) {
                                        Text("筛选", fontSize = 13.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("书摘", "搜索", "设置").forEach { label ->
                                        ActionCapsule(AppIcons.Gear, label, AppColors.CardBg, AppColors.TextPrimary,
                                            Color.Transparent, forceSolid = true, modifier = Modifier.weight(1f),
                                            onClick = { clicks++ })
                                    }
                                }
                                LiquidGlassTextButton(text = "打开弹窗", onClick = { showDialog = true })
                            }
                            if (current.theme == "material3" && !current.ink) {
                                Material3BottomNavigationBar(selectedIndex = 0, onTabSelected = {})
                            } else {
                                FloatingTabBar(selectedIndex = 0, onTabSelected = {}, reserveImportButtonSpace = true)
                            }
                        }
                        if (showDialog) {
                            LiquidGlassAlertDialog(onDismissRequest = { showDialog = false },
                                title = { Text("软件字号") }, text = { Text("操作按钮随字号调整") },
                                confirmButton = { LiquidGlassTextButton("保存", { showDialog = false; clicks++ }) },
                                dismissButton = { LiquidGlassTextButton("返回", { showDialog = false }) })
                        }
                    }
                }
            }
        }
    }

    private fun assertSingleLine(label: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals("$label must expose a text layout", 1, results.size)
        val result = results.single()
        assertEquals("$label must not wrap", 1, result.lineCount)
        assertFalse("$label must not be ellipsized", result.isLineEllipsized(0))
        // A non-wrapping paragraph can retain the parent's width even when the Text
        // node wraps its content. Check the actual glyph bounds, not paragraph width.
        assertTrue("$label must fit horizontally: $appearance right=${result.getLineRight(0)} width=${result.size.width}",
            result.getLineLeft(0) >= -.5f && result.getLineRight(0) <= result.size.width + .5f)
        assertTrue("$label must fit vertically: $appearance bottom=${result.getLineBottom(0)} height=${result.size.height}",
            result.getLineBottom(0) <= result.size.height + .5f)
        compose.onNodeWithText(label).assertIsDisplayed()
    }

    private fun verifyShortActions() {
        val actions = listOf("取消", "确定", "编辑", "筛选", "书摘", "搜索", "设置")
        actions.forEach { assertSingleLine(it) }
        val bounds = actions.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        bounds.forEach { assertTrue("Action must stay on screen", it.left >= 0 && it.right <= view.width && it.bottom <= view.height) }
        bounds.forEachIndexed { i, a -> bounds.drop(i + 1).forEach { b ->
            assertFalse("Actions must not overlap", a.overlaps(b))
        } }
        val before = clicks
        actions.forEach { compose.onNodeWithText(it).performClick() }
        assertEquals(before + actions.size, clicks)
        if (!appearance.ink && appearance.theme in listOf("material3", "liquid_glass")) {
            tabs.forEach { assertSingleLine(view.resources.getString(it.titleRes)) }
        }
    }

    private fun save(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/app-font-size/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun sixLevelsAndSystemLargeTextKeepShortActionsComplete() {
        mount()
        for (systemScale in listOf(1f, 2f)) {
            AppFontSize.entries.forEach { size ->
                compose.runOnIdle { appearance = Appearance(size, systemScale) }
                verifyShortActions()
                save("lumi-${size.storedValue}-system-$systemScale")
            }
        }
    }

    @Test fun largestTextWorksAcrossThemesAndDialogActions() {
        mount()
        for (theme in listOf("lumi", "material3", "liquid_glass")) {
            for (dark in listOf(false, true)) {
                compose.runOnIdle { appearance = Appearance(AppFontSize.EXTRA_LARGE, 2f, theme, dark) }
                save("$theme-${if (dark) "dark" else "light"}-largest")
                verifyShortActions()
                compose.onNodeWithText("打开弹窗").performClick()
                assertSingleLine("保存")
                assertSingleLine("返回")
                compose.onNodeWithText("保存").performClick()
            }
        }
        compose.runOnIdle { appearance = Appearance(AppFontSize.EXTRA_LARGE, 2f, ink = true) }
        verifyShortActions()
        save("ink-largest")
    }

    @Test fun selectingSizeUpdatesInterfaceImmediately() {
        mount()
        val previousWidth = compose.onNodeWithText("取消", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithText("标准").performClick()
        compose.onNodeWithText("超大").performClick()
        compose.runOnIdle { assertEquals(AppFontSize.EXTRA_LARGE, appearance.size) }
        val enlargedWidth = compose.onNodeWithText("取消", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        assertTrue(enlargedWidth > previousWidth)
        verifyShortActions()
    }

    @Test fun nestedThemesApplySizeOnceAndStandardRestoresOriginalDensity() {
        var size by mutableStateOf(AppFontSize.EXTRA_LARGE)
        val system = Density(2f, 1.5f)
        lateinit var outer: Density
        lateinit var inner: Density
        lateinit var original: Density
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides system) {
                EBookReaderTheme(appFontSize = size) {
                    outer = LocalDensity.current
                    EBookReaderTheme {
                        inner = LocalDensity.current
                        original = LocalAppBaseDensity.current!!
                    }
                }
            }
        }
        for (level in listOf(AppFontSize.EXTRA_LARGE, AppFontSize.SMALL, AppFontSize.STANDARD)) {
            compose.runOnIdle { size = level }
            compose.runOnIdle {
                assertSame(system, original)
                assertEquals(with(outer) { 16.sp.toPx() }, with(inner) { 16.sp.toPx() }, .001f)
                assertEquals(with(system) { (16.sp * level.scale).toPx() }, with(inner) { 16.sp.toPx() }, .001f)
                if (level == AppFontSize.STANDARD) assertSame(system, inner)
            }
        }
    }
}
