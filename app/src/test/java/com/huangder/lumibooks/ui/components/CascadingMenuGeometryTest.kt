package com.huangder.lumibooks.ui.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class CascadingMenuGeometryTest {
    private val safe = Rect(8f, 24f, 352f, 616f)

    @Test fun retreatShrinksTheParentAndDimsOnlyItsContent() {
        val parent = Rect(20f, 100f, 220f, 400f)
        assertEquals(parent, CascadingMenuGeometry.retreatBounds(parent, 0f))
        val retreated = CascadingMenuGeometry.retreatBounds(parent, 1f)
        assertEquals(parent.center, retreated.center)
        assertEquals(192f, retreated.width, .001f)
        assertEquals(.60f, CascadingMenuGeometry.parentContentAlpha(1f), .001f)
        assertEquals(1f, CascadingMenuGeometry.parentContentAlpha(0f), .001f)
    }

    @Test fun rowAnchorFollowsBothRetreatAndTheParentsClosingTravel() {
        val row = Rect(6f, 94f, 194f, 138f)
        val open = CascadingMenuGeometry.rowAnchor(Rect(20f, 100f, 220f, 400f), row, 200f, 300f)
        val returning = CascadingMenuGeometry.rowAnchor(Rect(140f, 40f, 240f, 190f), row, 200f, 300f)
        assertEquals(Rect(26f, 194f, 214f, 238f), open)
        assertEquals(open.width / 2f, returning.width, .001f)
        assertTrue(returning.center.y < open.center.y)
    }

    @Test fun childOpensOnAvailableSideAndFlipsAtTheRightEdge() {
        val leftParent = Rect(8f, 60f, 152f, 400f)
        val rightParent = Rect(208f, 60f, 352f, 400f)
        val rightChild = place(leftParent, 160f)
        val leftChild = place(rightParent, 160f)
        assertTrue(rightChild.left >= leftParent.right)
        assertTrue(leftChild.right <= rightParent.left)
    }

    @Test fun narrowWindowsKeepChildOnScreenWithPartialParentOverlap() {
        val parent = Rect(8f, 60f, 224f, 400f)
        val child = place(parent, 192f)
        assertTrue(child.overlaps(parent))
        assertTrue(child.left > parent.left)
        assertTrue(child.left >= safe.left && child.right <= safe.right)
        assertTrue(child.top >= safe.top && child.bottom <= safe.bottom)
    }

    @Test fun childAvoidsThePlaybackControlsAndScrollsWithinItsBoundedHeight() {
        val parent = Rect(8f, 60f, 224f, 400f)
        val controls = Rect(0f, 500f, 360f, 548f)
        val child = CascadingMenuGeometry.targetRect(parent, Rect(14f, 380f, 218f, 424f),
            192f, 360f, safe, 6f, false, controls)
        assertFalse(child.overlaps(controls))
        assertTrue(child.top >= safe.top)
    }

    @Test fun rtlPrefersTheLeftWhenBothSidesAreAvailable() {
        val parent = Rect(400f, 100f, 600f, 500f)
        val wideSafe = Rect(8f, 24f, 992f, 616f)
        val child = CascadingMenuGeometry.targetRect(parent, Rect(406f, 200f, 594f, 244f),
            192f, 200f, wideSafe, 6f, true)
        assertTrue(child.right < parent.left)
    }

    @Test fun parentMovesTowardTheFreeRailToKeepItsLabelsVisible() {
        val parent = Rect(100f, 100f, 276f, 400f)
        val shift = CascadingMenuGeometry.parentShift(parent, 166f, safe, 6f, false)
        val moved = CascadingMenuGeometry.retreatBounds(parent, 1f)
            .translate(androidx.compose.ui.geometry.Offset(shift, 0f))
        val child = CascadingMenuGeometry.targetRect(moved, moved, 166f, 200f, safe, 6f, false)
        assertTrue(moved.left >= safe.left && moved.right <= safe.right)
        assertFalse("Both panes fit side by side on a 360dp screen", child.overlaps(moved))
    }

    private fun place(parent: Rect, width: Float) = CascadingMenuGeometry.targetRect(
        parent, Rect(parent.left + 6f, 200f, parent.right - 6f, 244f),
        width, 200f, safe, 6f, false
    )
}
