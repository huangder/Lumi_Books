package com.huangder.lumibooks.ui.privacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily

/** Same layout as the category pages' header; editing swaps in unhide and delete. */
@Composable
internal fun HiddenLibraryHeader(
    bookCount: Int,
    isEditing: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onEditToggle: () -> Unit,
    onSelectAll: () -> Unit,
    onUnhide: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    Column(
        modifier
            .fillMaxWidth()
            .background(AppColors.WindowBg.copy(alpha = 0.94f))
            .statusBarsPadding()
            .padding(horizontal = AppSpace.lg, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isEditing) {
                LiquidGlassButton(
                    onClick = onEditToggle,
                    tintedColor = AppColors.CardBg.takeUnless { isLiquidGlass },
                    contentColor = AppColors.TextPrimary,
                    modifier = Modifier.widthIn(min = 88.dp).heightIn(min = 46.dp)
                ) {
                    Text(
                        stringResource(R.string.done), color = AppColors.TextPrimary, fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                LiquidGlassIconButton(
                    imageVector = AppIcons.Eye,
                    contentDescription = stringResource(R.string.hidden_library_unhide),
                    onClick = onUnhide,
                    enabled = selectedCount > 0,
                    size = 46.dp,
                    iconSize = 21.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = AppColors.CardBg,
                    liquidContainerColor = AppColors.CardBg,
                    liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f)
                )
                Spacer(Modifier.width(10.dp))
                LiquidGlassIconButton(
                    imageVector = AppIcons.Trash,
                    contentDescription = stringResource(R.string.delete),
                    onClick = onDelete,
                    enabled = selectedCount > 0,
                    size = 46.dp,
                    iconSize = 21.dp,
                    contentColor = Color.White,
                    normalContainerColor = Color(0xFFD92D3A),
                    liquidContainerColor = Color(0xFFD92D3A),
                    liquidScrimColor = Color(0xB8D92D3A)
                )
            } else {
                LiquidGlassIconButton(
                    imageVector = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                    settingsBackButton = true
                )
                Spacer(Modifier.width(16.dp))
                androidx.compose.material3.Icon(
                    imageVector = AppIcons.Lock,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.hidden_library_title), color = AppColors.TextPrimary,
                        fontSize = AppType.Section, fontWeight = FontWeight.Bold,
                        fontFamily = resolveAppFontFamily(KaiTi), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        pluralStringResource(R.plurals.hidden_library_book_count, bookCount, bookCount),
                        color = AppColors.TextSecondary, fontSize = AppType.Caption, maxLines = 1
                    )
                }
                if (bookCount > 0) {
                    LiquidGlassButton(
                        onClick = onEditToggle,
                        tintedColor = AppColors.CardBg,
                        modifier = Modifier.widthIn(min = 78.dp).heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.edit), color = AppColors.TextPrimary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        AnimatedVisibility(visible = isEditing) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.selected_books_count_compact, selectedCount),
                    color = AppColors.TextSecondary, fontSize = AppType.Caption, fontWeight = FontWeight.Medium,
                    maxLines = 1, modifier = Modifier.weight(1f)
                )
                LiquidGlassButton(
                    onClick = onSelectAll,
                    tintedColor = AppColors.Accent,
                    contentColor = AppColors.OnAccent,
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text(
                        stringResource(R.string.select_all), color = AppColors.OnAccent, fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
