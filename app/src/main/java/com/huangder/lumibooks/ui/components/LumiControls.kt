package com.huangder.lumibooks.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.animation.ShimmerBox
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.*

// =============================================================================================
// Press feedback
// =============================================================================================

/**
 * iOS-style row press: the row background briefly tints (instant in, soft out) instead of the
 * Material ripple. E-ink and reduced motion keep the tint but drop the fade.
 */
@Composable
internal fun Modifier.lumiRowClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    val target = if (pressed && enabled) AppColors.TextPrimary.copy(alpha = if (LocalEInkMode.current) .14f else .07f) else Color.Transparent
    val tint by animateColorAsState(target, if (!motion) snap() else tween(if (pressed) 40 else 260), label = "rowPress")
    return this
        .drawBehind { if (tint.alpha > 0f) drawRect(tint) }
        .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

// =============================================================================================
// Grouped rows (iOS Settings idiom)
// =============================================================================================

internal val LumiRowMinHeight = 52.dp

/**
 * The one row used inside [GroupedCard]s: optional icon (coloured tile when [iconTint] is set,
 * otherwise a plain glyph), title + optional subtitle, optional trailing value text, custom
 * trailing content and an optional chevron.
 */
@Composable
internal fun LumiRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    titleColor: Color = AppColors.TextPrimary,
    value: String? = null,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LumiRowMinHeight)
            .then(if (onClick != null) Modifier.lumiRowClickable(enabled, role, onClick) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else .45f }
            .padding(horizontal = AppSpace.md, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            if (iconTint != null) {
                SettingsIconTile(icon, iconTint)
                Spacer(Modifier.width(SettingsIconTileGap))
            } else {
                Icon(icon, null, tint = if (titleColor == AppColors.Destructive) titleColor else AppColors.TextSecondary,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(AppSpace.md))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = AppType.Body, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = AppType.Caption, color = AppColors.TextSecondary)
            }
        }
        if (!value.isNullOrBlank()) {
            Spacer(Modifier.width(AppSpace.sm))
            Text(value, fontSize = AppType.BodySmall, color = AppColors.TextSecondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 168.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(AppSpace.sm))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            DisclosureChevron()
        }
    }
}

@Composable
internal fun LumiNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    enabled: Boolean = true
) = LumiRow(title, modifier, subtitle, icon, iconTint, value = value, showChevron = true, enabled = enabled, onClick = onClick)

/** Whole row toggles the switch, like iOS. */
@Composable
internal fun LumiSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null
) = LumiRow(title, modifier, subtitle, icon, iconTint, enabled = true, role = Role.Switch,
    onClick = onCheckedChange?.let { change -> { change(!checked) } }) {
    LiquidGlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
}

/** Action row; [external] actions (open link, share, export) omit the chevron. */
@Composable
internal fun LumiActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    destructive: Boolean = false,
    external: Boolean = false,
    value: String? = null,
    enabled: Boolean = true
) = LumiRow(title, modifier, icon = icon, iconTint = iconTint,
    titleColor = if (destructive) AppColors.Destructive else AppColors.TextPrimary,
    value = value, showChevron = !external && !destructive, enabled = enabled, onClick = onClick)

/** Single-choice row with an animated trailing checkmark (iOS list selection). */
@Composable
internal fun LumiCheckRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    val shown by animateFloatAsState(if (selected) 1f else 0f,
        if (motion) VisualMotion.spring(dampingRatio = .6f, stiffness = 600f) else snap(), label = "checkRow")
    LumiRow(title, modifier.semantics { this.selected = selected }, subtitle, icon, enabled = enabled,
        role = Role.RadioButton, onClick = onClick) {
        Box(Modifier.size(22.dp), Alignment.Center) {
            Icon(AppIcons.Check, null, tint = if (LocalEInkMode.current) Color.Black else AppColors.Accent,
                modifier = Modifier.size(20.dp).graphicsLayer { alpha = shown; scaleX = .6f + .4f * shown; scaleY = .6f + .4f * shown })
        }
    }
}

/** Footnote under a grouped card (iOS section footer). */
@Composable
internal fun GroupedSectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(start = AppSpace.page + AppSpace.md, end = AppSpace.page + AppSpace.md,
        top = AppSpace.sm, bottom = AppSpace.xs), fontSize = AppType.Footnote, color = AppColors.TextSecondary)
}

// =============================================================================================
// Selection controls
// =============================================================================================

