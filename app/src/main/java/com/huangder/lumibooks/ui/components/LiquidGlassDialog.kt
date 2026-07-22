package com.huangder.lumibooks.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class LiquidGlassDialogSpec(
    val id: Any,
    val onDismissRequest: () -> Unit,
    val backdrop: Backdrop?,
    val shape: Shape,
    val modifier: Modifier,
    val alignment: Alignment,
    val contentScrimColor: Color,
    val backgroundScrimColor: Color,
    val backgroundBlurRadius: androidx.compose.ui.unit.Dp,
    val transparencyOverride: Float?,
    val content: @Composable BoxScope.() -> Unit
)

class LiquidGlassDialogHostState internal constructor(
    private val scope: CoroutineScope
) {
    private var clearJob: Job? = null
    private var revealJob: Job? = null
    internal var dialogs by mutableStateOf<List<LiquidGlassDialogSpec>>(emptyList())
        private set
    internal val spec: LiquidGlassDialogSpec?
        get() = dialogs.lastOrNull()
    internal var visible by mutableStateOf(false)
        private set

    internal fun show(dialogSpec: LiquidGlassDialogSpec) {
        clearJob?.cancel()
        val wasEmpty = dialogs.isEmpty()
        val existingIndex = dialogs.indexOfFirst { it.id === dialogSpec.id }
        dialogs = if (existingIndex >= 0) {
            dialogs.toMutableList().also { it[existingIndex] = dialogSpec }
        } else {
            dialogs + dialogSpec
        }
        if (wasEmpty) {
            visible = false
            revealJob?.cancel()
            revealJob = scope.launch {
                delay(16)
                if (dialogs.any { it.id === dialogSpec.id }) visible = true
            }
        } else {
            visible = true
        }
    }

    internal fun hide(id: Any) {
        val index = dialogs.indexOfFirst { it.id === id }
        if (index < 0) return
        if (index != dialogs.lastIndex) {
            dialogs = dialogs.filterNot { it.id === id }
            return
        }

        revealJob?.cancel()
        visible = false
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(260)
            if (!visible && dialogs.lastOrNull()?.id === id) {
                dialogs = dialogs.dropLast(1)
                visible = dialogs.isNotEmpty()
            }
        }
    }
}

val LocalLiquidGlassDialogHost = staticCompositionLocalOf<LiquidGlassDialogHostState?> { null }

