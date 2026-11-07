package com.huangder.lumibooks.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class ControlIconTest {
    @Test fun outlinesAreCachedAndOriginalVectorsRemainUnchanged() {
        listOf(AppIcons.ArrowLeft, AppIcons.Plus, AppIcons.MagnifyingGlass,
            AppIcons.SortAscending, AppIcons.CaretDown, AppIcons.Bookmark.regular, AppIcons.Tag).forEach { original ->
            val root = original.root
            val weighted = controlIcon(original)
            assertNotSame(original, weighted)
            assertSame(weighted, controlIcon(original))
            assertSame(weighted, controlIcon(weighted))
            assertSame(root, original.root)
            assertEquals(original.defaultWidth, weighted.defaultWidth)
            assertEquals(original.defaultHeight, weighted.defaultHeight)
        }
    }

    @Test fun filledAndUnregisteredIconsKeepTheirIdentity() {
        listOf(AppIcons.Play, AppIcons.Pause, AppIcons.Warning, AppIcons.CheckCircle,
            AppIcons.Heart.filled, AppIcons.Bookmark.filled, AppIcons.HomeTab.regular,
            AppIcons.Tile.Folders).forEach { assertSame(it, controlIcon(it)) }
        val custom = ImageVector.Builder("Custom", 24.dp, 24.dp, 24f, 24f).build()
        assertSame(custom, controlIcon(custom))
    }

    @Test fun groupsFillRulesTransparencyAndMirroringSurviveWeighting() {
        val original = ImageVector.Builder("Grouped", 48.dp, 48.dp, 48f, 48f,
            tintColor = Color.Red, autoMirror = true).apply {
            addGroup(name = "rotated", rotate = 15f, pivotX = 24f, pivotY = 24f,
                scaleX = .8f, scaleY = .9f, translationX = 1f, translationY = 2f)
            path(fill = SolidColor(Color.Black), fillAlpha = .4f, pathFillType = PathFillType.EvenOdd) {
                moveTo(8f, 8f); lineTo(40f, 8f); lineTo(24f, 40f); close()
            }
            clearGroup()
        }.build()
        val weighted = heavierControlVector(original)
        assertTrue(weighted.autoMirror)
        assertEquals(original.tintColor, weighted.tintColor)
        assertEquals(original.tintBlendMode, weighted.tintBlendMode)
        val oldGroup = original.root[0] as VectorGroup
        val group = (weighted.root[0] as VectorGroup)[0] as VectorGroup
        assertEquals(oldGroup.rotation, group.rotation, 0f)
        assertEquals(oldGroup.pivotX, group.pivotX, 0f)
        assertEquals(oldGroup.pivotY, group.pivotY, 0f)
        assertEquals(oldGroup.scaleX, group.scaleX, 0f)
        assertEquals(oldGroup.scaleY, group.scaleY, 0f)
        assertEquals(oldGroup.translationX, group.translationX, 0f)
        assertEquals(oldGroup.translationY, group.translationY, 0f)
        assertEquals(oldGroup.clipPathData, group.clipPathData)
        val oldPath = oldGroup[0] as VectorPath
        val path = group[0] as VectorPath
        assertSame(oldPath.pathData, path.pathData)
        assertEquals(oldPath.pathFillType, path.pathFillType)
        assertEquals(oldPath.fill, path.fill)
        assertEquals(.4f, path.fillAlpha, 0f)
        assertEquals(.4f, path.strokeAlpha, 0f)
        assertEquals(.6f, path.strokeLineWidth, .0001f)
        assertEquals(StrokeCap.Round, path.strokeLineCap)
        assertEquals(StrokeJoin.Round, path.strokeLineJoin)
        assertNull(oldPath.stroke)
    }

    @Test fun trueStrokesGainWeightWithoutLosingTrimOrOpacity() {
        val original = ImageVector.Builder("Stroke", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(pathData = listOf(PathNode.MoveTo(4f, 12f), PathNode.LineTo(20f, 12f)),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeAlpha = .6f, trimPathStart = .1f, trimPathEnd = .9f, trimPathOffset = .2f)
        }.build()
        val path = (heavierControlVector(original).root[0] as VectorGroup)[0] as VectorPath
        assertEquals(1.8f, path.strokeLineWidth, .0001f)
        assertEquals(.6f, path.strokeAlpha, 0f)
        assertEquals(.1f, path.trimPathStart, 0f)
        assertEquals(.9f, path.trimPathEnd, 0f)
        assertEquals(.2f, path.trimPathOffset, 0f)
        assertNull(path.fill)
    }
}