/** Round animated check (iOS-style) used for multi-select. Replaces Material Checkbox. */
@Composable
internal fun LumiCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 22.dp,
    role: Role = Role.Checkbox,
    /** Accepted for drop-in migration from Material Checkbox; Lumi controls use theme colours. */
    @Suppress("UNUSED_PARAMETER") colors: Any? = null
) {
    val eInk = LocalEInkMode.current
    val motion = LocalMotionEnabled.current && !eInk
    val fill by animateFloatAsState(if (checked) 1f else 0f,
        if (motion) VisualMotion.spring(dampingRatio = .62f, stiffness = 520f) else snap(), label = "lumiCheck")
    val accent = if (eInk) Color.Black else AppColors.Accent
    Box(
        modifier
            .then(if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = role,
                interactionSource = remember { MutableInteractionSource() }, indication = null) { onCheckedChange(it) } else Modifier)
            .padding(4.dp)
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else .4f },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.matchParentSize().clip(CircleShape).border(1.5.dp, AppColors.TextSecondary.copy(alpha = .45f), CircleShape))
        Box(Modifier.matchParentSize().graphicsLayer { scaleX = fill; scaleY = fill; alpha = fill }.clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center) {
            Icon(AppIcons.Check, null, tint = if (eInk) Color.White else AppColors.OnAccent, modifier = Modifier.size(size * .62f))
        }
    }
}

/** Single-choice indicator with the same look; announces as a radio button. */
@Composable
internal fun LumiRadio(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null) =
    LumiCheckbox(selected, onClick?.let { click -> { _: Boolean -> click() } }, modifier, enabled, role = Role.RadioButton)

// =============================================================================================
// Text field
// =============================================================================================

/**
 * Filled, rounded text field (iOS inset style) with a small label above. Parameter names mirror
 * Material's OutlinedTextField so call sites migrate by renaming; [shape] is accepted and ignored.
 */
@Composable
internal fun LumiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val eInk = LocalEInkMode.current
    val borderColor by animateColorAsState(when {
        isError -> AppColors.Destructive
        focused -> if (eInk) Color.Black else AppColors.Accent
        eInk -> Color.Black.copy(alpha = .4f)
        else -> Color.Transparent
    }, tween(160), label = "fieldBorder")
    val shapeR = AppRoundedCornerShape(AppRadius.md)
    Column(modifier) {
        if (label != null) {
            Box(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                CompositionLocalProvider(LocalContentColor provides if (isError) AppColors.Destructive else AppColors.TextSecondary) {
                    androidx.compose.material3.ProvideTextStyle(TextStyle(fontSize = AppType.Footnote, fontWeight = FontWeight.Medium)) { label() }
                }
            }
        }
        BasicTextField(
            value = value, onValueChange = onValueChange, enabled = enabled, readOnly = readOnly,
            singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            visualTransformation = visualTransformation, interactionSource = interaction,
            textStyle = (textStyle ?: TextStyle(fontSize = AppType.Body)).copy(color = if (enabled) AppColors.TextPrimary else AppColors.TextSecondary),
            cursorBrush = SolidColor(if (eInk) Color.Black else AppColors.Accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shapeR)
                    .background(if (eInk) Color.White else AppColors.BgGray)
                    .border(1.dp, borderColor, shapeR)
                    .padding(start = 14.dp, end = if (trailingIcon != null) 4.dp else 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            CompositionLocalProvider(LocalContentColor provides AppColors.TextSecondary.copy(alpha = .7f)) {
                                androidx.compose.material3.ProvideTextStyle(TextStyle(fontSize = AppType.Body)) { placeholder() }
                            }
                        }
                        inner()
                    }
                    if (suffix != null) {
                        Spacer(Modifier.width(6.dp))
                        CompositionLocalProvider(LocalContentColor provides AppColors.TextSecondary) { suffix() }
                    }
                    if (trailingIcon != null) CompositionLocalProvider(LocalContentColor provides AppColors.TextSecondary) { trailingIcon() }
                }
            }
        )
        if (supportingText != null) {
            Box(Modifier.padding(start = 4.dp, top = 6.dp)) {
                CompositionLocalProvider(LocalContentColor provides if (isError) AppColors.Destructive else AppColors.TextSecondary) {
                    androidx.compose.material3.ProvideTextStyle(TextStyle(fontSize = AppType.Caption)) { supportingText() }
                }
            }
        }
    }
}

// =============================================================================================
// Segmented control
// =============================================================================================

/** Lumi segmented height: 40dp, growing with the system font scale like the notes page tabs. */
@Composable internal fun lumiSegmentHeight(): Dp = (40f * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)).dp

/**
 * App-wide segmented control. Liquid glass uses the shared glass control (sampling the provided
 * sibling backdrop); other themes use the Lumi track: grey 20dp track, white 18dp pill with a soft
 * shadow that is never clipped. Tabs announce [Role.Tab] + selected.
 */
@Composable
internal fun LumiSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = true,
    enabled: Boolean = true,
    height: Dp = lumiSegmentHeight()
) {
    if (glass && LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current) {
        LiquidGlassSegmentedControl(itemCount = labels.size, selectedIndex = selectedIndex, onSelected = onSelect,
            modifier = modifier.fillMaxWidth(), enabled = enabled, trackHeight = height, trackPadding = 2.dp) { index, selected ->
            Text(labels[index], fontSize = AppType.BodySmall, maxLines = 1, softWrap = false,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) AppColors.TextPrimary.copy(alpha = .86f) else AppColors.TextSecondary)
        }
        return
    }
    LumiSegmentedTrack(labels, selectedIndex, onSelect, modifier.fillMaxWidth(), enabled, height)
}

