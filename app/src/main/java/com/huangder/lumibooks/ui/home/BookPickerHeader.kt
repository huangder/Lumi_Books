package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.ui.icons.ControlIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.*

/** Shared toolbar and status line for local and remote book selection. */
@Composable
internal fun BookPickerHeader(
    path: String,
    levelSummary: String,
    isScanning: Boolean,
    isSearching: Boolean,
    searchQuery: String,
    layoutMode: Int,
    onRefresh: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLayoutModeChange: () -> Unit,
    sortControl: @Composable () -> Unit,
    searchPlaceholder: String = stringResource(R.string.import_search_placeholder),
    searchTag: String = "folder-search-pill",
    onSearchSubmit: () -> Unit = {}
) {
    val layoutModeLabel = stringResource(R.string.import_layout_mode)
    // Same structure as the Library header: one toolbar row (search + a tidy cluster of equal
    // round tools), then one quiet status line with the text action on the right.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppSpace.sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookPickerSearchPill(
                query = searchQuery,
                expanded = isSearching,
                contentAlpha = 1f,
                onQueryChange = onSearchQueryChange,
                onExpand = onSearchOpen,
                placeholder = searchPlaceholder,
                onSubmit = onSearchSubmit,
                modifier = Modifier
                    .testTag(searchTag)
                    .weight(1f)
                    .height(44.dp)
            )
            Spacer(Modifier.width(AppSpace.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                sortControl()
                LiquidGlassIconButton(
                    imageVector = if (layoutMode == 1) AppIcons.List else AppIcons.SquaresFour,
                    contentDescription = layoutModeLabel,
                    onClick = onLayoutModeChange,
                    size = 44.dp,
                    iconSize = 20.dp,
                    normalContainerColor = AppColors.BgGray,
                    liquidContainerColor = AppColors.CardBg,
                    liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f)
                )
                if (isScanning) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(AppColors.BgGray),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AppColors.Accent,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    LiquidGlassIconButton(
                        imageVector = AppIcons.ArrowClockwise,
                        contentDescription = stringResource(R.string.import_folder_books_refresh),
                        onClick = onRefresh,
                        size = 44.dp,
                        iconSize = 20.dp,
                        normalContainerColor = AppColors.BgGray,
                        liquidContainerColor = AppColors.CardBg,
                        liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .padding(horizontal = AppSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (path.isNotBlank()) {
                    Text(path, color = AppColors.TextSecondary, fontSize = AppType.Caption2,
                        maxLines = 1, overflow = TextOverflow.StartEllipsis)
                }
                Text(levelSummary, color = AppColors.TextSecondary, fontSize = AppType.Footnote,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

        }
        Spacer(Modifier.height(AppSpace.xs))
    }
}

@Composable
private fun BookPickerSearchPill(
    query: String,
    expanded: Boolean,
    contentAlpha: Float,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val displayedQuery = if (expanded) query else ""
    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
    LiquidGlassSurface(
        controlEdge = true,
        shape = CircleShape,
        fallbackColor = AppColors.BgGray,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
        onClick = if (expanded) null else onExpand,
        effectPadding = 1.dp,
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (expanded || contentAlpha > 0f) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(contentAlpha)
                    .padding(start = AppSpace.md, end = AppSpace.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlIcon(
                    imageVector = AppIcons.MagnifyingGlass,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(AppSpace.sm))
                BasicTextField(
                    value = displayedQuery,
                    onValueChange = onQueryChange,
                    enabled = expanded,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(AppColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        onSubmit()
                    }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (displayedQuery.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = AppColors.TextSecondary,
                                    fontSize = AppType.BodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (displayedQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        ControlIcon(
                            imageVector = AppIcons.X,
                            contentDescription = stringResource(R.string.import_search_clear),
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ControlIcon(
                    imageVector = AppIcons.MagnifyingGlass,
                    contentDescription = stringResource(R.string.search),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

