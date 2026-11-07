package com.huangder.lumibooks.ui.components

import com.huangder.lumibooks.ui.icons.ControlIcon

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.theme.*

/** One labelled action in a [SelectionActionBar]. */
internal data class SelectionAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Pinned header for multi-select pages (hidden library, category books, …). Transparent so the
 * scaffold's top blur shows through; Edit ↔ Done and the summary ↔ count line cross-fade, and a
 * select-all toggle reads "Deselect all" once everything is selected.
 */
@Composable
internal fun SelectionHeader(
    summary: String,
    isEditing: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onEditToggle: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    showEdit: Boolean = true
) {
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = AppSpace.lg, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(isEditing, Modifier.weight(1f), transitionSpec = {
            if (motion) fadeIn(VisualMotion.tween(180)) togetherWith fadeOut(VisualMotion.tween(120))
            else fadeIn(androidx.compose.animation.core.snap()) togetherWith fadeOut(androidx.compose.animation.core.snap())
        }, label = "selectionSummary") { editing ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!editing && leadingIcon != null) {
                    Icon(leadingIcon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (editing) stringResource(R.string.selected_books_count_compact, selectedCount) else summary,
                    color = AppColors.TextSecondary, fontSize = AppType.Footnote, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        AnimatedContent(isEditing, transitionSpec = {
            if (motion) (fadeIn(VisualMotion.tween(180)) + androidx.compose.animation.scaleIn(initialScale = .9f))
                .togetherWith(fadeOut(VisualMotion.tween(120)))
            else fadeIn(androidx.compose.animation.core.snap()) togetherWith fadeOut(androidx.compose.animation.core.snap())
        }, label = "selectionActions") { editing ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing) {
                    InsightsLikeCapsule(stringResource(if (allSelected) R.string.selection_deselect_all else R.string.select_all),
                        onClick = onSelectAll)
                    InsightsLikeCapsule(stringResource(R.string.done), primary = true, onClick = onEditToggle)
                } else if (showEdit) {
                    InsightsLikeCapsule(stringResource(R.string.edit), onClick = onEditToggle)
                }
            }
        }
    }
}

/** Compact capsule button used by selection headers (no glass: it sits on the blurred header). */
@Composable
internal fun InsightsLikeCapsule(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val eInk = LocalEInkMode.current
    val shape = AppRoundedCornerShape(AppRadius.full)
    val container = when {
        eInk -> if (primary) Color.Black else Color.White
        primary -> AppColors.Accent
        else -> AppColors.BgGray
    }
    val content = when {
        eInk -> if (primary) Color.White else Color.Black
        primary -> AppColors.OnAccent
        else -> AppColors.TextPrimary
    }
    Box(modifier.heightIn(min = 36.dp).clip(shape)
        .then(if (eInk && !primary) Modifier.border(1.dp, Color.Black, shape) else Modifier)
        .background(container)
        .lumiRowClickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = content, fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * Bottom action bar for selection mode, meant for a scaffold `bottomBar` with
 * `bottomBarOverlay = true` (it then floats on the progressive blur). Slides up while editing;
 * every action has an icon and a label, and actions disable until something is selected.
 */
@Composable
internal fun SelectionActionBar(
    visible: Boolean,
    actions: List<SelectionAction>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    AnimatedVisibility(
        visible = visible,
        enter = if (motion) slideInVertically(VisualMotion.spring(dampingRatio = .86f, stiffness = 380f)) { it } + fadeIn(VisualMotion.tween(160))
            else fadeIn(androidx.compose.animation.core.snap()),
        exit = if (motion) slideOutVertically(VisualMotion.tween(200)) { it } + fadeOut(VisualMotion.tween(140))
            else fadeOut(androidx.compose.animation.core.snap()),
        modifier = modifier
    ) {
        val shape = AppRoundedCornerShape(26.dp)
        val eInk = LocalEInkMode.current
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = AppSpace.lg, vertical = 12.dp)
                .then(if (eInk) Modifier else Modifier.shadow(14.dp, shape, ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow))
                .cardOutline(shape).clip(shape).lumiCardSurface(shape = shape)
                .then(if (eInk) Modifier.border(1.dp, Color.Black, shape) else Modifier)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                val tint = when {
                    eInk -> Color.Black
                    action.destructive -> AppColors.Destructive
                    else -> AppColors.TextPrimary
                }
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp).clip(AppRoundedCornerShape(20.dp))
                        .lumiRowClickable(enabled = enabled, onClick = action.onClick)
                        .graphicsLayer { alpha = if (enabled) 1f else .38f }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    ControlIcon(action.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(action.label, color = tint, fontSize = AppType.Caption2, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
