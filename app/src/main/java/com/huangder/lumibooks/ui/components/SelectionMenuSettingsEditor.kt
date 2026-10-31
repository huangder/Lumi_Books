package com.huangder.lumibooks.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.SelectionMenuAction
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.LumiMotion
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.cardOutline
import com.kyant.backdrop.Backdrop

/** Label shared by the reader's selection menu and the settings editor. */
@StringRes
fun SelectionMenuAction.labelRes(): Int = when (this) {
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

internal fun SelectionMenuAction.icon(): ImageVector = when (this) {
    SelectionMenuAction.HIGHLIGHT -> AppIcons.PaintBrush
    SelectionMenuAction.UNDERLINE -> AppIcons.TextT
    SelectionMenuAction.NOTE -> AppIcons.PencilSimple
    SelectionMenuAction.DICTIONARY -> AppIcons.BookOpenText
    SelectionMenuAction.EXCERPT -> AppIcons.ImageSquare
    SelectionMenuAction.SEARCH -> AppIcons.MagnifyingGlass
    SelectionMenuAction.WEB_SEARCH -> AppIcons.Globe
    SelectionMenuAction.COPY -> AppIcons.FileText
    SelectionMenuAction.REPLACE -> AppIcons.ArrowsLeftRight
}

private val ReorderEdgeZone = 56.dp
private val ReorderMaxScrollPerFrame = 14.dp
private const val DraggedCardScale = 1.02f

/**
 * Editable list of selection menu actions: a visibility switch per action and long-press
 * handles for drag reordering. Every change is reported through [onChange] right away; the
 * caller decides whether it is saved immediately or kept as a draft.
 *
 * [onReorderingChange] fires when a drag starts and ends so hosts can pause costly effects.
 */
@Composable
fun SelectionMenuSettingsEditor(
    settings: SelectionMenuSettings,
    onChange: (SelectionMenuSettings) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onReorderingChange: (Boolean) -> Unit = {}
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motionEnabled = LocalMotionEnabled.current && !LocalEInkMode.current
    val haptics = LocalHapticFeedback.current
    val reorder = remember(listState, scope, density) {
        SelectionMenuReorderState(
            listState = listState,
            scope = scope,
            edgeZonePx = with(density) { ReorderEdgeZone.toPx() },
            maxScrollPerFramePx = with(density) { ReorderMaxScrollPerFrame.toPx() }
        )
    }
    // Gesture callbacks outlive recompositions; always act on the newest settings.
    val currentSettings by rememberUpdatedState(settings)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnReorderingChange by rememberUpdatedState(onReorderingChange)
    val order = reorder.draftOrder ?: settings.order

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        items(order, key = SelectionMenuAction::storageKey) { action ->
            val dragged = reorder.draggedAction == action
            val index = order.indexOf(action)
            SelectionMenuActionCard(
                action = action,
                visible = settings.isVisible(action),
                canToggle = !settings.isVisible(action) || settings.canHide(action),
                onVisibleChange = { visible -> currentOnChange(currentSettings.withVisibility(action, visible)) },
                onMoveUp = if (index > 0) {
                    { currentOnChange(currentSettings.moveBy(action, -1)) }
                } else null,
                onMoveDown = if (index < order.lastIndex) {
                    { currentOnChange(currentSettings.moveBy(action, 1)) }
                } else null,
                handleModifier = Modifier.pointerInput(action, reorder) {
                    fun endDrag(commit: Boolean) {
                        if (commit) {
                            reorder.finish()?.let { currentOnChange(currentSettings.withOrder(it)) }
                        } else {
                            reorder.cancel()
                        }
                        currentOnReorderingChange(false)
                    }
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            if (reorder.start(action, currentSettings.order)) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnReorderingChange(true)
                            }
                        },
                        onDragEnd = { if (reorder.draggedAction == action) endDrag(commit = true) },
                        onDragCancel = { if (reorder.draggedAction == action) endDrag(commit = false) }
                    ) { change, dragAmount ->
                        change.consume()
                        reorder.dragBy(dragAmount.y)
                    }
                },
                modifier = Modifier
                    .then(
                        // The dragged card follows the finger; only its neighbours glide.
                        if (motionEnabled && !dragged) Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = VisualMotion.tween(LumiMotion.MenuEnterMillis, easing = AppEasing.Smooth)
                        ) else Modifier
                    )
                    .zIndex(if (dragged) 1f else 0f)
                    .graphicsLayer {
                        translationY = reorder.translationOf(action)
                        val scale = if (dragged && motionEnabled) DraggedCardScale else 1f
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}

