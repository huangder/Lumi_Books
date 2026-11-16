package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import com.huangder.lumibooks.replacement.BookReplacementRules
import com.huangder.lumibooks.replacement.ReplacementProgram
import com.huangder.lumibooks.replacement.ReplacementRule
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h740dp-xxhdpi")
class ReplacementRulesSheetMotionTest {
    @get:Rule val compose = createComposeRule()

    private fun height(): Dp = compose.onNodeWithTag("page").getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun mount(motion: Boolean, eInk: Boolean = false): androidx.compose.runtime.MutableState<ReplacementRule> {
        val rule = mutableStateOf(ReplacementRule("r", find = "小明", replacement = "阿明"))
        compose.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides motion, LocalEInkMode provides eInk) {
                Column(Modifier.fillMaxWidth().testTag("page")) {
                    val current = rule.value
                    ReplacementRuleEditor(current, ReplacementProgram.validate(current), true, { rule.value = it },
                        BookReplacementRules(), 0) { null }
                }
            }
        }
        compose.waitForIdle()
        return rule
    }

    @Test fun deleteNoticeExpandsSmoothly() {
        val rule = mount(motion = true)
        val start = height()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            rule.value = rule.value.copy(replacement = "")
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        val middle = height()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val end = height()
        assertTrue("expected growth, $start -> $end", end > start)
        assertTrue("expected an intermediate height, got $start / $middle / $end", middle > start && middle < end)
    }

    @Test fun eInkSnapsWithoutIntermediateFrames() {
        val rule = mount(motion = true, eInk = true)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            rule.value = rule.value.copy(replacement = "")
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val after = height()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(height(), after)
    }
}
