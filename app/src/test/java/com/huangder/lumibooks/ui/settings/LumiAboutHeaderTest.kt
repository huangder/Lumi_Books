package com.huangder.lumibooks.ui.settings

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LumiAboutHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fiveClicksUnlockOnlyOnceEvenWhenPersistenceIsSlow() {
        val unlocked = mutableStateOf(false)
        var unlocks = 0
        var completion: ((Boolean) -> Unit)? = null
        compose.setContent {
            LumiAboutHeader(unlocked.value, "1.0", false, {}, {
                unlocks++
                completion = it
            })
        }
        for (remaining in 4 downTo 1) {
            compose.onNodeWithTag("lumi_about_header").performClick()
            assertEquals("麻烦再点击${remaining}次喵", ShadowToast.getTextOfLatestToast())
            assertEquals(0, unlocks)
        }
        repeat(20) { compose.onNodeWithTag("lumi_about_header").performClick() }
        assertEquals(1, unlocks)
        compose.runOnIdle {
            unlocked.value = true
            completion!!.invoke(true)
        }
        compose.waitForIdle()
        assertEquals("彩蛋安置好了喵（*/∇＼*）", ShadowToast.getTextOfLatestToast())
        compose.onNodeWithTag("lumi_about_header").performClick()
        assertEquals(1, unlocks)
        assertEquals("我已经安放好啦！", ShadowToast.getTextOfLatestToast())
    }

    @Test fun updateButtonNeverCountsEvenWhileAnUpdateIsPending() {
        val checking = mutableStateOf(false)
        var checks = 0
        var unlocks = 0
        compose.setContent {
            LumiAboutHeader(false, "1.0", checking.value, { checks++ }, { unlocks++ })
        }
        repeat(5) { compose.onNodeWithTag("lumi_check_update").performClick() }
        assertEquals(5, checks)
        compose.runOnIdle { checking.value = true }
        repeat(5) { compose.onNodeWithTag("lumi_check_update").performClick() }
        assertEquals(5, checks)
        compose.onNodeWithTag("lumi_about_header").performClick()
        assertEquals("麻烦再点击4次喵", ShadowToast.getTextOfLatestToast())
        assertEquals(0, unlocks)
    }

    @Test fun rotationRestoresUnfinishedClicksAndLeavingResetsThem() {
        val visible = mutableStateOf(true)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            if (visible.value) LumiAboutHeader(false, "1.0", false, {}, {})
        }
        repeat(2) { compose.onNodeWithTag("lumi_about_header").performClick() }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("lumi_about_header").performClick()
        assertEquals("麻烦再点击2次喵", ShadowToast.getTextOfLatestToast())
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("lumi_about_header").performClick()
        assertEquals("麻烦再点击4次喵", ShadowToast.getTextOfLatestToast())
    }

    @Test fun previouslyUnlockedHeaderDoesNotTriggerUnlockAgain() {
        var unlocks = 0
        compose.setContent { LumiAboutHeader(true, "1.0", false, {}, { unlocks++ }) }
        repeat(8) {
            compose.onNodeWithTag("lumi_about_header").performClick()
            assertEquals("我已经安放好啦！", ShadowToast.getTextOfLatestToast())
        }
        assertEquals(0, unlocks)
    }
}
