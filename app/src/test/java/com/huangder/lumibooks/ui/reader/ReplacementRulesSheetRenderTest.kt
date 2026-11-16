package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.replacement.BookReplacementRules
import com.huangder.lumibooks.replacement.ReplacementPreview
import com.huangder.lumibooks.replacement.ReplacementProgram
import com.huangder.lumibooks.replacement.ReplacementRule
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the replacement panel states with the two canonical examples for visual review. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplacementRulesSheetRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    private val removeAds = ReplacementRule(
        "ads", name = "删广告",
        find = "(?m)^\\s*(本章未完|点击下一页继续阅读|手机用户请访问\\S+).*$\\n?",
        replacement = "", regex = true
    )
    private val rename = ReplacementRule("rename", name = "角色改名", find = "小明", replacement = "阿明")
    private val rules = BookReplacementRules(true, listOf(removeAds, rename))

    private data class Scene(val dark: Boolean, val eInk: Boolean, val content: @Composable () -> Unit)
    private val scene = mutableStateOf<Scene?>(null)
    private var mounted = false

    private fun mount(dark: Boolean = false, eInk: Boolean = false, content: @Composable () -> Unit) {
        scene.value = Scene(dark, eInk, content)
        if (!mounted) {
            mounted = true
            compose.setContent {
                view = LocalView.current
                val current = scene.value ?: return@setContent
                CompositionLocalProvider(
                    LocalMotionEnabled provides false, LocalIsDarkTheme provides current.dark,
                    LocalEInkMode provides current.eInk, LocalAppTheme provides "lumi"
                ) {
                    key(current) {
                        Column(
                            Modifier.fillMaxSize().background(AppColors.CardBg)
                                .verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) { current.content() }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun list(settings: BookReplacementRules, dark: Boolean = false, eInk: Boolean = false) = mount(dark, eInk) {
        ReplacementRuleList(settings, true, {}, { _, _ -> }, {}, { _, _ -> }, {})
    }

    private fun editor(rule: ReplacementRule, dark: Boolean = false, preview: ReplacementPreview? = null) = mount(dark) {
        ReplacementRuleEditor(rule, ReplacementProgram.validate(rule), true, {}, rules, 0) { preview }
    }

    private fun save(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/replacement/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun emptyList() {
        list(BookReplacementRules())
        compose.onNodeWithText("还没有替换规则").assertIsDisplayed()
        compose.onNodeWithContentDescription("在本书启用").assertIsDisplayed()
        save("empty-light")
    }

    @Test fun ruleListLightDarkAndEInk() {
        for ((name, dark, eInk) in listOf(Triple("list-light", false, false), Triple("list-dark", true, false),
            Triple("list-eink", false, true))) {
            list(rules, dark, eInk)
            compose.onNodeWithText("规则从上到下依次执行").assertIsDisplayed()
            compose.onNodeWithContentDescription("启用规则：删广告").assertIsDisplayed()
            compose.onNodeWithContentDescription("删广告, 正则, 删除 ${removeAds.find}").assertIsDisplayed()
            compose.onNodeWithContentDescription("角色改名, 普通, 将 小明 替换为 阿明").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("上移").onFirst()
                .assertHeightIsAtLeast(44.dp).assertWidthIsAtLeast(44.dp)
            save(name)
        }
    }

    @Test fun pausedBookSwitch() {
        list(rules.copy(enabled = false))
        compose.onNodeWithText("全书开关已关闭，以下规则暂不生效").assertIsDisplayed()
        compose.onNodeWithText("已关闭，正文显示原文，规则会保留").assertIsDisplayed()
        save("list-paused-light")
    }

    @Test fun renamePreviewLightAndDark() {
        val preview = ReplacementProgram(listOf(rename))
            .apply("王老师看着小明，说：“小明，回家吧。”", previewRule = rename.id).preview.copy(count = 12)
        for ((name, dark) in listOf("rename-light" to false, "rename-dark" to true)) {
            editor(rename, dark, preview)
            compose.onNodeWithText("匹配 12 处").assertIsDisplayed()
            compose.onNodeWithText("替换前").assertIsDisplayed()
            compose.onNodeWithText("预览显示第一处匹配附近的内容").assertIsDisplayed()
            save(name)
        }
    }

    @Test fun adDeletionPreviewLightAndDark() {
        val preview = ReplacementProgram(listOf(removeAds))
            .apply("他推开了门。\n本章未完，点击下一页继续阅读\n门外站着", previewRule = removeAds.id).preview
        for ((name, dark) in listOf("ads-light" to false, "ads-dark" to true)) {
            editor(removeAds, dark, preview)
            compose.onNodeWithText("替换内容为空：匹配到的文字会从正文中删除，原文件不变").assertIsDisplayed()
            compose.onNodeWithText("匹配 1 处").assertIsDisplayed()
            save(name)
        }
    }

    @Test fun noMatchEmptyFindAndErrors() {
        editor(rename.copy(find = "小红"), preview = ReplacementPreview())
        compose.onNodeWithText("当前章节无匹配").assertIsDisplayed()
        compose.onNodeWithText("保存后仍会作用于其他章节").assertIsDisplayed()
        save("no-match-light")

        editor(ReplacementRule("new"))
        compose.onNodeWithText("输入查找内容后，这里会显示当前章节的替换效果").assertIsDisplayed()
        save("new-empty-light")

        editor(removeAds.copy(find = "(?<=第)\\d+章", replacement = "章"), dark = true)
        compose.onNodeWithText("正则无效。支持 RE2/J 语法，环视和模式内反向引用不可用。").assertIsDisplayed()
        compose.onNodeWithText("修正上方错误后即可预览和保存").assertIsDisplayed()
        save("invalid-regex-dark")

        editor(removeAds.copy(find = "(小)(明)", replacement = "$3"))
        compose.onNodeWithText("替换内容引用了无效的捕获组，或含有未完成的转义。").assertIsDisplayed()
        save("invalid-group-light")

        editor(rename, preview = null)
        compose.onNodeWithText("当前章节无法预览替换").assertIsDisplayed()
    }
}
