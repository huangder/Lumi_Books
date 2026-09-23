package com.huangder.lumibooks.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LumiEasterEgg
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

enum class LumiBackgroundScene { HOME, BOOKSHELF, SECONDARY }

val LocalLumiBackgroundPresent = staticCompositionLocalOf { false }
val LocalLumiBackgroundBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Only this sibling is recorded for glass; cards can never sample their own render nodes. */
@Composable
fun LumiBackgroundHost(
    scene: LumiBackgroundScene,
    modifier: Modifier = Modifier,
    bookshelfHeaderBottom: Float = 0f,
    backgroundBackdrop: LayerBackdrop? = null,
    content: @Composable BoxScope.() -> Unit
) {
    if (LocalAppThemeVariant.current != LumiEasterEgg.THEME) {
        Box(modifier, content = content)
        return
    }
    val localBackdrop = rememberLayerBackdrop()
    val backdrop = backgroundBackdrop ?: localBackdrop
    val glass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    Box(modifier) {
        LumiArtwork(
            scene = scene,
            bookshelfHeaderBottom = bookshelfHeaderBottom,
            modifier = Modifier.matchParentSize().then(if (glass) Modifier.layerBackdrop(backdrop) else Modifier)
        )
        CompositionLocalProvider(
            LocalLumiBackgroundPresent provides true,
            LocalLumiBackgroundBackdrop provides backdrop.takeIf { glass }
        ) { content() }
    }
}

@Composable
private fun LumiArtwork(scene: LumiBackgroundScene, bookshelfHeaderBottom: Float, modifier: Modifier) {
    val mainImage = ImageBitmap.imageResource(R.drawable.lumi_chan_background)
    val secondaryImage = ImageBitmap.imageResource(R.drawable.lumi_chan_secondary_background)
    val density = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(density).toFloat()
    val safeBottom = WindowInsets.safeDrawing.getBottom(density).toFloat()
    val motion = LocalMotionEnabled.current
    val background = AppColors.WindowBg
    BoxWithConstraints(modifier.background(background).clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        // Reserve the regular shelf header before it is composed. Its measured edge
        // can grow with accessibility text sizes; every main page uses that same size.
        val headerBottom = maxOf(bookshelfHeaderBottom, safeTop + 220f * density.density)
        val geometry = lumiMainPlacement(width, height, safeTop, safeBottom, density.density, headerBottom)
        val y = rememberLumiMainPosition(
            scene, geometry, motion,
            viewportKey = listOf(width, height, safeTop, safeBottom, density.density)
        )
        val secondaryAlpha by animateFloatAsState(
            targetValue = if (scene == LumiBackgroundScene.SECONDARY) 1f else 0f,
            animationSpec = if (motion) tween(180) else snap(),
            label = "lumiSecondaryBackground"
        )
        val secondary = lumiSecondaryPlacement(width, height, safeTop, safeBottom, density.density)
        Canvas(Modifier.fillMaxSize()) {
            if (secondaryAlpha < 1f && geometry.width > 0f) {
                drawImage(
                    image = mainImage,
                    dstOffset = IntOffset(geometry.x.roundToInt(), y.value.roundToInt()),
                    dstSize = IntSize(geometry.width.roundToInt(), geometry.width.roundToInt()),
                    alpha = 1f - secondaryAlpha
                )
            }
            if (secondaryAlpha > 0f) {
                drawImage(
                    image = secondaryImage,
                    dstOffset = IntOffset(secondary.x.roundToInt(), secondary.y.roundToInt()),
                    dstSize = IntSize(secondary.width.roundToInt(), secondary.height.roundToInt()),
                    alpha = secondaryAlpha
                )
            }
        }
    }
}

/** The Canvas and every glass sampler share this single animated position. */
@Composable
internal fun rememberLumiMainPosition(
    scene: LumiBackgroundScene,
    geometry: LumiMainPlacement,
    motion: Boolean,
    viewportKey: Any
): androidx.compose.runtime.State<Float> {
    var lastMainScene by remember { mutableStateOf(scene) }
    if (scene != LumiBackgroundScene.SECONDARY) lastMainScene = scene
    val targetY = if (lastMainScene == LumiBackgroundScene.BOOKSHELF) geometry.bottomY else geometry.topY
    // Geometry changes (rotation/insets) and a newly mounted host start at the target.
    val y = remember(viewportKey) { Animatable(targetY) }
    val previousScene = remember { arrayOf(scene) }
    LaunchedEffect(y, targetY, scene, motion) {
        val crossfade = previousScene[0] == LumiBackgroundScene.SECONDARY || scene == LumiBackgroundScene.SECONDARY
        previousScene[0] = scene
        if (motion && !crossfade) y.animateTo(targetY, tween(450, easing = FastOutSlowInEasing))
        else y.snapTo(targetY)
    }
    return y.asState()
}
