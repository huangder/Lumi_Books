package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.*

enum class AppDialogActionRole { Primary, Secondary, Cancel, Destructive }

/** Explicit actions let the container measure labels before choosing a layout. */
data class AppDialogAction(
    val text: String,
    val onClick: () -> Unit,
    val role: AppDialogActionRole = AppDialogActionRole.Primary,
    val enabled: Boolean = true,
    val tintedColor: Color? = null,
    val contentColor: Color? = null
)

internal fun stackDialogActions(availableWidth: Float, labelWidths: List<Float>, gap: Float, padding: Float): Boolean =
    labelWidths.size > 2 || (labelWidths.size == 2 &&
        labelWidths.any { it + padding * 2 > (availableWidth - gap) / 2 })

internal fun orderedDialogActions(actions: List<AppDialogAction>, stacked: Boolean): List<AppDialogAction> =
    actions.sortedBy { action ->
        if (stacked) when (action.role) {
            AppDialogActionRole.Primary, AppDialogActionRole.Destructive -> 0
            AppDialogActionRole.Secondary -> 1
            AppDialogActionRole.Cancel -> 2
        } else if (action.role == AppDialogActionRole.Cancel) 0 else 1
    }

@Composable
internal fun AppDialogActionButton(action: AppDialogAction, modifier: Modifier = Modifier, glass: Boolean = true) {
    val tint = action.tintedColor ?: if (glass && action.role == AppDialogActionRole.Primary) AppColors.Accent else null
    val ink = action.contentColor ?: when {
        tint != null -> AppColors.OnAccent
        action.role == AppDialogActionRole.Destructive -> AppColors.Destructive
        !glass && action.role == AppDialogActionRole.Primary -> AppColors.Accent
        else -> AppColors.TextPrimary
    }
    if (glass) {
        LiquidGlassButton(
            onClick = action.onClick,
            enabled = action.enabled,
            tintedColor = tint,
            contentColor = ink,
            modifier = modifier.heightIn(min = AppPresentation.ActionMinHeight)
        ) {
            Text(action.text, color = ink, fontSize = AppType.Body, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp))
        }
    } else {
        TextButton(onClick = action.onClick, enabled = action.enabled, modifier = modifier,
            colors = ButtonDefaults.textButtonColors(
                containerColor = tint ?: Color.Transparent,
                contentColor = ink
            )) { Text(action.text, textAlign = TextAlign.Center) }
    }
}

@Composable
internal fun AppDialogActions(actions: List<AppDialogAction>) {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(fontSize = AppType.Body, fontWeight = FontWeight.SemiBold)
    val widths = actions.map { measurer.measure(it.text, style, softWrap = false).size.width.toFloat() }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = with(density) {
            stackDialogActions(maxWidth.toPx(), widths, AppPresentation.ActionGap.toPx(), 16.dp.toPx())
        }
        val ordered = orderedDialogActions(actions, stacked)
        if (stacked) {
            Column(Modifier.fillMaxWidth().testTag("dialog_actions_vertical"), verticalArrangement = Arrangement.spacedBy(AppPresentation.ActionGap)) {
                ordered.forEach { AppDialogActionButton(it, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxWidth().testTag("dialog_actions_horizontal"), horizontalArrangement = Arrangement.spacedBy(AppPresentation.ActionGap)) {
                ordered.forEach { AppDialogActionButton(it, Modifier.weight(1f)) }
            }
        }
    }
}
