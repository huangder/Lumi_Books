package com.huangder.lumibooks.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassMenuMorphTest {
    private val anchor = Rect(left = 800f, top = 120f, right = 844f, bottom = 164f)
    private val menuWidthPx = 196f
    private val hostWidthPx = 1080f
    private val hostHeightPx = 2400f
    private val marginPx = 8f
    private val gapPx = 6f
    private val squashEnd = LiquidGlassMenuMorph.SquashEnd
    private val squashPeak = squashEnd / 2f

    private fun targetRect(
        anchorBounds: Rect = anchor,
        heightPx: Float = LiquidGlassMenuMorph.menuHeightPx(3, 8, 44f, 16f),
        alignEnd: Boolean = true
    ) = LiquidGlassMenuMorph.targetRect(
        anchor = anchorBounds,
        widthPx = menuWidthPx,
        heightPx = heightPx,
        hostWidthPx = hostWidthPx,
        hostHeightPx = hostHeightPx,
        marginPx = marginPx,
        gapPx = gapPx,
        alignEnd = alignEnd
    )

    @Test
    fun `menu height follows the visible row cap`() {
        assertEquals(148f, LiquidGlassMenuMorph.menuHeightPx(3, 8, 44f, 16f), 0.001f)
        assertEquals(368f, LiquidGlassMenuMorph.menuHeightPx(12, 8, 44f, 16f), 0.001f)
        assertEquals(16f, LiquidGlassMenuMorph.menuHeightPx(0, 8, 44f, 16f), 0.001f)
    }

    @Test
    fun `standalone menu grows along the trigger top edge`() {
        val rect = targetRect()

        assertEquals(648f, rect.left, 0.001f)
        assertEquals(anchor.top, rect.top, 0.001f)
        assertEquals(anchor.right, rect.right, 0.001f)
        assertEquals(menuWidthPx, rect.width, 0.001f)
    }

    @Test
    fun `menu flips above the trigger when the bottom would overflow`() {
        val bottomAnchor = Rect(left = 800f, top = 2340f, right = 844f, bottom = 2384f)

        val rect = targetRect(anchorBounds = bottomAnchor)

        assertEquals(bottomAnchor.bottom - 148f, rect.top, 0.001f)
        assertEquals(bottomAnchor.bottom, rect.bottom, 0.001f)
        assertTrue(rect.bottom <= hostHeightPx - marginPx)
    }

    @Test
    fun `menu x stays inside the screen margins`() {
        val edgeAnchor = Rect(left = 0f, top = 120f, right = 44f, bottom = 164f)

        assertEquals(marginPx, targetRect(anchorBounds = edgeAnchor).left, 0.001f)
        assertEquals(anchor.left, targetRect(alignEnd = false).left, 0.001f)
    }

    @Test
    fun `growth follows a short press without a discontinuity`() {
        assertEquals(0f, LiquidGlassMenuMorph.growthProgress(0f), 0.001f)
        assertEquals(0f, LiquidGlassMenuMorph.growthProgress(LiquidGlassMenuMorph.PressEnd), 0.001f)
        assertEquals(0.5f, LiquidGlassMenuMorph.growthProgress(LiquidGlassMenuMorph.PressEnd + 0.5f * (1f - LiquidGlassMenuMorph.PressEnd)), 0.001f)
        assertEquals(1f, LiquidGlassMenuMorph.growthProgress(1f), 0.001f)

        var previous = 0f
        repeat(11) { step ->
            val value = LiquidGlassMenuMorph.growthProgress(step / 10f)
            assertTrue(value >= previous - 0.0001f)
            previous = value
        }
    }

    @Test
    fun `parabolic squash dips below the trigger and springs back`() {
        assertEquals(1f, LiquidGlassMenuMorph.squashScale(0f), 0.0001f)
        assertEquals(1f, LiquidGlassMenuMorph.squashScale(squashEnd), 0.0001f)
        assertEquals(
            1f - LiquidGlassMenuMorph.SquashAmount,
            LiquidGlassMenuMorph.squashScale(squashPeak),
            0.0001f
        )

        var previous = 1f
        for (step in 1..5) {
            val value = LiquidGlassMenuMorph.squashScale(squashPeak * step / 5f)
            assertTrue("squash should keep shrinking, was $value", value <= previous + 0.0001f)
            previous = value
        }
        previous = 1f - LiquidGlassMenuMorph.SquashAmount
        for (step in 1..5) {
            val value = LiquidGlassMenuMorph.squashScale(squashPeak + squashPeak * step / 5f)
            assertTrue("squash should spring back, was $value", value >= previous - 0.0001f)
            previous = value
        }
    }

    @Test
    fun `morph starts on the trigger rectangle`() {
        val start = LiquidGlassMenuMorph.morphRect(anchor, targetRect(), 0f)

        assertEquals(anchor.left, start.left, 0.001f)
        assertEquals(anchor.top, start.top, 0.001f)
        assertEquals(anchor.right, start.right, 0.001f)
        assertEquals(anchor.bottom, start.bottom, 0.001f)
    }

    @Test
    fun `squashed container stays concentric with the trigger`() {
        val squashed = LiquidGlassMenuMorph.morphRect(anchor, targetRect(), squashPeak, continuous = false)

        assertEquals(anchor.center.x, squashed.center.x, 0.001f)
        assertEquals(anchor.center.y, squashed.center.y, 0.001f)
        assertEquals(anchor.width * 0.88f, squashed.width, 0.01f)
        assertEquals(anchor.height * 0.88f, squashed.height, 0.01f)
    }

    @Test
    fun `container returns to the trigger rectangle when the squash ends`() {
        val recovered = LiquidGlassMenuMorph.morphRect(anchor, targetRect(), squashEnd, continuous = false)

        assertEquals(anchor.left, recovered.left, 0.001f)
        assertEquals(anchor.top, recovered.top, 0.001f)
        assertEquals(anchor.right, recovered.right, 0.001f)
        assertEquals(anchor.bottom, recovered.bottom, 0.001f)
    }

    @Test
    fun `morph settles exactly on the menu rectangle`() {
        val target = targetRect()

        val end = LiquidGlassMenuMorph.morphRect(anchor, target, 1f)

        assertEquals(target.left, end.left, 0.001f)
        assertEquals(target.top, end.top, 0.001f)
        assertEquals(target.right, end.right, 0.001f)
        assertEquals(target.bottom, end.bottom, 0.001f)
    }

    @Test
    fun `anchored edge settles long before the growing edge`() {
        val target = targetRect().translate(Offset(0f, 30f))
        val midway = LiquidGlassMenuMorph.PressEnd + 0.5f * (1f - LiquidGlassMenuMorph.PressEnd)

        val rect = LiquidGlassMenuMorph.morphRect(anchor, target, midway)
        val anchoredTravel = (rect.top - anchor.top) / (target.top - anchor.top)
        val growingTravel = (rect.bottom - anchor.bottom) / (target.bottom - anchor.bottom)

        assertTrue("anchored edge should be nearly settled, was $anchoredTravel", anchoredTravel > 0.7f)
        assertTrue("growing edge should still be travelling, was $growingTravel", growingTravel < 0.52f)
    }

    @Test
    fun `container grows monotonically once the squash is over`() {
        val target = targetRect()
        var previous = LiquidGlassMenuMorph.morphRect(anchor, target, squashEnd)

        for (step in 1..10) {
            val progress = squashEnd + (1f - squashEnd) * step / 10f
            val rect = LiquidGlassMenuMorph.morphRect(anchor, target, progress)
            assertTrue(rect.top >= previous.top - 0.001f)
            assertTrue(rect.bottom >= previous.bottom - 0.001f)
            assertTrue(rect.left <= previous.left + 0.001f)
            assertTrue(rect.right >= previous.right - 0.001f)
            assertTrue(rect.width > 0f)
            assertTrue(rect.height > 0f)
            previous = rect
        }
    }

    @Test
    fun `spring overshoot carries past the settled rectangle without running away`() {
        val target = targetRect()

        val overshoot = LiquidGlassMenuMorph.morphRect(anchor, target, 1.03f)
        assertTrue(overshoot.bottom > target.bottom)
        assertTrue(overshoot.left < target.left)

        val clamped = LiquidGlassMenuMorph.morphRect(anchor, target, 1.5f)
        assertTrue(clamped.bottom <= target.bottom + target.height * 0.11f)
    }

    @Test
    fun `touch impulse covers the whole squash phase`() {
        assertEquals(1f, LiquidGlassMenuMorph.touchImpulse(0f), 0.001f)
        assertEquals(0.5f, LiquidGlassMenuMorph.touchImpulse(squashPeak), 0.001f)
        assertEquals(0f, LiquidGlassMenuMorph.touchImpulse(squashEnd), 0.001f)
        assertEquals(0f, LiquidGlassMenuMorph.touchImpulse(0.8f), 0.001f)
    }

    @Test
    fun `container fades in quickly and stays opaque afterwards`() {
        assertEquals(0f, LiquidGlassMenuMorph.containerAlpha(0f), 0.001f)
        assertEquals(
            1f,
            LiquidGlassMenuMorph.containerAlpha(LiquidGlassMenuMorph.FadeInEnd),
            0.001f
        )
        assertEquals(1f, LiquidGlassMenuMorph.containerAlpha(1f), 0.001f)
        assertTrue(
            LiquidGlassMenuMorph.containerAlpha(0.05f) <
                LiquidGlassMenuMorph.containerAlpha(0.1f)
        )
    }

    @Test
    fun `glass starts blurry and sharpens as it settles`() {
        assertEquals(
            LiquidGlassMenuMorph.BlurBoostDp,
            LiquidGlassMenuMorph.blurBoostDp(0f),
            0.001f
        )
        assertEquals(0f, LiquidGlassMenuMorph.blurBoostDp(1f), 0.001f)
        assertTrue(LiquidGlassMenuMorph.blurBoostDp(0.4f) > LiquidGlassMenuMorph.blurBoostDp(0.7f))
    }

    @Test
    fun `corner radius morphs from the trigger radius to the menu radius`() {
        assertEquals(22f, LiquidGlassMenuMorph.cornerRadiusPx(22f, 24f, 0f), 0.001f)
        assertEquals(23f, LiquidGlassMenuMorph.cornerRadiusPx(22f, 24f, 0.5f), 0.001f)
        assertEquals(24f, LiquidGlassMenuMorph.cornerRadiusPx(22f, 24f, 1f), 0.001f)
        assertTrue(LiquidGlassMenuMorph.cornerRadiusPx(22f, 24f, 1.03f) > 24f)
    }

    @Test
    fun `squashing trigger keeps its own corner radius`() {
        assertEquals(
            16f,
            LiquidGlassMenuMorph.cornerRadiusPx(
                sourceRadiusPx = 16f,
                targetRadiusPx = 24f,
                growthProgress = LiquidGlassMenuMorph.growthProgress(squashPeak, continuous = false)
            ),
            0.001f
        )
    }

    @Test
    fun `unshaped triggers fall back to a capsule radius`() {
        assertEquals(22f, LiquidGlassMenuMorph.capsuleCornerRadiusPx(Rect(0f, 0f, 44f, 44f)), 0.001f)
        assertEquals(18f, LiquidGlassMenuMorph.capsuleCornerRadiusPx(Rect(0f, 0f, 158f, 36f)), 0.001f)
    }

    @Test
    fun `contents surface early and become clear before settling`() {
        assertEquals(0f, LiquidGlassMenuMorph.contentProgress(0f), 0.001f)
        assertEquals(0f, LiquidGlassMenuMorph.contentProgress(0.15f), 0.001f)
        assertTrue(LiquidGlassMenuMorph.contentProgress(0.35f) > 0f)
        assertEquals(0.5f, LiquidGlassMenuMorph.contentProgress(0.5f), 0.001f)
        assertEquals(1f, LiquidGlassMenuMorph.contentProgress(0.85f), 0.001f)
        assertEquals(1f, LiquidGlassMenuMorph.contentProgress(1f), 0.001f)
    }

    @Test
    fun `every row surfaces together instead of queueing up`() {
        val contentProgress = 0.5f
        val count = 8
        val reveals = (0 until count).map {
            LiquidGlassMenuMorph.itemRevealProgress(contentProgress, it, count)
        }

        assertTrue(reveals.zipWithNext().all { (first, second) -> first >= second - 0.0001f })
        assertTrue(reveals.all { it in 0f..1f })
        assertTrue(reveals.first() - reveals.last() <= 0.25f)
        assertEquals(1f, LiquidGlassMenuMorph.itemRevealProgress(1f, 7, count), 0.001f)
    }

    @Test
    fun `single item menus have no reveal spread`() {
        assertEquals(0.4f, LiquidGlassMenuMorph.itemRevealProgress(0.4f, 0, 1), 0.001f)
    }

    @Test
    fun `rows travel and scale into place`() {
        assertEquals(LiquidGlassMenuMorph.ItemTravelDp, LiquidGlassMenuMorph.itemTravelPx(0f), 0.001f)
        assertEquals(0f, LiquidGlassMenuMorph.itemTravelPx(1f), 0.001f)
        assertEquals(
            LiquidGlassMenuMorph.ItemTravelDp / 2f,
            LiquidGlassMenuMorph.itemTravelPx(0.5f),
            0.001f
        )
        assertEquals(LiquidGlassMenuMorph.ItemStartScale, LiquidGlassMenuMorph.itemScale(0f), 0.001f)
        assertEquals(1f, LiquidGlassMenuMorph.itemScale(1f), 0.001f)
        assertEquals(1f, LiquidGlassMenuMorph.itemScale(0.5f), 0.001f)
    }

    @Test
    fun `content group un-blurs as it surfaces`() {
        assertEquals(LiquidGlassMenuMorph.ContentBlurDp, LiquidGlassMenuMorph.contentBlurDp(0f), 0.001f)
        assertEquals(0f, LiquidGlassMenuMorph.contentBlurDp(1f), 0.001f)
        assertTrue(
            LiquidGlassMenuMorph.contentBlurDp(0.2f) > LiquidGlassMenuMorph.contentBlurDp(0.8f)
        )
    }

    @Test
    fun `same anchor resumes the current shape`() {
        val reopened = Rect(left = 800.5f, top = 120.4f, right = 844f, bottom = 164f)

        assertTrue(LiquidGlassMenuMorph.keepsCurrentProgress(anchor, reopened, 0.5f))
        assertFalse(LiquidGlassMenuMorph.keepsCurrentProgress(anchor, reopened, 0f))
        assertFalse(LiquidGlassMenuMorph.keepsCurrentProgress(null, anchor, 0.5f))
        assertFalse(
            LiquidGlassMenuMorph.keepsCurrentProgress(
                anchor,
                Rect(left = 120f, top = 400f, right = 164f, bottom = 444f),
                0.5f
            )
        )
    }

    @Test
    fun `toggling the same trigger opens and then closes its menu`() {
        val host = LiquidGlassMenuHostState()
        val spec = LiquidGlassMenuSpec(anchorBounds = anchor, width = 196.dp, items = emptyList())

        host.toggle(spec)
        assertTrue(host.isActive(spec))

        host.toggle(spec.copy())
        assertNull(host.activeMenu)
    }

    @Test
    fun `new morph compresses the actual trigger then expands continuously`() {
        val pressEnd = LiquidGlassMenuMorph.PressEnd
        val pressed = LiquidGlassMenuMorph.morphRect(anchor, targetRect(), pressEnd / 2f)
        assertEquals(anchor.width * 0.9f, pressed.width, 0.001f)
        assertEquals(anchor.center, pressed.center)
        assertEquals(anchor, LiquidGlassMenuMorph.morphRect(anchor, targetRect(), pressEnd))
        for (step in 0..100) {
            val rect = LiquidGlassMenuMorph.morphRect(anchor, targetRect(), pressEnd + (1f - pressEnd) * step / 100f)
            assertTrue(rect.width >= anchor.width)
            assertTrue(rect.height >= anchor.height)
        }
        assertEquals(1f, LiquidGlassMenuMorph.sourceAlpha(0f), 0f)
        assertEquals(1f, LiquidGlassMenuMorph.sourceAlpha(pressEnd / 2f), 0f)
        assertEquals(0f, LiquidGlassMenuMorph.sourceAlpha(0.6f), 0f)
    }

    @Test
    fun `opening spring visibly overshoots then returns to the target`() {
        val animation = TargetBasedAnimation(LiquidGlassMenuMotion.Default.openSpec(), Float.VectorConverter, 0f, 1f)
        val samples = (0..160).map { animation.getValueFromNanos(it * 8_000_000L) }
        val peak = samples.max()
        val peakMillis = samples.indexOf(peak) * 8
        assertTrue("leave time to read the press and expansion: " + peakMillis, peakMillis in 300..400)
        assertTrue("opening must visibly overshoot: " + peak, peak > 1.05f)
        assertTrue("overshoot must stay controlled: " + peak, peak < 1.12f)
        assertTrue(samples.drop(samples.indexOf(peak)).zipWithNext().any { (first, next) -> next < first })
        assertEquals(1f, samples.last(), 0.001f)
    }

    @Test
    fun `physical near edge alignment does not mirror in RTL`() {
        val alignment = LiquidGlassMenuMorph.nearAlignment(anchor, targetRect())
        val contentSize = IntSize(196, 148)
        val containerSize = IntSize(44, 44)
        assertEquals(
            alignment.align(contentSize, containerSize, LayoutDirection.Ltr),
            alignment.align(contentSize, containerSize, LayoutDirection.Rtl)
        )
    }

    @Test
    fun `embedded menus finish above their own local trigger`() {
        val triggers = listOf(Rect(160f, 720f, 196f, 756f), Rect(210f, 720f, 262f, 756f), Rect(280f, 720f, 310f, 756f))
        triggers.forEach { trigger ->
            val target = LiquidGlassMenuMorph.targetRect(trigger, 192f, 210f, 400f, 800f, 8f, 6f,
                alignEnd = true, overlapAnchor = false, preferAbove = true)
            assertEquals(trigger.top - 6f, target.bottom, 0.001f)
            assertEquals(trigger, LiquidGlassMenuMorph.morphRect(trigger, target, 0f))
            assertEquals(target, LiquidGlassMenuMorph.morphRect(trigger, target, 1f))
        }
    }

    @Test
    fun `oversized menus stay inside a small host`() {
        val target = LiquidGlassMenuMorph.targetRect(anchor, 500f, 1200f, 320f, 480f, 8f, 6f, true)
        assertTrue(target.left >= 8f && target.top >= 8f)
        assertTrue(target.right <= 312f && target.bottom <= 472f)
    }

    @Test
    fun `window anchors are converted into the current dialog host`() {
        assertEquals(Rect(20f, 30f, 64f, 74f),
            LiquidGlassMenuMorph.windowToHost(Rect(120f, 230f, 164f, 274f), Offset(100f, 200f)))
    }

    @Test
    fun `stable source identity survives a layout change and reverse toggle`() {
        val host = LiquidGlassMenuHostState()
        val id = Any()
        val first = LiquidGlassMenuSpec(anchor, 196.dp, emptyList(), sourceId = id)
        host.toggle(first)
        host.toggle(first.copy(anchorBounds = anchor.translate(Offset(12f, 50f))))
        assertNull(host.activeMenu)
        host.toggle(first)
        assertTrue(host.isActive(first))
    }

    @Test
    fun `selection and repeated dismiss submit only once`() {
        val host = LiquidGlassMenuHostState()
        var dismissed = 0
        var selected = 0
        val spec = LiquidGlassMenuSpec(anchor, 196.dp, emptyList(), sourceId = Any(), onDismiss = { dismissed++ })
        host.show(spec)
        assertTrue(host.select(spec) { selected++ })
        assertFalse(host.select(spec) { selected++ })
        host.dismiss()
        assertEquals(1, dismissed)
        assertEquals(1, selected)
    }

    @Test
    fun `closing keeps ownership until the rendered transition completes`() {
        val host = LiquidGlassMenuHostState()
        val id = Any()
        val spec = LiquidGlassMenuSpec(anchor, 196.dp, emptyList(), sourceId = id)
        host.show(spec)
        host.displayedMenu = spec
        host.drawingSourceId = id
        host.dismiss()
        assertTrue(host.ownsDrawing(id))
        assertEquals(spec, host.displayedMenu)
        host.toggle(spec)
        assertTrue(host.sameSource(host.displayedMenu, host.activeMenu!!))
    }

    @Test
    fun `a second trigger replaces the first menu and dismisses it`() {
        val host = LiquidGlassMenuHostState()
        var dismissedFirst = 0
        val first = LiquidGlassMenuSpec(
            anchorBounds = anchor,
            width = 196.dp,
            items = emptyList(),
            onDismiss = { dismissedFirst++ }
        )
        val secondAnchor = Rect(left = 120f, top = 400f, right = 164f, bottom = 444f)
        val second = LiquidGlassMenuSpec(
            anchorBounds = secondAnchor,
            width = 196.dp,
            items = emptyList()
        )

        host.show(first)
        host.toggle(second)

        assertEquals(1, dismissedFirst)
        assertTrue(host.isActive(second))
        assertFalse(host.isActive(first))
    }
}
