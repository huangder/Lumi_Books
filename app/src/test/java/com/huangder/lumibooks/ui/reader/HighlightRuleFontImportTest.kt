package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.app.ActivityOptionsCompat
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.highlight.HighlightRuleScanState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HighlightRuleFontImportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun importedFontIsSavedOnTheCurrentRuleWithoutReselectingIt() {
        val rule = HighlightRule(id = "rule", name = "人名", pattern = "小明")
        val fontUri = Uri.parse("file:///rule-font.ttf")
        val preset = CustomFontPreset(id = "imported", path = "/rule-font.ttf", name = "规则字体")
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) {
                    dispatchResult(requestCode, fontUri)
                }
            }
        }
        var savedRule: HighlightRule? = null

        compose.setContent {
            var customFonts by remember { mutableStateOf(emptyList<CustomFontPreset>()) }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                HighlightRulesSheet(
                    visible = true,
                    rules = listOf(rule),
                    customFonts = customFonts,
                    materializeNotes = false,
                    scanState = HighlightRuleScanState.Idle,
                    eInkModeEnabled = true,
                    onMaterializeChange = {},
                    onSaveRule = { savedRule = it },
                    onDuplicateRule = {},
                    onDeleteRule = {},
                    onRuleEnabledChange = { _, _ -> },
                    onMoveRule = { _, _ -> },
                    onImportRules = { _, _ -> error("Unexpected rule import") },
                    onExportRules = { error("Unexpected rule export") },
                    onImportFont = { uri, _ ->
                        assertEquals(fontUri, uri)
                        customFonts = listOf(preset)
                        preset
                    },
                    onCancelScan = {},
                    onRebuild = {},
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithContentDescription("编辑").performScrollTo().performClick()
        compose.onNode(hasText("跟随正文") and hasAnySibling(hasText("高亮字体")))
            .performScrollTo().performClick()
        compose.onNodeWithText("+ 导入字体").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(preset.name)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("保存").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }

        compose.runOnIdle { assertEquals(preset.fontTypeKey, savedRule?.fontType) }
    }
}
