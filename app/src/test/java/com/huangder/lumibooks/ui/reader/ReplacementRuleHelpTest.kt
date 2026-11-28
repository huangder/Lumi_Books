package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.replacement.ReplacementProgram
import com.huangder.lumibooks.replacement.ReplacementRule
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplacementRuleHelpTest {
    @get:Rule val compose = createComposeRule()

    @Test fun displayedRecipesProduceTheirDocumentedResults() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ReplacementHelpExamples.forEach { example ->
            val rule = ReplacementRule(
                "help", find = context.getString(example.find), regex = true,
                replacement = if (example.space) " " else example.value?.let(context::getString).orEmpty()
            )
            assertNull(rule.find, ReplacementProgram.validate(rule))
            assertEquals(rule.find, context.getString(example.after),
                ReplacementProgram(listOf(rule)).apply(context.getString(example.before)).display)
        }
    }

    // Software screenshots exercise the glass blur without API 33+ RuntimeShader.
    @Test @Config(sdk = [31])
    fun tabsAlignWithBackAndCopyTheEntirePromptInEveryTheme() {
        data class Scene(val theme: String, val dark: Boolean, val ink: Boolean = false)
        val scene = mutableStateOf(Scene("lumi", false))
        lateinit var view: View
        compose.setContent {
            val current = scene.value
            key(current) {
                EBookReaderTheme(appTheme = current.theme, darkTheme = current.dark, eInkMode = current.ink,
                    motionPreference = MotionPreference.REDUCED) {
                    view = LocalView.current
                    ReplacementRuleHelpPage {}
                }
            }
        }
        for (current in listOf(Scene("lumi", false), Scene("lumi", true),
            Scene("material3", false), Scene("material3", true),
            Scene("liquid_glass", false), Scene("liquid_glass", true), Scene("lumi", false, true))) {
            compose.runOnIdle { scene.value = current }
            compose.waitForIdle()
            val tabs = compose.onNodeWithTag("replacement-help-tabs").assertHeightIsEqualTo(48.dp)
            val back = compose.onNodeWithContentDescription("返回").assertHeightIsEqualTo(48.dp)
            val tabsBounds = tabs.fetchSemanticsNode().boundsInRoot
            val backBounds = back.fetchSemanticsNode().boundsInRoot
            assertEquals(backBounds.top, tabsBounds.top, 0.5f)
            assertEquals(backBounds.bottom, tabsBounds.bottom, 0.5f)
            compose.onNodeWithText("教程", useUnmergedTree = false).assertIsSelected()
            screenshot(view, "${current.theme}-${current.dark}-${current.ink}-tutorial")

            compose.onNodeWithText("AI 提示词", useUnmergedTree = false).performClick()
            compose.onNodeWithText("第 1 步：截取想要替换的片段").assertIsDisplayed()
            compose.onNodeWithText("复制完整提示词").performScrollTo().performClick()
            val context = ApplicationProvider.getApplicationContext<Application>()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val expected = context.resources.openRawResource(R.raw.replacement_regex_ai_prompt)
                .bufferedReader().use { it.readText() }
            compose.runOnIdle { assertEquals(expected, clipboard.primaryClip!!.getItemAt(0).text.toString()) }
            compose.onNodeWithText("已复制，可发送给 AI").assertIsDisplayed()
            screenshot(view, "${current.theme}-${current.dark}-${current.ink}-ai")

            compose.onNodeWithText("教程", useUnmergedTree = false).performClick()
            compose.onNodeWithText("先理解：查找一类文字，再统一处理").assertIsDisplayed()
        }
    }

    private fun screenshot(view: View, name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/replacement-help/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
