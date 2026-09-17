package com.huangder.lumibooks.ui.settings

import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.icons.AppIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.PROGRESSIVE_BLUR_TINT_ALPHA
import com.huangder.lumibooks.ui.components.PROGRESSIVE_BLUR_TINT_ALPHA_DARK
import com.huangder.lumibooks.ui.components.ProgressiveTopBlur
import com.huangder.lumibooks.ui.components.ProgressiveTopScrim
import com.huangder.lumibooks.ui.components.largeTitleAlpha
import com.huangder.lumibooks.ui.components.largeTitleScale
import com.huangder.lumibooks.ui.components.progressiveBlurEnabled
import com.huangder.lumibooks.ui.components.progressiveTopScrimEnabled
import com.huangder.lumibooks.ui.components.quantizeBlurStrength
import com.huangder.lumibooks.ui.components.smallTitleAlpha
import com.huangder.lumibooks.ui.components.smallTitleScale
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.fangSongFamily
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.Backdrop

/** Toolbar row height below the status bar. */
private val SettingsToolbarHeight = 56.dp

/**
 * Extra band height below the toolbar where the blur finishes dissolving into the content. A long
 * tail is what makes the bar read as fog rather than a frosted rectangle.
 */
private val SettingsToolbarBlurTail = 56.dp

/**
 * 折叠式设置页脚手架：内容可以滚到顶栏与状态栏下方，顶栏作为独立前景层始终清晰。
 *
 * 结构固定为「内容捕获层 → 渐进模糊层 → 前景工具条」三层：
 *
 * * 内容层只负责滚动，并额外提供一个仅含滚动内容的 Backdrop 作为模糊采样源（不包含工具条，
 *   避免标题与按钮在模糊层里留下鬼影）；
 * * 模糊层按滚动进度增强，从顶部满强度连续衰减到完全清晰；
 * * 工具条（返回、标题、可选操作）绘制在最上层，永不进入模糊。
 *
 * 大标题随滚动上移、缩小并淡出，小标题同步淡入，两者与模糊强度共用同一个折叠进度。
 */
@Composable
internal fun CollapsingSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val capability = LocalLiquidGlassCapability.current
    val eInkMode = LocalEInkMode.current
    val blurEnabled = progressiveBlurEnabled(capability.supported, eInkMode)
    val scrimEnabled = progressiveTopScrimEnabled(capability.supported, eInkMode)
    val tint = AppColors.WindowBg
    val tintAlpha = if (LocalIsDarkTheme.current) {
        PROGRESSIVE_BLUR_TINT_ALPHA_DARK
    } else {
        PROGRESSIVE_BLUR_TINT_ALPHA
    }

    val scrollState = rememberScrollState()
    val topBlurBackdrop = rememberLayerBackdrop()
    var largeTitleHeight by remember { mutableFloatStateOf(0f) }

    val statusBarTopPadding = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val toolbarTotalHeight = statusBarTopPadding + SettingsToolbarHeight
    val bandHeight = toolbarTotalHeight + SettingsToolbarBlurTail
    val toolbarBottomPx = with(LocalDensity.current) { toolbarTotalHeight.toPx() }

    // 单一进度源：模糊强度与大小标题过渡都读它，二者不会失步。
    val collapseProgress = remember(scrollState, toolbarBottomPx) {
        derivedStateOf {
            detailTitleCollapseFraction(
                titleTop = toolbarBottomPx - scrollState.value,
                titleHeight = largeTitleHeight,
                viewportTop = toolbarBottomPx
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurEnabled) Modifier.layerBackdrop(topBlurBackdrop) else Modifier)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .imePadding()
                    .verticalScroll(scrollState)
            ) {
                Spacer(Modifier.height(toolbarTotalHeight))
                Text(
                    text = title,
                    modifier = Modifier
                        .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm)
                        .onSizeChanged { size -> largeTitleHeight = size.height.toFloat() }
                        .graphicsLayer {
                            // 读取放在绘制阶段，滚动时只失效图层，不触发重组。
                            val progress = collapseProgress.value
                            val scale = largeTitleScale(progress)
                            scaleX = scale
                            scaleY = scale
                            alpha = largeTitleAlpha(progress)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                    fontSize = AppType.Display,
                    fontWeight = FontWeight.Bold,
                    fontFamily = resolveAppFontFamily(fangSongFamily()),
                    color = AppColors.TextPrimary
                )
                content()
                Spacer(Modifier.height(120.dp))
            }
        }

        if (blurEnabled) {
            ProgressiveTopBlurBand(
                progress = collapseProgress,
                backdrop = topBlurBackdrop,
                bandHeight = bandHeight,
                tint = tint,
                tintAlpha = tintAlpha,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } else if (scrimEnabled) {
            ProgressiveTopScrimBand(
                progress = collapseProgress,
                bandHeight = bandHeight,
                tint = tint,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // 前景层：返回、标题与操作永远清晰，且不参与模糊采样。
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .height(SettingsToolbarHeight)
                .padding(horizontal = AppSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassIconButton(
                imageVector = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                settingsBackButton = true
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppSpace.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    fontFamily = resolveAppFontFamily(fangSongFamily()),
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        val progress = collapseProgress.value
                        val scale = smallTitleScale(progress)
                        scaleX = scale
                        scaleY = scale
                        alpha = smallTitleAlpha(progress)
                    }
                )
            }
            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    content = actions
                )
            } else {
                // 与返回按钮等宽，保证标题在屏幕上真正居中。
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

/**
 * 模糊层叶子节点：只把量化后的强度传下去，滚动过程中重组次数被限制在 [quantizeBlurStrength]
 * 的档位数内，页面内容不参与重组。
 */
@Composable
private fun ProgressiveTopBlurBand(
    progress: State<Float>,
    backdrop: Backdrop,
    bandHeight: Dp,
    tint: Color,
    tintAlpha: Float,
    modifier: Modifier = Modifier
) {
    val strength by remember(progress) {
        derivedStateOf { quantizeBlurStrength(progress.value) }
    }
    ProgressiveTopBlur(
        backdrop = backdrop,
        strength = strength,
        modifier = modifier,
        bandHeight = bandHeight,
        tint = tint.copy(alpha = tintAlpha)
    )
}

@Composable
private fun ProgressiveTopScrimBand(
    progress: State<Float>,
    bandHeight: Dp,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val strength by remember(progress) {
        derivedStateOf { quantizeBlurStrength(progress.value) }
    }
    ProgressiveTopScrim(
        strength = strength,
        modifier = modifier,
        bandHeight = bandHeight,
        tint = tint
    )
}
