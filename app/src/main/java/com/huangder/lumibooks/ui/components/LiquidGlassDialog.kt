package com.huangder.lumibooks.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import androidx.compose.animation.core.animateFloatAsState
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.animation.VisualMotion.delayMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.huangder.lumibooks.ui.theme.AppPresentation
import com.huangder.lumibooks.ui.theme.AppSurfaceRole
import com.huangder.lumibooks.ui.theme.AppType
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
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
    val fallbackBlurRadius: androidx.compose.ui.unit.Dp,
    val transparencyOverride: Float?,
    val properties: DialogProperties,
    val maxWidth: Dp,
    /** A bottom sheet presented as a centered card on tablets. */
    val cardSheet: Boolean = false,
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
        // If the outgoing top dialog has already started hiding, replacing it must
        // remove that stale entry. Otherwise cancelling its clear job leaves it
        // underneath the new dialog and it reappears when the new dialog closes.
        if (!visible && dialogs.isNotEmpty()) {
            dialogs = dialogs.dropLast(1)
        }
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
            delay(delayMillis(260L))
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
    /** Native editors cannot safely be recorded into a Compose layer. */
    snapshotContent: Boolean = false,
    content: @Composable () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val motionEnabled = LocalMotionEnabled.current
    val inheritedBackdrop = LocalLiquidGlassBackdrop.current
    val scope = rememberCoroutineScope()
    val state = remember(scope) { LiquidGlassDialogHostState(scope) }
    val currentSpec = state.spec
    val providedBackdrop = backdrop ?: inheritedBackdrop
    val needsSnapshot = isLiquidGlass && (snapshotContent || providedBackdrop == null)
    val snapshot = rememberWindowGlassSnapshot(
        enabled = needsSnapshot && currentSpec != null,
        identity = state.dialogs.firstOrNull()?.id
    )
    val activeBackdrop = if (needsSnapshot) snapshot.backdrop else providedBackdrop
    val overlayReady = !needsSnapshot || snapshot.ready
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
                modifier = Modifier.fillMaxSize()
                    .then(if (currentSpec != null) Modifier.clearAndSetSemantics { } else Modifier)
            ) {
                content()
            }

            AnimatedVisibility(
                visible = currentSpec != null && overlayReady,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(190)),
                modifier = Modifier.fillMaxSize()
            ) {
                val spec = currentSpec ?: return@AnimatedVisibility
                BackHandler(enabled = state.visible) {
                    if (spec.properties.dismissOnBackPress) spec.onDismissRequest()
                }
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
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (spec.properties.dismissOnClickOutside) spec.onDismissRequest()
                        }
                ) {
                    val containerEnter = if (!motionEnabled) {
                        fadeIn(tween(140))
                    } else if (spec.alignment == Alignment.BottomCenter) {
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
                    val containerExit = if (!motionEnabled) {
                        fadeOut(tween(160))
                    } else if (spec.alignment == Alignment.BottomCenter) {
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
                                if (!motionEnabled) {
                                    fadeIn(tween(120)) togetherWith fadeOut(tween(140))
                                } else {
                                    (scaleIn(
                                        initialScale = 0.88f,
                                        animationSpec = spring(dampingRatio = 0.68f, stiffness = 210f)
                                    ) + fadeIn(tween(150))) togetherWith
                                        (scaleOut(
                                            targetScale = 0.92f,
                                            animationSpec = tween(210)
                                        ) + fadeOut(tween(170)))
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "liquidGlassDialogStack"
                        ) { targetSpec ->
                            if (targetSpec != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // Cards sit mid-screen, so keep them clear of system bars.
                                        .then(
                                            if (targetSpec.alignment != Alignment.BottomCenter) {
                                                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                                            } else {
                                                Modifier
                                            }
                                        )
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
                                        interactive = false,
                                        provideSurfaceBackdrop = true,
                                        role = AppSurfaceRole.Overlay,
                                        fallbackBlurRadius = targetSpec.fallbackBlurRadius,
                                        forceFallback = targetSpec.backdrop == null && activeBackdrop == null,
                                        modifier = Modifier
                                            .then(
                                                if (targetSpec.cardSheet) {
                                                    Modifier
                                                        .widthIn(max = minOf(CardSheetMaxWidth, targetSpec.maxWidth))
                                                        .heightIn(max = CardSheetMaxHeight)
                                                } else {
                                                    Modifier.widthIn(max = targetSpec.maxWidth)
                                                }
                                            )
                                            .then(targetSpec.modifier)
                                            .fillMaxWidth()
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
    backdrop: Backdrop? = null,
    shape: Shape = AppRoundedCornerShape(AppPresentation.DialogCorner),
    alignment: Alignment = Alignment.Center,
    contentScrimColor: Color = AppColors.CardBg.copy(alpha = 0.56f),
    backgroundScrimColor: Color = Color.Black.copy(alpha = 0.20f),
    fallbackBlurRadius: androidx.compose.ui.unit.Dp = AppPresentation.OverlayFallbackBlur,
    transparencyOverride: Float? = null,
    properties: DialogProperties = DialogProperties(),
    maxWidth: Dp = AppPresentation.SheetMaxWidth,
    content: @Composable BoxScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val host = LocalLiquidGlassDialogHost.current
    // Hosted dialogs belong to the host's overlay layer, whose backdrop captures the
    // completed page below it. Falling back to a control-local backdrop here can sample
    // only the flat window background and, when nested, create a recursive RenderNode tree.
    val sourceBackdrop = backdrop ?: if (host == null || !isLiquidGlass) {
        LocalLiquidGlassBackdrop.current
    } else {
        null
    }
    val id = remember { Any() }
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestContent by rememberUpdatedState(content)
    // Tablets center bottom sheets as cards (see AdaptiveSheet.kt).
    val cardSheet = alignment == Alignment.BottomCenter && usesCardSheets()
    val effectiveAlignment = if (cardSheet) Alignment.Center else alignment
    val windowSnapshot = rememberWindowGlassSnapshot(
        enabled = host == null && isLiquidGlass, identity = id
    )

    if (host != null && isLiquidGlass) {
        DisposableEffect(
            host,
            id,
            sourceBackdrop,
            shape,
            modifier,
            effectiveAlignment,
            cardSheet,
            contentScrimColor,
            backgroundScrimColor,
            fallbackBlurRadius,
            transparencyOverride,
            properties,
            maxWidth
        ) {
            host.show(
                LiquidGlassDialogSpec(
                    id = id,
                    onDismissRequest = { latestDismiss() },
                    backdrop = sourceBackdrop,
                    shape = shape,
                    modifier = modifier,
                    alignment = effectiveAlignment,
                    contentScrimColor = contentScrimColor,
                    backgroundScrimColor = backgroundScrimColor,
                    fallbackBlurRadius = fallbackBlurRadius,
                    transparencyOverride = transparencyOverride,
                    properties = properties,
                    maxWidth = maxWidth,
                    cardSheet = cardSheet,
                    content = { latestContent() }
                )
            )
            onDispose { host.hide(id) }
        }
    } else if (isLiquidGlass) {
        // Without a host, record pixels before opening the independent Dialog window.
        // Passing a live LayerBackdrop across windows can build a cyclic native graph.
        if (!windowSnapshot.ready) return
        Dialog(onDismissRequest = onDismissRequest, properties = properties) {
            LiquidGlassSurface(
                modifier = Modifier.widthIn(max = maxWidth).then(modifier).fillMaxWidth(),
                shape = shape,
                fallbackColor = AppColors.CardBg,
                backdrop = windowSnapshot.backdrop,
                contentScrimColor = contentScrimColor,
                transparencyOverride = transparencyOverride,
                provideSurfaceBackdrop = true,
                role = AppSurfaceRole.Overlay,
                fallbackBlurRadius = fallbackBlurRadius,
                forceFallback = windowSnapshot.backdrop == null,
                contentAlignment = Alignment.TopStart,
                content = content
            )
        }
    } else if (cardSheet) {
        CardSheetDialog(
            onDismissRequest = onDismissRequest,
            shape = shape,
            modifier = modifier,
            properties = properties,
            content = content
        )
    } else {
        Dialog(onDismissRequest = onDismissRequest, properties = properties) {
            Surface(
                modifier = Modifier.widthIn(max = maxWidth).then(modifier).fillMaxWidth(),
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
    confirmButton: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    contentScrimColor: Color = AppColors.CardBg.copy(alpha = 0.60f),
    backgroundScrimColor: Color = Color.Black.copy(alpha = 0.20f),
    fallbackBlurRadius: androidx.compose.ui.unit.Dp = AppPresentation.OverlayFallbackBlur,
    transparencyOverride: Float? = null,
    actions: List<AppDialogAction>? = null,
    titleText: String? = null
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val resolvedTitle: (@Composable () -> Unit)? = titleText?.let { value -> { Text(value) } } ?: title

    if (!isLiquidGlass) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                if (actions != null) actions.filter { it.role != AppDialogActionRole.Cancel }.forEach {
                    AppDialogActionButton(it, glass = false)
                } else confirmButton()
            },
            modifier = modifier,
            dismissButton = if (actions != null) {
                { actions.filter { it.role == AppDialogActionRole.Cancel }.forEach { AppDialogActionButton(it, glass = false) } }
            } else dismissButton,
            title = resolvedTitle,
            text = text,
            properties = properties
        )
        return
    }

    LiquidGlassDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        maxWidth = AppPresentation.AlertMaxWidth,
        backdrop = backdrop,
        shape = AppRoundedCornerShape(AppPresentation.DialogCorner),
        properties = properties,
        contentScrimColor = contentScrimColor,
        backgroundScrimColor = backgroundScrimColor,
        fallbackBlurRadius = fallbackBlurRadius,
        transparencyOverride = transparencyOverride
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            resolvedTitle?.let {
                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(
                    fontSize = AppType.Section, fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary, textAlign = TextAlign.Start
                )) { Box(Modifier.fillMaxWidth()) { it() } }
            }
            text?.let {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (resolvedTitle != null) 12.dp else 0.dp, bottom = 20.dp)
                ) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(
                        fontSize = AppType.BodySmall, color = AppColors.TextSecondary,
                        textAlign = TextAlign.Start
                    )) { it() }
                }
            }
            if (text == null) Box(Modifier.padding(top = 20.dp))
            if (actions != null) AppDialogActions(actions) else FlowRow(
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
