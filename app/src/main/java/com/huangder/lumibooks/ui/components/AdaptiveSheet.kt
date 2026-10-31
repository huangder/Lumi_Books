package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huangder.lumibooks.ui.layout.currentAdaptiveWindowInfo
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape

/** Widest card on tablets; sheets keep their phone width so their content is not stretched. */
internal val CardSheetMaxWidth = com.huangder.lumibooks.ui.theme.AppPresentation.SheetMaxWidth

/** Tallest card; together with the width cap this keeps cards near square, slightly taller. */
internal val CardSheetMaxHeight = 640.dp

/** Phone height the sheets' height fractions were tuned for; cards keep those proportions. */
private val CardSheetReferenceHeight = 820.dp

/** Gap between a card and the safe area. Taps there still reach the scrim and dismiss. */
private val CardSheetMargin = 24.dp

private val CardSheetCornerRadius = com.huangder.lumibooks.ui.theme.AppPresentation.SheetCorner

/** Tablet windows present bottom sheets as centered floating cards in every theme. */
@Composable
internal fun usesCardSheets(): Boolean = currentAdaptiveWindowInfo().usesCardSheets

/** Bottom sheets round only their top edge; centered cards round every corner. */
@Composable
internal fun adaptiveSheetShape(bottomSheetShape: Shape): Shape =
    if (usesCardSheets()) AppRoundedCornerShape(CardSheetCornerRadius) else bottomSheetShape

/**
 * Positions and sizes a sheet inside its full-screen overlay Box.
 *
 * Phones keep the bottom-anchored sheet built from the same constraints as before. Tablets
 * center a card: phone width (at most 480dp) and a height that keeps the sheet's phone
 * proportions ([heightFraction] of a phone screen), capped at 640dp and by the safe area.
 */
@Composable
internal fun BoxScope.adaptiveSheetFrame(
    heightFraction: Float? = null,
    maxHeight: Dp = Dp.Unspecified,
    maxWidth: Dp = Dp.Unspecified
): Modifier {
    if (!usesCardSheets()) {
        return Modifier
            .align(Alignment.BottomCenter)
            .then(if (maxWidth.isSpecified) Modifier.widthIn(max = maxWidth) else Modifier)
            .fillMaxWidth()
            .then(if (maxHeight.isSpecified) Modifier.heightIn(max = maxHeight) else Modifier)
            .then(if (heightFraction != null) Modifier.fillMaxHeight(heightFraction) else Modifier)
    }
    val cardMaxWidth = if (maxWidth.isSpecified) minOf(maxWidth, CardSheetMaxWidth) else CardSheetMaxWidth
    val cardMaxHeight = minOf(
        if (heightFraction != null) CardSheetReferenceHeight * heightFraction else CardSheetMaxHeight,
        if (maxHeight.isSpecified) maxHeight else CardSheetMaxHeight,
        CardSheetMaxHeight
    )
    return Modifier
        .align(Alignment.Center)
        // Consumes system bar and IME insets, so the sheet's own navigationBarsPadding /
        // imePadding become no-ops instead of padding the card twice.
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(CardSheetMargin)
        .widthIn(max = cardMaxWidth)
        .fillMaxWidth()
        .heightIn(max = cardMaxHeight)
        // Fraction sheets lay out a weighted list, so they keep a fixed height as on phones.
        .then(if (heightFraction != null) Modifier.fillMaxHeight() else Modifier)
}

/**
 * Card sheet for themes without the in-window liquid glass dialog host: a centered platform
 * dialog sized like hosted cards. ModalBottomSheet would stay pinned to the bottom edge.
 */
@Composable
internal fun CardSheetDialog(
    onDismissRequest: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable BoxScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The window spans the screen width, so taps beside the card arrive here.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        enabled = properties.dismissOnClickOutside,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismissRequest
                    )
            )
            Surface(
                modifier = Modifier
                    .padding(horizontal = CardSheetMargin)
                    .widthIn(max = CardSheetMaxWidth)
                    .heightIn(max = CardSheetMaxHeight)
                    .fillMaxWidth()
                    .then(modifier),
                shape = shape,
                color = AppColors.CardBg,
                contentColor = AppColors.TextPrimary
            ) {
                Box(contentAlignment = Alignment.TopStart, content = content)
            }
        }
    }
}
