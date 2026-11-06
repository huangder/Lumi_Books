package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.LocalAppUiIconStyle
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.cardOutline

/**
 * Building blocks of the inset grouped list (as in iOS Settings): one card shape, shadow and
 * page margin, dividers inset from the leading edge only, and a direction-aware chevron.
 */
@Composable
internal fun GroupedCard(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = AppSpace.page,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = AppRoundedCornerShape(AppRadius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .shadow(8.dp, shape, ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
            .cardOutline(shape)
            .clip(shape)
            .lumiCardSurface(shape = shape),
        content = content
    )
}

/** Section caption above a [GroupedCard], aligned with the text inside its rows. */
@Composable
internal fun GroupedSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(
            start = AppSpace.page + AppSpace.md,
            end = AppSpace.page + AppSpace.md,
            top = AppSpace.md,
            bottom = AppSpace.sm
        ),
        fontSize = AppType.Footnote,
        fontWeight = FontWeight.Medium,
        color = AppColors.TextSecondary
    )
}

/** Hairline between rows; [startInset] lines it up with the row text, iOS style. */
@Composable
internal fun GroupedRowDivider(startInset: Dp = AppSpace.md) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(0.5.dp)
            .background(AppColors.Divider)
    )
}

/** iOS system colors used for settings tiles; one hue per settings domain. */
internal object SettingsTileColors {
    val Blue = Color(0xFF007AFF)
    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFFF9500)
    val Gray = Color(0xFF8E8E93)
    val Indigo = Color(0xFF5856D6)
    val Teal = Color(0xFF30B0C7)
    val Cyan = Color(0xFF32ADE6)
    val Pink = Color(0xFFFF2D55)
    val Red = Color(0xFFFF3B30)
}

/** Side of a [SettingsIconTile]; rows add [SettingsIconTileGap] before their text. */
internal val SettingsIconTileSize = 30.dp
internal val SettingsIconTileGap = 14.dp

/** Settings category icon; line styles omit the tile and monochrome tiles use neutral contrast. */
@Composable
internal fun SettingsIconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    val style = LocalAppUiIconStyle.current
    val eInk = LocalEInkMode.current
    val tileStyle = style.usesFilledGlyph
    val iconTint = when {
        style.isColorful && tileStyle -> Color.White
        style.isColorful && eInk -> Color.Black
        style.isColorful -> color
        tileStyle -> AppColors.PageBg
        else -> AppColors.TextPrimary
    }
    Box(
        modifier
            .size(SettingsIconTileSize)
            .then(
                if (tileStyle) {
                    Modifier.clip(AppRoundedCornerShape(7.dp)).background(
                        when {
                            !style.isColorful -> AppColors.TextPrimary
                            eInk -> Color.Black
                            else -> color
                        }
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            AppIcons.styled(icon, style),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(if (tileStyle) 19.dp else 22.dp)
        )
    }
}

/** Trailing chevron of navigation rows. */
@Composable
internal fun DisclosureChevron(modifier: Modifier = Modifier) {
    Icon(
        directionalIcon(AppIcons.CaretRight, AppIcons.CaretLeft),
        contentDescription = null,
        tint = AppColors.TextSecondary,
        modifier = modifier.size(18.dp)
    )
}

/** The [GroupedCard] surface as a modifier (soft shadow, outline, clip, card colour) for custom cards. */
@Composable
internal fun Modifier.lumiGroupedSurface(radius: Dp = AppRadius.lg): Modifier {
    val shape = AppRoundedCornerShape(radius)
    return this
        .shadow(8.dp, shape, ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
        .cardOutline(shape)
        .clip(shape)
        .lumiCardSurface(shape = shape)
}
