package com.huangder.lumibooks.ui.animation

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class PagePushWindowTest {
    private fun activity(page: Boolean = false, restored: Boolean = false): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java, Intent()).create().get()
        activity.setTheme(if (page) R.style.Theme_EBookReader_Page else R.style.Theme_EBookReader)
        shadowOf(activity).setIsTaskRoot(!page)
        PagePushTransitions.onActivityCreated(activity, if (restored) Bundle() else null)
        PagePushTransitions.onActivityStarted(activity)
        return activity
    }

    @Test fun openingSharesOneProgressWithItsParentInsteadOfStartingTwoAnimations() {
        val parent = activity()
        PagePushTransitions.onActivityResumed(parent)
        val child = activity(page = true)
        val scene = PagePushTransitions.scene(child)!!
        if (Build.VERSION.SDK_INT >= 30) {
            assertTrue(scene.moving)
            assertEquals(0f, scene.progress, 0f)
            assertSame(scene, PagePushTransitions.scene(parent)!!.coveredBy)
            for (i in 0..100) {
                scene.progress = i / 100f
                assertEquals(1f - i / 100f, pagePushOffset(scene.progress, false), 0.0001f)
                assertEquals(-0.25f * i / 100f, pagePushOffset(scene.progress, true), 0.0001f)
            }
        }
        PagePushTransitions.onActivityDestroyed(child)
        assertNull(PagePushTransitions.scene(parent)!!.coveredBy)
        PagePushTransitions.onActivityDestroyed(parent)
    }

    @Test fun restoredPageDoesNotReplayEntrance() {
        val parent = activity()
        PagePushTransitions.onActivityResumed(parent)
        val child = activity(page = true, restored = true)
        assertFalse(PagePushTransitions.scene(child)!!.moving)
        assertEquals(1f, PagePushTransitions.scene(child)!!.progress, 0f)
        PagePushTransitions.onActivityDestroyed(child)
        PagePushTransitions.onActivityDestroyed(parent)
    }

    @Test fun independentTaskDoesNotUseAnUnrelatedPausedPageAsItsParent() {
        val root = activity()
        assertFalse(PagePushTransitions.scene(root)!!.moving)
        PagePushTransitions.onActivityResumed(root)
        val child = activity(page = true)
        PagePushTransitions.onActivityDestroyed(child)
        PagePushTransitions.onActivityDestroyed(root)
        val next = activity(page = true)
        assertFalse(PagePushTransitions.scene(next)!!.moving)
        PagePushTransitions.onActivityDestroyed(next)
    }

    @Test fun motionPreferenceIsImmediatelyAvailableToOpeningAndClosingHost() {
        val root = activity()
        PagePushTransitions.onActivityResumed(root)
        val child = activity(page = true)
        PagePushTransitions.updateMotion(child, MotionPreference.FAST, false)
        assertEquals(PagePushSpeed.FAST, PagePushTransitions.scene(child)!!.speed)
        PagePushTransitions.updateMotion(child, MotionPreference.STANDARD, true)
        assertEquals(PagePushSpeed.NONE, PagePushTransitions.scene(child)!!.speed)
        PagePushTransitions.onActivityDestroyed(child)
        PagePushTransitions.onActivityDestroyed(root)
    }

    @Test fun allFourDeviceCornersArePreservedIndependently() {
        val corners = PageCorners(50f, 51f, 60f, 61f)
        val outline = corners.createOutline(Size(1000f, 2000f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
        assertEquals(50f, outline.roundRect.topLeftCornerRadius.x, 0f)
        assertEquals(51f, outline.roundRect.topRightCornerRadius.x, 0f)
        assertEquals(60f, outline.roundRect.bottomRightCornerRadius.x, 0f)
        assertEquals(61f, outline.roundRect.bottomLeftCornerRadius.x, 0f)
    }

    @Test fun secondaryWindowWithMissingOrZeroCornersInheritsTheDeviceRadius() {
        assertEquals(72f, resolvedPageRadius(0f, 72f, 32f), 0f)
        assertEquals(72f, resolvedPageRadius(null, 72f, 32f), 0f)
        assertEquals(64f, resolvedPageRadius(64f, 72f, 32f), 0f)
        assertEquals(32f, resolvedPageRadius(0f, null, 32f), 0f)
    }

    @Test fun expandedShadowKeepsEachCornerCenteredOnThePageCorner() {
        val corners = PageCorners(50f, 51f, 60f, 61f)
        val spread = 24f
        val shadow = corners.expanded(spread).createOutline(
            Size(1000f + spread * 2, 2000f + spread * 2), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
        // The shadow is drawn at (-spread, -spread), so its curved corner centers
        // coincide with the page instead of leaving a vertical rectangular strip.
        assertEquals(corners.topLeft, shadow.roundRect.topLeftCornerRadius.x - spread, 0f)
        assertEquals(corners.bottomLeft, shadow.roundRect.bottomLeftCornerRadius.x - spread, 0f)
        assertEquals(corners.topRight, shadow.roundRect.topRightCornerRadius.x - spread, 0f)
        assertEquals(corners.bottomRight, shadow.roundRect.bottomRightCornerRadius.x - spread, 0f)
    }

    @Test fun standardAndFastTimingMatchTheWindowFallback() {
        assertEquals(560, PAGE_PUSH_DURATION)
        assertEquals(380, PAGE_PUSH_FAST_DURATION)
        assertTrue(PagePushEasing.transform(0.25f) < 0.8f)
        assertEquals(1f, PagePushEasing.transform(1f), 0.0001f)
        val root = activity()
        assertEquals(560L, AnimationUtils.loadAnimation(root, R.anim.lumi_push_open_enter).duration)
        assertEquals(380L, AnimationUtils.loadAnimation(root, R.anim.lumi_push_open_enter_fast).duration)
        PagePushTransitions.onActivityDestroyed(root)
    }
}
