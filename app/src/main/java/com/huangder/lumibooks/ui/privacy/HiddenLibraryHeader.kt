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
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

/** Pinned header of the hidden library: shared selection header (actions sit in the bottom bar). */
@Composable
internal fun HiddenLibraryHeader(
    bookCount: Int,
    isEditing: Boolean,
    selectedCount: Int,
    onEditToggle: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    allSelected: Boolean = false
) {
    com.huangder.lumibooks.ui.components.SelectionHeader(
        summary = pluralStringResource(R.plurals.hidden_library_book_count, bookCount, bookCount),
        isEditing = isEditing,
        selectedCount = selectedCount,
        allSelected = allSelected,
        onEditToggle = onEditToggle,
        onSelectAll = onSelectAll,
        modifier = modifier,
        leadingIcon = AppIcons.Lock,
        showEdit = bookCount > 0
    )
}

/** Bottom actions of the hidden library while selecting. */
@Composable
internal fun HiddenLibraryActionBar(isEditing: Boolean, selectedCount: Int, onUnhide: () -> Unit, onDelete: () -> Unit) {
    com.huangder.lumibooks.ui.components.SelectionActionBar(
        visible = isEditing,
        enabled = selectedCount > 0,
        actions = listOf(
            com.huangder.lumibooks.ui.components.SelectionAction(AppIcons.Eye, stringResource(R.string.hidden_library_unhide), onClick = onUnhide),
            com.huangder.lumibooks.ui.components.SelectionAction(AppIcons.Trash, stringResource(R.string.delete), destructive = true, onClick = onDelete)
        )
    )
}
