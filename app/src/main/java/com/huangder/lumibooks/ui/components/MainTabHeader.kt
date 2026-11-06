package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.theme.*

/**
 * One header geometry for every main tab (Home, Library, Statistics), so their large titles sit
 * at exactly the same height: status bar + 12dp, a fixed-height eyebrow slot (Health-style date
 * or summary line, may be empty), then the title row. Title top lands at status bar + 32dp at the
 * default font scale, matching the former Home header.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MainTabHeader(
    eyebrow: String?,
    modifier: Modifier = Modifier,
    includeStatusBar: Boolean = true,
    horizontalPadding: androidx.compose.ui.unit.Dp = AppSpace.lg,
    trailing: @Composable RowScope.() -> Unit = {},
    title: @Composable RowScope.() -> Unit
) {
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    // Full line box of the Footnote style (ascent + descent), so descenders are never clipped.
    val eyebrowHeight = with(LocalDensity.current) { AppType.Footnote.toDp() } * 1.55f
    Column(modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        Spacer(Modifier.height((if (includeStatusBar) statusTop else 0.dp) + 12.dp))
        Box(Modifier.fillMaxWidth().height(eyebrowHeight), Alignment.CenterStart) {
            if (!eyebrow.isNullOrBlank()) Text(eyebrow, color = AppColors.TextSecondary, fontSize = AppType.Footnote,
                fontWeight = FontWeight.SemiBold, letterSpacing = .02.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.wrapContentHeight(Alignment.CenterVertically, unbounded = true))
        }
        Spacer(Modifier.height((20.dp - eyebrowHeight).coerceAtLeast(0.dp)))
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = title)
            trailing()
        }
    }
}

/** The large title text shared by all main tabs. */
@Composable
internal fun MainTabTitle(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = AppColors.TextPrimary) {
    Text(text, modifier = modifier, fontSize = AppType.Display, fontWeight = FontWeight.Bold,
        fontFamily = resolveAppFontFamily(KaiTi), letterSpacing = (-0.02).sp, color = color, maxLines = 1)
}
