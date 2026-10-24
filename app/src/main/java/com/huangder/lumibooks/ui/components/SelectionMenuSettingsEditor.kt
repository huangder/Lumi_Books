package com.huangder.lumibooks.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.SelectionMenuAction
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest

@StringRes
internal fun SelectionMenuAction.labelRes(): Int = when (this) {
    SelectionMenuAction.HIGHLIGHT -> R.string.menu_highlight
    SelectionMenuAction.UNDERLINE -> R.string.menu_underline
    SelectionMenuAction.NOTE -> R.string.menu_note
    SelectionMenuAction.DICTIONARY -> R.string.menu_dictionary
    SelectionMenuAction.EXCERPT -> R.string.excerpt_title
    SelectionMenuAction.SEARCH -> R.string.menu_search
    SelectionMenuAction.WEB_SEARCH -> R.string.menu_web_search
    SelectionMenuAction.COPY -> R.string.menu_copy
    SelectionMenuAction.REPLACE -> R.string.menu_replace
}

@Composable
internal fun SelectionMenuSettingsEditor(
    settings: SelectionMenuSettings,
    onChange: (SelectionMenuSettings) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(AppSpace.md),
    onDraggingChange: (Boolean) -> Unit = {}
) {
    val motionEnabled = LocalMotionEnabled.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reorder = remember(listState, density, motionEnabled) {
        SelectionMenuReorderState(listState, scope, settings.order,
            with(density) { 48.dp.toPx() }, with(density) { 600.dp.toPx() }, motionEnabled)
    }
    val latestSettings by rememberUpdatedState(settings)
    val latestChange by rememberUpdatedState(onChange)
    SideEffect { reorder.syncOrder(settings.order) }
    val lifted = reorder.draggedAction != null
    LaunchedEffect(lifted) { onDraggingChange(lifted) }
    DisposableEffect(Unit) { onDispose { onDraggingChange(false) } }
    LaunchedEffect(reorder) {
        snapshotFlow { listState.layoutInfo }.collect { reorder.reorderAtPointer() }
    }
    LaunchedEffect(reorder) {
        snapshotFlow { reorder.scrollSpeed }.collectLatest { speed ->
            if (speed == 0f) return@collectLatest
            var previousFrame = withFrameNanos { it }
            while (reorder.dragging) {
                val frame = withFrameNanos { it }
                val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
                previousFrame = frame
                val consumed = listState.scrollBy(speed * seconds)
                if (consumed == 0f) break
                reorder.reorderAtPointer()
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().testTag("selection_menu_list")
            .onGloballyPositioned { reorder.listCoordinates = it }
            .pointerInput(reorder) {
                // Listen on the stationary list, so moving/recomposing a card cannot move the gesture origin.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val action = reorder.handleAt(down.position) ?: return@awaitEachGesture
                    val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    if (!reorder.start(action, press.position.y)) return@awaitEachGesture
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        var completed = false
                        while (true) {
                            // Claim movement before the inner LazyColumn scroll handler sees it.
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                .firstOrNull { it.id == press.id } ?: break
                            if (change.isConsumed) break
                            if (!change.pressed) {
                                change.consume()
                                completed = true
                                break
                            }
                            change.consume()
                            reorder.dragTo(change.position.y)
                        }
                        reorder.finish(completed)?.let { order -> latestChange(latestSettings.copy(order = order)) }
                    } catch (cancelled: CancellationException) {
                        reorder.finish(false)
                        throw cancelled
                    }
                }
            },
        userScrollEnabled = !reorder.dragging,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        itemsIndexed(reorder.order, key = { _, action -> action.key }) { index, action ->
            val active = action == reorder.draggedAction
            SelectionMenuToolCard(
                action = action,
                visible = settings.isVisible(action),
                canHide = settings.order.count(settings::isVisible) > 1,
                canMoveUp = index > 0,
                canMoveDown = index < settings.order.lastIndex,
                onVisibilityChange = { onChange(settings.withVisibility(action, it)) },
                onMove = { direction ->
                    listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                    onChange(settings.move(action, direction))
                },
                dragging = active,
                onHandlePositioned = { reorder.registerHandle(action, it) },
                modifier = Modifier.zIndex(if (active) 1f else 0f)
                    .graphicsLayer { translationY = reorder.translationY(action) }
                    .then(if (motionEnabled && !active) Modifier.animateItem(
                    fadeInSpec = null, fadeOutSpec = null,
                    placementSpec = tween(160, easing = AppEasing.Smooth)
                ) else Modifier)
            )
        }
    }
}

