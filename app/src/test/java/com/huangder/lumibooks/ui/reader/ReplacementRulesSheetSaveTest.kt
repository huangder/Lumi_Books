package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.huangder.lumibooks.replacement.BookReplacementRules
import com.huangder.lumibooks.replacement.ReplacementRule
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
class ReplacementRulesSheetSaveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmSavesAnEditedRuleAfterFindBecomesValidAgain() {
        val rule = ReplacementRule("rename", find = "小明", replacement = "阿明")
        val settings = mount(BookReplacementRules(enabled = true, rules = listOf(rule)))
        compose.onNodeWithContentDescription("普通, 将 小明 替换为 阿明").performClick()

        val confirm = compose.onNodeWithContentDescription("确定")
        compose.onNode(hasSetTextAction() and hasText("小明")).performTextReplacement("")
        confirm.assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("小红")
        confirm.assertIsEnabled().performTouchInput { click() }

        compose.onNodeWithText("添加规则", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(rule.copy(find = "小红")), settings.value.rules) }
    }

    @Test fun confirmSavesANewRuleAfterEnteringFind() {
        val settings = mount(BookReplacementRules())
        compose.onNodeWithText("添加规则", substring = true).performClick()

        val confirm = compose.onNodeWithContentDescription("确定")
        confirm.assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("广告")
        confirm.assertIsEnabled().performTouchInput { click() }

        compose.onNodeWithText("添加规则", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals("广告", settings.value.rules.single().find) }
    }

    @Test fun backAsksBeforeDiscardingAnEdit() {
        val rule = ReplacementRule("rename", find = "小明", replacement = "阿明")
        val settings = mount(BookReplacementRules(rules = listOf(rule)))
        compose.onNodeWithContentDescription("普通, 将 小明 替换为 阿明").performClick()
        compose.onNode(hasSetTextAction() and hasText("小明")).performTextReplacement("小红")

        compose.onNodeWithContentDescription("返回").performTouchInput { click() }

        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(rule), settings.value.rules) }
    }

    private fun mount(initial: BookReplacementRules) = mutableStateOf(initial).also { settings ->
        compose.setContent {
            CompositionLocalProvider(LocalAppTheme provides "liquid_glass", LocalMotionEnabled provides false) {
                LiquidGlassDialogHost(backdrop = rememberLayerBackdrop()) {
                    ReplacementRulesSheet(
                        settings = settings.value,
                        chapter = 0,
                        updateRules = { settings.value = it(settings.value) },
                        previewChapter = { _, _, _ -> null },
                        onDismiss = {}
                    )
                }
            }
        }
    }
}