@Composable
private fun SelectionMenuActionCard(
    action: SelectionMenuAction,
    visible: Boolean,
    canToggle: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    handleModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val shape = AppRoundedCornerShape(AppRadius.lg)
    val label = stringResource(action.labelRes())
    val dragDescription = stringResource(R.string.selection_menu_drag, label)
    val moveUpLabel = stringResource(R.string.selection_menu_move_up, label)
    val moveDownLabel = stringResource(R.string.selection_menu_move_down, label)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("selection-menu-${action.storageKey}")
            .shadow(8.dp, shape, ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .cardOutline(shape)
            .clip(shape)
            .lumiCardSurface(shape = shape)
            .heightIn(min = 60.dp)
            .padding(start = AppSpace.md, end = AppSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(action.icon(), contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(AppSpace.md))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (action == SelectionMenuAction.REPLACE) {
                Spacer(Modifier.width(AppSpace.sm))
                TxtOnlyTag()
            }
        }
        Spacer(Modifier.width(AppSpace.sm))
        LiquidGlassSwitch(checked = visible, onCheckedChange = onVisibleChange, enabled = canToggle)
        Box(
            modifier = Modifier
                .size(44.dp)
                .semantics {
                    contentDescription = dragDescription
                    customActions = listOfNotNull(
                        onMoveUp?.let { move -> CustomAccessibilityAction(moveUpLabel) { move(); true } },
                        onMoveDown?.let { move -> CustomAccessibilityAction(moveDownLabel) { move(); true } }
                    )
                }
                .then(handleModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.DotsSixVertical, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

/** Marks actions that only appear for TXT books. Neutral colours keep it legible on e-ink. */
@Composable
private fun TxtOnlyTag() {
    Text(
        stringResource(R.string.selection_menu_txt_only),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        color = AppColors.TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(AppRoundedCornerShape(AppRadius.full))
            .background(AppColors.TextSecondary.copy(alpha = 0.12f))
            .padding(horizontal = AppSpace.sm, vertical = 2.dp)
    )
}

/** Capsule action used at the bottom of the selection menu settings page and sheet. */
@Composable
fun SelectionMenuCapsuleButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassSurface(
        shape = AppRoundedCornerShape(AppRadius.full),
        fallbackColor = AppColors.CardBg,
        modifier = modifier.heightIn(min = 44.dp),
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.86f),
        onClick = onClick,
        controlEdge = true
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpace.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, maxLines = 1)
        }
    }
}
private enum class SheetOutcome { Save, Discard }

/**
 * Reader sheet for the selection menu. The sheet edits a draft that only [onSave] receives
 * when the user confirms; close, back and scrim taps discard it. Either callback runs after
 * the exit animation, so the caller may remove the sheet right away.
 */
@Composable
fun SelectionMenuSettingsDialog(
    visible: Boolean,
    currentItems: SelectionMenuSettings,
    onDismiss: () -> Unit,
    onSave: (SelectionMenuSettings) -> Unit,
    backdrop: Backdrop? = null
) {
    if (!visible) return
    // Leaving composition while hidden resets the draft, so each opening starts from the saved settings.
    var draft by remember { mutableStateOf(currentItems) }
    var outcome by remember { mutableStateOf<SheetOutcome?>(null) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSave by rememberUpdatedState(onSave)
    val motionEnabled = LocalMotionEnabled.current && !LocalEInkMode.current
    val sheetOffset = remember { Animatable(1f) }
    val discard = { if (outcome == null) outcome = SheetOutcome.Discard }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler(onBack = discard)

    LaunchedEffect(Unit) {
        if (motionEnabled) sheetOffset.animateBottomSheetIn() else sheetOffset.snapTo(0f)
    }
    LaunchedEffect(outcome) {
        val result = outcome ?: return@LaunchedEffect
        if (motionEnabled) sheetOffset.animateBottomSheetOut() else sheetOffset.snapTo(1f)
        when (result) {
            SheetOutcome.Save -> currentOnSave(draft)
            SheetOutcome.Discard -> currentOnDismiss()
        }
    }

    val scrimColor = AppColors.Scrim
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.82f
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(scrimColor, alpha = 0.28f * (1f - sheetOffset.value.coerceIn(0f, 1f))) }
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = discard)
        )
        LiquidGlassColumnSheetContainer(
            modifier = adaptiveSheetFrame(maxHeight = maxSheetHeight)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            fallbackColor = AppColors.CardBg,
            shape = AppRoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = backdrop ?: LocalLiquidGlassBackdrop.current
        ) {
            AppSheetHeader(title = stringResource(R.string.selection_menu_settings), onClose = discard)
            Spacer(Modifier.height(12.dp))
            SelectionMenuSettingsEditor(
                settings = draft,
                onChange = { draft = it },
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
            )
            Spacer(Modifier.height(AppSpace.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                SelectionMenuCapsuleButton(
                    text = stringResource(R.string.highlight_reset_default),
                    icon = AppIcons.ArrowCounterClockwise,
                    onClick = { draft = SelectionMenuSettings() },
                    modifier = Modifier.weight(1f)
                )
                SelectionMenuCapsuleButton(
                    text = stringResource(R.string.confirm),
                    icon = AppIcons.Check,
                    onClick = { if (outcome == null) outcome = SheetOutcome.Save },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