@Composable
internal fun LumiSegmentedTrack(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = lumiSegmentHeight()) {
    val motionEnabled = LocalMotionEnabled.current
    val eInk = LocalEInkMode.current
    val trackShape = AppRoundedCornerShape(20.dp)
    val pillShape = AppRoundedCornerShape(18.dp)
    BoxWithConstraints(modifier.height(height)
        .then(if (eInk) Modifier.border(1.dp, Color.Black, trackShape) else Modifier.background(AppColors.BgGray, trackShape))
        .padding(2.dp)) {
        val segmentWidth = maxWidth / labels.size.coerceAtLeast(1)
        val offset by animateDpAsState(segmentWidth * selectedIndex,
            if (motionEnabled) VisualMotion.spring(dampingRatio = .8f, stiffness = 420f) else snap(), label = "lumiSegment")
        Box(Modifier.offset(x = offset).width(segmentWidth).fillMaxHeight()
            .then(if (eInk) Modifier.background(Color.Black, pillShape)
                else Modifier.shadow(2.dp, pillShape, clip = false).background(if (LocalIsDarkTheme.current) AppColors.CardBg else Color.White, pillShape)))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected = selected, enabled = enabled, role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { onSelect(index) }), Alignment.Center) {
                    Text(label, fontSize = AppType.BodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            eInk && selected -> Color.White
                            selected -> AppColors.TextPrimary
                            else -> AppColors.TextSecondary
                        }, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
        }
    }
}

// =============================================================================================
// Loading and inline errors
// =============================================================================================

/** Skeleton rows while a list loads (shimmer with motion, flat blocks otherwise). */
@Composable
internal fun LumiListSkeleton(rows: Int = 6, modifier: Modifier = Modifier, rowHeight: Dp = 56.dp) {
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    Column(modifier.fillMaxWidth().padding(horizontal = AppSpace.page), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) { index ->
            val block = Modifier.fillMaxWidth(if (index % 3 == 2) .72f else 1f).height(rowHeight).clip(AppRoundedCornerShape(AppRadius.md))
            if (motion) ShimmerBox(block) else Box(block.background(AppColors.BgGray))
        }
    }
}

/** Centred loading indicator with an optional caption; caption only on e-ink / reduced motion. */
@Composable
internal fun LumiLoading(modifier: Modifier = Modifier, caption: String? = null) {
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    Column(modifier.fillMaxWidth().padding(vertical = AppSpace.xl), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
        if (motion) CircularProgressIndicator(Modifier.size(26.dp), color = AppColors.TextSecondary, strokeWidth = 2.5.dp)
        if (caption != null) Text(caption, color = AppColors.TextSecondary, fontSize = AppType.Footnote)
    }
}

/** Inline error/notice banner that stays until resolved, with an optional retry action. */
@Composable
internal fun LumiInlineBanner(
    message: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val eInk = LocalEInkMode.current
    val tone = if (eInk) Color.Black else if (destructive) AppColors.Destructive else AppColors.Accent
    val shape = AppRoundedCornerShape(AppRadius.md)
    Row(modifier.fillMaxWidth().clip(shape)
        .then(if (eInk) Modifier.border(1.dp, Color.Black, shape) else Modifier.background(tone.copy(alpha = .1f)))
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (destructive) AppIcons.Warning else AppIcons.Info, null, tint = tone, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, color = AppColors.TextPrimary, fontSize = AppType.Footnote, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            Text(actionLabel, color = tone, fontSize = AppType.Footnote, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(AppRoundedCornerShape(AppRadius.sm)).lumiRowClickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}

/**
 * Conditional section that expands/fades instead of popping (instant on e-ink / reduced motion).
 * Drop-in for `if (visible) { … }` inside a Column.
 */
@Composable
internal fun LumiReveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motion) androidx.compose.animation.expandVertically(VisualMotion.spring(dampingRatio = .9f, stiffness = 420f)) +
            androidx.compose.animation.fadeIn(VisualMotion.tween(180)) else androidx.compose.animation.EnterTransition.None,
        exit = if (motion) androidx.compose.animation.shrinkVertically(VisualMotion.tween(200)) +
            androidx.compose.animation.fadeOut(VisualMotion.tween(120)) else androidx.compose.animation.ExitTransition.None
    ) { Column(content = content) }
}

/**
 * The same iOS press tint as [lumiRowClickable], for rows that need their own clickable
 * (combinedClickable for long-press, selectable, …): pass the row's interaction source.
 */
@Composable
internal fun Modifier.lumiPressTint(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    val target = if (pressed) AppColors.TextPrimary.copy(alpha = if (LocalEInkMode.current) .14f else .07f) else Color.Transparent
    val tint by animateColorAsState(target, if (!motion) snap() else tween(if (pressed) 40 else 260), label = "pressTint")
    return this.drawBehind { if (tint.alpha > 0f) drawRect(tint) }
}
