package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape

/** PDF and comic actions share the same available-width policy, including system text scaling. */
@Composable
internal fun RasterReaderActionRow(
    isComic: Boolean,
    onAnnotationClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onImageAdjustmentsClick: () -> Unit,
    onConversionClick: () -> Unit,
    modifier: Modifier = Modifier,
    glassContentScrimColor: Color = AppColors.WindowBg.copy(alpha = 0.18f)
) {
    val annotationLabel = stringResource(R.string.pdf_annotation_tool)
    val bookmarksLabel = stringResource(R.string.reader_bookmark)
    val adjustmentLabel = stringResource(R.string.reader_image_adjustment_entry)
    val conversionLabel = stringResource(R.string.pdf_convert_action)
    val density = LocalDensity.current
    val textDensity = Density(density.density, density.fontScale)
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(fontSize = 13.sp)
    val actionWidth = with(density) {
        listOf(annotationLabel, bookmarksLabel, adjustmentLabel).maxOf {
            measurer.measure(it, style, softWrap = false, maxLines = 1, density = textDensity).size.width
        }.toDp()
    } + 52.dp // icon, gap, and both horizontal paddings
    val menuHost = LocalLiquidGlassMenuHost.current
    val moreMenuId = remember { Any() }
    var moreAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val moreExpanded = menuHost?.activeMenu?.sourceId == moreMenuId
    val currentOnAdjustmentsClick by rememberUpdatedState(onImageAdjustmentsClick)
    val currentOnConversionClick by rememberUpdatedState(onConversionClick)
    val menuSurfaceColor = AppColors.CardBg
    val menuContentColor = AppColors.TextPrimary

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Fit all three weighted capsules before reserving space for PDF's existing More button.
        val adjustmentInMore = actionWidth * 3 + 20.dp + (if (isComic) 0.dp else 58.dp) > maxWidth
        val showMore = !isComic || adjustmentInMore
        val moreItems = buildList {
            if (adjustmentInMore) {
                add(LiquidGlassMenuItem(adjustmentLabel, AppIcons.Gear) { currentOnAdjustmentsClick() })
            }
            if (!isComic) {
                add(LiquidGlassMenuItem(conversionLabel, AppIcons.Books) { currentOnConversionClick() })
            }
        }
        val menuWidth = maxOf(208.dp, with(density) {
            moreItems.maxOfOrNull {
                measurer.measure(it.label, style.copy(fontSize = 14.sp), softWrap = false, density = textDensity).size.width
            }?.toDp() ?: 0.dp
        } + 60.dp)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            PdfActionCapsule(AppIcons.PencilSimple, annotationLabel, Modifier.weight(1f), onClick = onAnnotationClick)
            PdfActionCapsule(AppIcons.Bookmark.filled, bookmarksLabel, Modifier.weight(1f), onClick = onBookmarksClick)
            if (!adjustmentInMore) {
                PdfActionCapsule(AppIcons.Gear, adjustmentLabel, Modifier.weight(1f), onClick = onImageAdjustmentsClick)
            }
            if (showMore) {
                LiquidGlassIconButton(
                    imageVector = AppIcons.DotsThreeVertical,
                    contentDescription = stringResource(R.string.more_options),
                    contentColor = if (moreExpanded) AppColors.Accent else menuContentColor,
                    normalContainerColor = AppColors.BgGray,
                    liquidContainerColor = AppColors.BgGray,
                    liquidScrimColor = glassContentScrimColor,
                    pressFeedbackEnabled = false,
                    modifier = Modifier.liquidGlassMenuAnchor(moreMenuId)
                        .onGloballyPositioned { moreAnchorBounds = it.boundsInWindow() },
                    onClick = {
                        if (moreAnchorBounds != Rect.Zero) {
                            menuHost?.toggle(LiquidGlassMenuSpec(
                                anchorBounds = moreAnchorBounds, sourceId = moreMenuId,
                                width = menuWidth, preferAbove = true,
                                surfaceColor = menuSurfaceColor, contentColor = menuContentColor,
                                items = moreItems
                            ))
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun PdfActionCapsule(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (active) AppColors.Accent else AppColors.TextPrimary
    val density = LocalDensity.current
    val textDensity = Density(density.density, density.fontScale)
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(fontSize = 13.sp)
    val labelWidth = with(density) {
        measurer.measure(label, style, softWrap = false, maxLines = 1, density = textDensity).size.width.toDp()
    }
    LiquidGlassSurface(
        controlEdge = true,
        shape = AppRoundedCornerShape(22.dp),
        fallbackColor = if (active) AppColors.Accent.copy(alpha = 0.14f) else AppColors.BgGray,
        contentScrimColor = AppColors.WindowBg.copy(alpha = 0.18f),
        modifier = modifier.heightIn(min = 48.dp),
        onClick = onClick
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val horizontalPadding = if (labelWidth + 52.dp <= maxWidth) 14.dp else 8.dp
            // Keep the complete short label even when large system text needs a second row for the icon.
            val stackIcon = labelWidth + 24.dp + horizontalPadding * 2 > maxWidth
            val contentModifier = Modifier.fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 10.dp)
            val content: @Composable () -> Unit = {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
                Text(label, style = style, color = contentColor, maxLines = 1,
                    softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            if (stackIcon) {
                Column(contentModifier, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) { content() }
            } else {
                Row(contentModifier, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) { content() }
            }
        }
    }
}
