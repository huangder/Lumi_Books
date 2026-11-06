package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppPresentation
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.LocalAppTheme

/**
 * The app's standard bottom sheet. Liquid glass and tablet windows present it through the
 * in-window glass dialog; other themes use a Material sheet with the same corner, scrim and
 * grabber, so every sheet in the app looks and dismisses alike.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    if (LocalAppTheme.current == "liquid_glass" || usesCardSheets()) {
        LiquidGlassDialog(
            onDismissRequest = onDismissRequest,
            alignment = Alignment.BottomCenter,
            shape = AppRoundedCornerShape(AppPresentation.SheetCorner),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.12f)
        ) { content() }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppColors.CardBg,
            contentColor = AppColors.TextPrimary,
            scrimColor = Color.Black.copy(alpha = 0.12f),
            shape = AppRoundedCornerShape(
                topStart = AppPresentation.SheetCorner,
                topEnd = AppPresentation.SheetCorner
            ),
            dragHandle = { AppSheetGrabber() }
        ) { content() }
    }
}

/** iOS-style grabber shown at the top of Material bottom sheets. */
@Composable
internal fun AppSheetGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .size(width = 36.dp, height = 5.dp)
            .background(AppColors.TextSecondary.copy(alpha = 0.3f), AppRoundedCornerShape(2.5.dp))
    )
}

/**
 * Standard sheet geometry: height capped to 80% of the real screen (never past the window on
 * short or split screens), with top/bottom gaps that clear the navigation bar even when a dialog
 * window reports no insets. Sheet content should use these instead of fixed heights/paddings.
 */
@androidx.compose.runtime.Composable
internal fun appSheetMaxHeight(): androidx.compose.ui.unit.Dp =
    (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * .8f).dp

@androidx.compose.runtime.Composable
internal fun appSheetPadding(horizontal: androidx.compose.ui.unit.Dp = com.huangder.lumibooks.ui.theme.AppSpace.lg): androidx.compose.foundation.layout.PaddingValues {
    val navigation = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    return androidx.compose.foundation.layout.PaddingValues(start = horizontal, end = horizontal,
        top = com.huangder.lumibooks.ui.theme.AppSpace.md + 4.dp,
        bottom = maxOf(navigation, com.huangder.lumibooks.ui.theme.AppSpace.md) + com.huangder.lumibooks.ui.theme.AppSpace.lg)
}