@Composable
fun LiquidGlassDialogHost(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    content: @Composable () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val inheritedBackdrop = LocalLiquidGlassBackdrop.current
    val activeBackdrop = backdrop ?: inheritedBackdrop
    val scope = rememberCoroutineScope()
    val state = remember(scope) { LiquidGlassDialogHostState(scope) }
    val currentSpec = state.spec
    val backgroundBlurRadius by animateDpAsState(
        targetValue = if (state.visible) currentSpec?.backgroundBlurRadius ?: 0.dp else 0.dp,
        animationSpec = tween(180),
        label = "liquidGlassDialogBackgroundBlur"
    )
    val scrimProgress by animateFloatAsState(
        targetValue = if (state.visible) 1f else 0f,
        animationSpec = tween(if (state.visible) 160 else 220),
        label = "liquidGlassDialogScrim"
    )

    CompositionLocalProvider(
        LocalLiquidGlassDialogHost provides state,
        LocalLiquidGlassBackdrop provides activeBackdrop.takeIf { isLiquidGlass }
    ) {
        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = if (
                            android.os.Build.VERSION.SDK_INT >= 31 &&
                            backgroundBlurRadius.value > 0.01f
                        ) {
                            android.graphics.RenderEffect.createBlurEffect(
                                backgroundBlurRadius.toPx(),
                                backgroundBlurRadius.toPx(),
                                android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        } else {
                            null
                        }
                    }
            ) {
                content()
            }

            AnimatedVisibility(
                visible = currentSpec != null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(190)),
                modifier = Modifier.fillMaxSize()
            ) {
                val spec = currentSpec ?: return@AnimatedVisibility
                BackHandler(enabled = state.visible) { spec.onDismissRequest() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            spec.backgroundScrimColor.copy(
                                alpha = spec.backgroundScrimColor.alpha * scrimProgress
                            )
                        )
                        .clickable(
                            enabled = state.visible,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = spec.onDismissRequest
                        )
                ) {
                    val containerEnter = if (spec.alignment == Alignment.BottomCenter) {
                        scaleIn(
                            initialScale = 0.86f,
                            animationSpec = spring(dampingRatio = 0.70f, stiffness = 145f),
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        ) + slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.70f, stiffness = 145f)
                        ) + fadeIn(tween(120))
                    } else {
                        scaleIn(
                            initialScale = 0.86f,
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = 260f)
                        ) + slideInVertically(
                            initialOffsetY = { it / 10 },
                            animationSpec = spring(dampingRatio = 0.66f, stiffness = 260f)
                        ) + fadeIn(tween(120))
                    }
                    val containerExit = if (spec.alignment == Alignment.BottomCenter) {
                        scaleOut(
                            targetScale = 0.88f,
                            animationSpec = tween(240),
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        ) + slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(260)
                        ) + fadeOut(tween(180))
                    } else {
                        scaleOut(
                            targetScale = 0.90f,
                            animationSpec = tween(190)
                        ) + slideOutVertically(
                            targetOffsetY = { it / 12 },
                            animationSpec = tween(190)
                        ) + fadeOut(tween(150))
                    }
                    AnimatedVisibility(
                        visible = state.visible,
                        enter = containerEnter,
                        exit = containerExit,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AnimatedContent(
                            targetState = currentSpec,
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.88f,
                                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 210f)
                                ) + fadeIn(tween(150))) togetherWith
                                    (scaleOut(
                                        targetScale = 0.92f,
                                        animationSpec = tween(210)
                                    ) + fadeOut(tween(170)))
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "liquidGlassDialogStack"
                        ) { targetSpec ->
                            if (targetSpec != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(
                                            start = if (targetSpec.alignment == Alignment.BottomCenter) 14.dp else 32.dp,
                                            end = if (targetSpec.alignment == Alignment.BottomCenter) 14.dp else 32.dp,
                                            top = 24.dp,
                                            bottom = if (targetSpec.alignment == Alignment.BottomCenter) 12.dp else 24.dp
                                        ),
                                    contentAlignment = targetSpec.alignment
                                ) {
                                    LiquidGlassSurface(
                                        shape = targetSpec.shape,
                                        fallbackColor = AppColors.CardBg,
                                        backdrop = targetSpec.backdrop ?: activeBackdrop,
                                        contentScrimColor = targetSpec.contentScrimColor,
                                        transparencyOverride = targetSpec.transparencyOverride,
                                        interactive = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 560.dp)
                                            .then(targetSpec.modifier)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) { },
                                        contentAlignment = Alignment.TopStart,
                                        content = targetSpec.content
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidGlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    alignment: Alignment = Alignment.Center,
    contentScrimColor: Color = AppColors.CardBg.copy(alpha = 0.78f),
    backgroundScrimColor: Color = Color.Black.copy(alpha = 0.20f),
    backgroundBlurRadius: androidx.compose.ui.unit.Dp = 0.dp,
    transparencyOverride: Float? = null,
    properties: DialogProperties = DialogProperties(),
    content: @Composable BoxScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val host = LocalLiquidGlassDialogHost.current
    val sourceBackdrop = LocalLiquidGlassBackdrop.current
    val id = remember { Any() }
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestContent by rememberUpdatedState(content)

    if (host != null && isLiquidGlass) {
        DisposableEffect(
            host,
            id,
            sourceBackdrop,
            shape,
            modifier,
            alignment,
            contentScrimColor,
            backgroundScrimColor,
            backgroundBlurRadius,
            transparencyOverride
        ) {
            host.show(
                LiquidGlassDialogSpec(
                    id = id,
                    onDismissRequest = { latestDismiss() },
                    backdrop = sourceBackdrop,
                    shape = shape,
                    modifier = modifier,
                    alignment = alignment,
                    contentScrimColor = contentScrimColor,
                    backgroundScrimColor = backgroundScrimColor,
                    backgroundBlurRadius = backgroundBlurRadius,
                    transparencyOverride = transparencyOverride,
                    content = { latestContent() }
                )
            )
            onDispose { host.hide(id) }
        }
    } else {
        Dialog(onDismissRequest = onDismissRequest, properties = properties) {
            Surface(
                modifier = Modifier.fillMaxWidth().then(modifier),
                shape = shape,
                color = AppColors.CardBg,
                contentColor = AppColors.TextPrimary
            ) {
                Box(contentAlignment = Alignment.TopStart, content = content)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiquidGlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val host = LocalLiquidGlassDialogHost.current

    if (!isLiquidGlass) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            title = title,
            text = text,
            properties = properties
        )
        return
    }

    LiquidGlassDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        properties = properties,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.End
        ) {
            title?.let {
                Box(Modifier.fillMaxWidth()) { it() }
            }
            text?.let {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (title != null) 14.dp else 0.dp, bottom = 12.dp)
                ) { it() }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