@Composable
internal fun SelectionMenuSettingsDialog(
    visible: Boolean,
    currentItems: SelectionMenuSettings,
    onDismiss: () -> Unit,
    onSave: (SelectionMenuSettings) -> Unit,
    backdrop: Backdrop? = null
) {
    if (!visible) return
    var draft by remember { mutableStateOf(currentItems) }
    val sheetOffset = remember { Animatable(1f) }
    var closing by remember { mutableStateOf(false) }
    var saveOnClose by remember { mutableStateOf(false) }
    val dismiss = { closing = true }
    val backProgress = ConfigurableBottomSheetBackHandler(onBack = dismiss)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    LaunchedEffect(Unit) { sheetOffset.animateBottomSheetIn() }
    LaunchedEffect(closing) {
        if (closing) {
            sheetOffset.animateBottomSheetOut()
            if (saveOnClose) onSave(draft) else onDismiss()
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(AppColors.Scrim.copy(alpha = 0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f))))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = dismiss))
        LiquidGlassColumnSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 840.dp).fillMaxWidth()
                .heightIn(max = screenHeight * 0.88f).materialBottomSheetMotion(sheetOffset.value, backProgress),
            contentModifier = Modifier.navigationBarsPadding().pointerInput(Unit) { detectTapGestures { } },
            fallbackColor = AppColors.WindowBg,
            shape = AppRoundedCornerShape(topStart = AppRadius.xl, topEnd = AppRadius.xl),
            backdrop = backdrop
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = AppSpace.md).heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.selection_menu_settings), color = AppColors.TextPrimary,
                    fontSize = AppType.Section, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 48.dp))
                LiquidGlassIconButton(AppIcons.X, stringResource(R.string.reader_close), dismiss,
                    modifier = Modifier.align(Alignment.CenterEnd), size = 44.dp, normalContainerColor = AppColors.BgGray)
            }
            SelectionMenuSettingsEditor(draft, { draft = it }, Modifier.weight(1f, fill = false))
            Row(Modifier.fillMaxWidth().padding(AppSpace.md), horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                SelectionMenuCapsuleButton(stringResource(R.string.highlight_reset_default), AppIcons.ArrowCounterClockwise,
                    { draft = SelectionMenuSettings() }, Modifier.weight(1f))
                SelectionMenuCapsuleButton(stringResource(R.string.confirm), AppIcons.Check,
                    { saveOnClose = true; closing = true }, Modifier.weight(1f), selected = true)
            }
        }
    }
}

private fun SelectionMenuAction.icon(): ImageVector = when (this) {
    SelectionMenuAction.HIGHLIGHT -> AppIcons.PaintBrush
    SelectionMenuAction.UNDERLINE -> AppIcons.LineSegments
    SelectionMenuAction.NOTE -> AppIcons.PencilSimple
    SelectionMenuAction.DICTIONARY -> AppIcons.BookOpen
    SelectionMenuAction.EXCERPT -> AppIcons.Cards
    SelectionMenuAction.SEARCH -> AppIcons.MagnifyingGlass
    SelectionMenuAction.WEB_SEARCH -> AppIcons.Globe
    SelectionMenuAction.COPY -> AppIcons.FileText
    SelectionMenuAction.REPLACE -> AppIcons.ArrowsLeftRight
}

@Composable
private fun SelectionMenuToolCard(
    action: SelectionMenuAction,
    visible: Boolean,
    canHide: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    dragging: Boolean,
    onHandlePositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(action.labelRes())
    val dragLabel = stringResource(R.string.selection_menu_drag, label)
    val upLabel = stringResource(R.string.selection_menu_move_up, label)
    val downLabel = stringResource(R.string.selection_menu_move_down, label)
    val shape = AppRoundedCornerShape(AppRadius.lg)
    val currentMove by rememberUpdatedState(onMove)
    Row(
        modifier.fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
            .cardOutline(shape).clip(shape).lumiCardSurface(shape = shape)
            .then(if (dragging) Modifier.border(1.dp, AppColors.Accent, shape) else Modifier)
            .padding(AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(AppRoundedCornerShape(AppRadius.md))
            .background(if (visible) AppColors.Accent.copy(alpha = 0.10f) else AppColors.BgGray),
            contentAlignment = Alignment.Center) {
            Icon(action.icon(), null, tint = if (visible) AppColors.Accent else AppColors.TextSecondary,
                modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(AppSpace.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            Text(label, color = AppColors.TextPrimary, fontSize = AppType.Body, fontWeight = FontWeight.SemiBold)
            if (action == SelectionMenuAction.REPLACE) {
                Text(stringResource(R.string.selection_menu_txt_only),
                    color = AppColors.TextSecondary, fontSize = AppType.Caption)
            }
        }
        Spacer(Modifier.width(AppSpace.sm))
        LiquidGlassSwitch(checked = visible, onCheckedChange = onVisibilityChange,
            enabled = !visible || canHide, modifier = Modifier.semantics { contentDescription = label })
        Spacer(Modifier.width(AppSpace.sm))
        Box(
            Modifier.size(36.dp, 48.dp).semantics {
                contentDescription = dragLabel
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(upLabel) { currentMove(-1); true })
                    if (canMoveDown) add(CustomAccessibilityAction(downLabel) { currentMove(1); true })
                }
            }.onGloballyPositioned(onHandlePositioned), contentAlignment = Alignment.Center
        ) { Icon(AppIcons.DotsSixVertical, null, tint = AppColors.TextSecondary, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
internal fun SelectionMenuCapsuleButton(
    text: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, selected: Boolean = false
) {
    val container = if (selected) AppColors.Accent else AppColors.CardBg
    val foreground = if (selected) AppColors.OnAccent else AppColors.TextPrimary
    LiquidGlassSurface(
        controlEdge = true, modifier = modifier.heightIn(min = 44.dp),
        shape = AppRoundedCornerShape(AppRadius.full), fallbackColor = container,
        contentScrimColor = container.copy(alpha = 0.86f), tintColor = AppColors.Accent.takeIf { selected },
        onClick = onClick
    ) {
        Row(Modifier.padding(horizontal = AppSpace.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
            Icon(icon, null, tint = foreground, modifier = Modifier.size(17.dp))
            Text(text, color = foreground, fontSize = AppType.Caption, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
