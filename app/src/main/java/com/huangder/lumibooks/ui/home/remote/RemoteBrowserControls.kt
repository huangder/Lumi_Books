package com.huangder.lumibooks.ui.home.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

/** DEFAULT keeps the catalog's own order, which OPDS servers often curate (newest, popular). */
enum class RemoteSortMode { DEFAULT, NAME, SIZE, TIME, FORMAT }

@Composable
private fun sortLabel(mode: RemoteSortMode): String = stringResource(
    when (mode) {
        RemoteSortMode.DEFAULT -> R.string.import_sort_default
        RemoteSortMode.NAME -> R.string.folder_books_sort_name
        RemoteSortMode.SIZE -> R.string.folder_books_sort_size
        RemoteSortMode.TIME -> R.string.folder_books_sort_time
        RemoteSortMode.FORMAT -> R.string.folder_books_sort_format
    }
)

/** Sort menu with the same glass menu / dropdown fallback as the folder books page. */
@Composable
internal fun RemoteSortButton(
    mode: RemoteSortMode,
    ascending: Boolean,
    onModeChange: (RemoteSortMode) -> Unit,
    onAscendingChange: (Boolean) -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val menuHost = LocalLiquidGlassMenuHost.current
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val labels = RemoteSortMode.entries.associateWith { sortLabel(it) }
    val ascendingLabel = stringResource(R.string.folder_books_sort_ascending)
    val descendingLabel = stringResource(R.string.folder_books_sort_descending)
    val directional = mode != RemoteSortMode.DEFAULT
    val options: List<Triple<String, Boolean, () -> Unit>> = RemoteSortMode.entries.map { option ->
        Triple(labels.getValue(option), option == mode) { onModeChange(option) }
    } + if (directional) {
        listOf(
            Triple(ascendingLabel, ascending) { onAscendingChange(true) },
            Triple(descendingLabel, !ascending) { onAscendingChange(false) }
        )
    } else {
        emptyList()
    }

    DisposableEffect(menuHost) { onDispose { if (expanded) menuHost?.dismiss() } }
    Box {
        LiquidGlassIconButton(
            imageVector = AppIcons.SortAscending,
            contentDescription = stringResource(R.string.import_sort),
            onClick = {
                if (menuHost != null && anchor != Rect.Zero) {
                    if (expanded) {
                        menuHost.dismiss()
                    } else {
                        expanded = true
                        menuHost.show(
                            LiquidGlassMenuSpec(
                                anchorBounds = anchor,
                                width = 196.dp,
                                maxVisibleItems = 8,
                                onDismiss = { expanded = false },
                                items = options.map { (label, selected, action) ->
                                    LiquidGlassMenuItem(label = label, selected = selected, onClick = action)
                                }
                            )
                        )
                    }
                } else {
                    expanded = true
                }
            },
            size = 44.dp,
            iconSize = 20.dp,
            contentColor = if (mode == RemoteSortMode.DEFAULT) AppColors.TextPrimary else AppColors.Accent,
            normalContainerColor = AppColors.BgGray,
            liquidContainerColor = AppColors.CardBg,
            liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
            modifier = Modifier.liquidGlassMenuAnchor().onGloballyPositioned { anchor = it.boundsInRoot() }
        )
        if (!isLiquidGlass || menuHost == null) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(196.dp),
                shape = AppRoundedCornerShape(AppRadius.md),
                containerColor = AppColors.WindowBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                options.forEach { (label, selected, action) ->
                    DropdownMenuItem(
                        text = {
                            Text(label, color = if (selected) AppColors.Accent else AppColors.TextPrimary, fontSize = AppType.BodySmall)
                        },
                        onClick = {
                            expanded = false
                            action()
                        },
                        trailingIcon = if (selected) {
                            { Icon(AppIcons.CheckFilled, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}

/** Round layout toggle (list → 2 columns → 3 columns), same control as the folder books page. */
@Composable
internal fun RemoteLayoutButton(layoutMode: Int, onClick: () -> Unit) {
    LiquidGlassSurface(
        controlEdge = true,
        shape = CircleShape,
        fallbackColor = AppColors.BgGray,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
        onClick = onClick,
        effectPadding = 1.dp,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector = if (layoutMode == 1) AppIcons.List else AppIcons.SquaresFour,
            contentDescription = stringResource(R.string.import_layout_mode),
            tint = AppColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Accent capsule for select-all / done, matching the folder books header. */
@Composable
internal fun RemoteAccentPill(text: String, onClick: () -> Unit) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = AppRoundedCornerShape(AppRadius.capsule),
        tintedColor = AppColors.Accent,
        prominentShadow = false,
        contentColor = AppColors.OnAccent
    ) {
        Text(
            text = text,
            color = AppColors.OnAccent,
            fontSize = AppType.BodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun sortRemoteBooks(
    books: List<com.huangder.lumibooks.data.remote.RemoteBookItem>,
    mode: RemoteSortMode,
    ascending: Boolean
): List<com.huangder.lumibooks.data.remote.RemoteBookItem> {
    if (mode == RemoteSortMode.DEFAULT) return books
    val comparator: Comparator<com.huangder.lumibooks.data.remote.RemoteBookItem> = when (mode) {
        RemoteSortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        RemoteSortMode.SIZE -> compareBy { it.preferredFormat?.sizeBytes ?: 0L }
        RemoteSortMode.TIME -> compareBy<com.huangder.lumibooks.data.remote.RemoteBookItem> { it.lastModified }
            .thenBy { it.published.orEmpty() }
        RemoteSortMode.FORMAT -> compareBy { it.preferredFormat?.extension.orEmpty() }
        RemoteSortMode.DEFAULT -> error("unreachable")
    }
    val sorted = books.sortedWith(comparator.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    return if (ascending) sorted else sorted.reversed()
}
