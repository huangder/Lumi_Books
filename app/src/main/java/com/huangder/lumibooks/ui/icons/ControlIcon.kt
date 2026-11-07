package com.huangder.lumibooks.ui.icons

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import java.util.IdentityHashMap

private val controlIcons = IdentityHashMap<ImageVector, ImageVector>()

/** Slightly heavier outline for controls. Display icons retain their original vector. */
fun controlIcon(icon: ImageVector): ImageVector {
    if (!AppIcons.isOutline(icon)) return icon
    return synchronized(controlIcons) {
        controlIcons.getOrPut(icon) { heavierControlVector(icon) }
    }
}

/** Icon's vector overload, with the shared control weight and unchanged semantics/tint/layout. */
@Composable
fun ControlIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(controlIcon(imageVector), contentDescription, modifier, tint)
}

internal fun heavierControlVector(icon: ImageVector): ImageVector {
    val extraWidth = 0.3f * minOf(icon.viewportWidth, icon.viewportHeight) / 24f
    return ImageVector.Builder(
        name = "${icon.name}.Control",
        defaultWidth = icon.defaultWidth,
        defaultHeight = icon.defaultHeight,
        viewportWidth = icon.viewportWidth,
        viewportHeight = icon.viewportHeight,
        tintColor = icon.tintColor,
        tintBlendMode = icon.tintBlendMode,
        autoMirror = icon.autoMirror
    ).apply { copyControlGroup(icon.root, extraWidth) }.build()
}

private fun ImageVector.Builder.copyControlGroup(group: VectorGroup, extraWidth: Float) {
    addGroup(
        name = group.name,
        rotate = group.rotation,
        pivotX = group.pivotX,
        pivotY = group.pivotY,
        scaleX = group.scaleX,
        scaleY = group.scaleY,
        translationX = group.translationX,
        translationY = group.translationY,
        clipPathData = group.clipPathData
    )
    group.forEach { node ->
        when (node) {
            is VectorGroup -> copyControlGroup(node, extraWidth)
            is VectorPath -> addPath(
                pathData = node.pathData,
                pathFillType = node.pathFillType,
                name = node.name,
                fill = node.fill,
                fillAlpha = node.fillAlpha,
                stroke = node.stroke ?: node.fill,
                strokeAlpha = if (node.stroke != null) node.strokeAlpha else node.fillAlpha,
                strokeLineWidth = if (node.stroke != null) node.strokeLineWidth + extraWidth else extraWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = node.strokeLineMiter,
                trimPathStart = node.trimPathStart,
                trimPathEnd = node.trimPathEnd,
                trimPathOffset = node.trimPathOffset
            )
        }
    }
    clearGroup()
}
