package com.huangder.lumibooks.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.UnevenRoundedRectangle

/**
 * The app's shared shape factory.  Shapes keeps the existing corner dimensions,
 * while its continuous style removes the curvature discontinuity at each edge.
 */
object AppShapes {
    fun usesContinuousCorners(theme: String): Boolean = theme != "material3"

    fun rounded(radius: Dp, continuous: Boolean = true): CornerBasedShape =
        from(RoundedCornerShape(radius), continuous)

    /** Int overload follows Compose: 50 is a capsule, not 50 dp. */
    fun rounded(percent: Int, continuous: Boolean = true): CornerBasedShape =
        from(RoundedCornerShape(percent), continuous)

    /** Float overload follows Compose's pixel corner size. */
    fun rounded(radius: Float, continuous: Boolean = true): CornerBasedShape =
        from(RoundedCornerShape(radius), continuous)

    fun rounded(
        topStart: Dp = 0.dp,
        topEnd: Dp = 0.dp,
        bottomEnd: Dp = 0.dp,
        bottomStart: Dp = 0.dp,
        continuous: Boolean = true
    ): CornerBasedShape = from(RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart), continuous)

    fun from(shape: CornerBasedShape, continuous: Boolean): CornerBasedShape = if (continuous) {
        ContinuousCornerBasedShape(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart)
    } else shape

    fun material(continuous: Boolean): Shapes = if (continuous) ContinuousMaterialShapes else StandardMaterialShapes

    private val StandardMaterialShapes = Shapes()
    private val ContinuousMaterialShapes = StandardMaterialShapes.let {
        Shapes(from(it.extraSmall, true), from(it.small, true), from(it.medium, true),
            from(it.large, true), from(it.extraLarge, true))
    }
}

@Composable
fun AppRoundedCornerShape(size: Dp): CornerBasedShape =
    AppShapes.rounded(size, AppShapes.usesContinuousCorners(LocalAppTheme.current))

@Composable
fun AppRoundedCornerShape(percent: Int): CornerBasedShape =
    AppShapes.rounded(percent, AppShapes.usesContinuousCorners(LocalAppTheme.current))

@Composable
fun AppRoundedCornerShape(size: Float): CornerBasedShape =
    AppShapes.rounded(size, AppShapes.usesContinuousCorners(LocalAppTheme.current))

@Composable
fun AppRoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp
): CornerBasedShape = AppShapes.rounded(topStart, topEnd, bottomEnd, bottomStart,
    AppShapes.usesContinuousCorners(LocalAppTheme.current))

/** A CornerBasedShape facade backed by Shapes' iOS continuous-curvature outline. */
@Immutable
internal class ContinuousCornerBasedShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        // The base class has already resolved CornerSize to pixels and normalized
        // competing corners. A unit density preserves those pixels in Shapes' Dp API.
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        }
        val uniform = topStart == topEnd && topEnd == bottomEnd && bottomEnd == bottomStart
        val shape = when {
            uniform && topStart >= size.minDimension / 2f -> Capsule()
            uniform -> RoundedRectangle(topStart.dp)
            else -> UnevenRoundedRectangle(topStart.dp, topEnd.dp, bottomEnd.dp, bottomStart.dp)
        }
        return shape.createOutline(size, layoutDirection, Density(1f))
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = ContinuousCornerBasedShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun equals(other: Any?): Boolean = other is ContinuousCornerBasedShape &&
        topStart == other.topStart && topEnd == other.topEnd &&
        bottomEnd == other.bottomEnd && bottomStart == other.bottomStart

    override fun hashCode(): Int = (((topStart.hashCode() * 31 + topEnd.hashCode()) * 31 +
        bottomEnd.hashCode()) * 31 + bottomStart.hashCode())
}
