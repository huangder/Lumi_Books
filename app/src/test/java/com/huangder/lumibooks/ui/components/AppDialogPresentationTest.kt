package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.*
import com.huangder.lumibooks.ui.home.ImportBooksActionSheet
import com.huangder.lumibooks.R
import androidx.test.core.app.ApplicationProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppDialogPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actionsAdaptToMeasuredLabelsAndFontScaleWithoutTruncation() {
        val large = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            EBookReaderTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, if (large.value) 2f else 1f),
                    LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Column(Modifier.width(272.dp)) {
                        AppDialogActions(listOf(
                            AppDialogAction("取消", {}, AppDialogActionRole.Cancel),
                            AppDialogAction("保存更改", { clicks++ })
                        ))
                    }
                }
            }
        }
        compose.onNodeWithTag("dialog_actions_horizontal").assertExists()
        val cancel = compose.onNodeWithText("取消").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithText("保存更改").fetchSemanticsNode().boundsInRoot
        assertTrue(cancel.left < save.left)
        compose.runOnIdle { rtl.value = true }
        assertTrue(compose.onNodeWithText("取消").fetchSemanticsNode().boundsInRoot.left >
            compose.onNodeWithText("保存更改").fetchSemanticsNode().boundsInRoot.left)
        compose.runOnIdle { large.value = true }
        compose.onNodeWithTag("dialog_actions_vertical").assertExists()
        assertTrue(compose.onNodeWithText("保存更改").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText("取消").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("保存更改").performClick()
        assertEquals(1, clicks)
    }

    @Test fun singleActionFillsWidthAndDisabledActionDoesNotSubmit() {
        var clicks = 0
        compose.setContent {
            EBookReaderTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.width(272.dp)) {
                        AppDialogActions(listOf(AppDialogAction("下载", { clicks++ }, enabled = false)))
                    }
                }
            }
        }
        val action = compose.onNodeWithText("下载")
        action.assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
        val button = compose.onNode(hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(272f, button.width, 1f)
        assertTrue(button.height >= 48f)
    }

    @Test fun nestedDialogsRestoreTheirParentAndConstrainAlertWidth() {
        val nested = mutableStateOf(false)
        val open = mutableStateOf(true)
        val dark = mutableStateOf(false)
        lateinit var host: LiquidGlassDialogHostState
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(darkTheme = dark.value) {
                CompositionLocalProvider(LocalAppTheme provides "liquid_glass",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(false, false)) {
                    val backdrop = rememberLayerBackdrop()
                    LiquidGlassDialogHost(backdrop = backdrop, modifier = Modifier.fillMaxSize()) {
                        host = LocalLiquidGlassDialogHost.current!!
                        Box(Modifier.fillMaxSize().background(AppColors.WindowBg).layerBackdrop(backdrop)) {
                            Text("页面内容", Modifier.testTag("page_content"))
                        }
                        if (open.value) LiquidGlassAlertDialog(
                            onDismissRequest = { open.value = false }, titleText = "保存阅读设置？",
                            text = { Text("更改将应用到当前书籍。") },
                            modifier = Modifier.testTag("alert_card"),
                            actions = listOf(AppDialogAction("继续", { nested.value = true }),
                                AppDialogAction("取消", { open.value = false }, AppDialogActionRole.Cancel))
                        )
                        if (nested.value) LiquidGlassAlertDialog(
                            onDismissRequest = { nested.value = false }, titleText = "确认保存",
                            actions = listOf(AppDialogAction("返回", { nested.value = false }))
                        )
                    }
                }
            }
        }
        compose.waitUntil { host.visible }
        compose.waitForIdle()
        val card = compose.onNodeWithTag("alert_card").fetchSemanticsNode().boundsInRoot
        val density = view.resources.displayMetrics.density
        assertTrue("Alert width is capped independently of task sheets", card.width <= 320f * density + 1f)
        compose.onNodeWithTag("page_content").assertDoesNotExist()
        savePreview(view, "alert-light")
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle()
        savePreview(view, "alert-dark")
        compose.onNodeWithText("继续").performClick()
        compose.waitUntil { host.dialogs.size == 2 }
        compose.onNodeWithText("返回").performClick()
        compose.waitUntil { host.dialogs.size == 1 && host.visible }
        compose.onNodeWithText("保存阅读设置？").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.waitUntil { host.dialogs.isEmpty() }
        compose.onNodeWithTag("page_content").assertExists()
    }

    @Test fun contentSurfacesDoNotAcquireGlassDepthOrSampleBehindTheirText() {
        var depth = -1
        compose.setContent {
            EBookReaderTheme {
                CompositionLocalProvider(LocalAppTheme provides "liquid_glass",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                    LocalLiquidGlassLayer provides 2) {
                    LiquidGlassSurface(AppShapes.rounded(18.dp), Color.White,
                        modifier = Modifier.size(200.dp, 100.dp), role = AppSurfaceRole.Content) {
                        depth = LocalLiquidGlassLayer.current
                        Text("清晰内容")
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(2, depth) }
        compose.onNodeWithText("清晰内容").assertIsDisplayed()
    }

    @Test fun anchoredImportKeepsPickerWorkflowAliveAndCancelsOnlyOnDismiss() {
        val showing = mutableStateOf(false)
        val preparing = mutableStateOf(false)
        var bounds by mutableStateOf<Rect?>(null)
        var selections = 0
        var cancellations = 0
        lateinit var host: LiquidGlassMenuHostState
        val context = ApplicationProvider.getApplicationContext<Application>()
        compose.setContent {
            EBookReaderTheme {
                CompositionLocalProvider(LocalAppTheme provides "liquid_glass",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(false, false)) {
                    LiquidGlassMenuHost(Modifier.fillMaxSize()) {
                        host = LocalLiquidGlassMenuHost.current!!
                        val source = remember { Any() }
                        LiquidGlassTextButton("Import", { showing.value = true },
                            Modifier.padding(40.dp).liquidGlassMenuAnchor(source)
                                .onGloballyPositioned { bounds = it.boundsInWindow() })
                        if (showing.value) ImportBooksActionSheet(
                            isPreparing = preparing.value, authorizedDirectoryUris = emptyList(),
                            anchorBounds = bounds, sourceId = source,
                            onDismiss = { cancellations++; showing.value = false },
                            onSelectFiles = { selections++ }, onAuthorizeDirectory = {},
                            onOpenFolderBooks = {}, onImportWebdav = {}, onImportOpds = {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Import").performClick()
        compose.waitUntil { host.activeMenu != null }
        val files = context.getString(R.string.import_select_files)
        compose.onNodeWithText(files).performScrollTo().performClick()
        assertEquals(1, selections)
        assertEquals(0, cancellations)
        assertTrue(showing.value)
        compose.runOnIdle { preparing.value = true }
        compose.onNodeWithText(files).assertIsNotEnabled()
        // A successful external picker result moves to the confirmation page, rather than cancelling.
        compose.runOnIdle { showing.value = false }
        compose.waitForIdle()
        compose.waitUntil { host.activeMenu == null }
        assertEquals(0, cancellations)
        compose.runOnIdle { preparing.value = false; showing.value = true }
        compose.waitForIdle()
        compose.waitUntil { host.activeMenu != null }
        compose.runOnIdle { host.dismiss() }
        compose.waitUntil { !showing.value }
        assertEquals(1, cancellations)
    }

    private fun savePreview(view: View, name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = File("build/reports/ios-alignment").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
