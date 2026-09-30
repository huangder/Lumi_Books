package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.kyant.backdrop.Backdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.components.AnnotationTagSheet
import com.huangder.lumibooks.ui.theme.AppColors

@Stable
internal class ReaderAnnotationTagState {
    var quickTarget by mutableStateOf<Pair<String, Boolean>?>(null)
    var editingTarget by mutableStateOf<Pair<String, Boolean>?>(null)
    fun edit(note: Note) { editingTarget = note.syncId to false }
    fun editBookmark(bookmark: Bookmark) { editingTarget = bookmark.syncId to true }
    fun dismissEditor() { editingTarget = null }
    fun openQuick(target: Pair<String, Boolean>) { editingTarget = target; quickTarget = null }
}

@Composable
internal fun rememberReaderAnnotationTagState(bookId: String): ReaderAnnotationTagState {
    val state = remember(bookId) { ReaderAnnotationTagState() }
    LaunchedEffect(state.quickTarget) {
        if (state.quickTarget != null) { delay(5_000L); state.quickTarget = null }
    }
    return state
}

@Composable
internal fun BoxScope.ReaderAnnotationTagControls(
    quickTarget: Pair<String, Boolean>?,
    editingTarget: Pair<String, Boolean>?,
    bookmarks: List<Bookmark>,
    notes: List<Note>,
    availableTags: List<String>,
    viewModel: ReaderViewModel,
    onQuickClick: (Pair<String, Boolean>) -> Unit,
    onEditorDismiss: () -> Unit,
    backdrop: Backdrop? = null
) {
    if (quickTarget != null) {
        ProvideLiquidGlassBackdrop(backdrop) {
            LiquidGlassTextButton(
                text = stringResource(R.string.annotation_tag_add),
                onClick = { onQuickClick(quickTarget) },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
                tintedColor = AppColors.Accent
            )
        }
    }
    if (editingTarget != null) {
        val (syncId, isBookmark) = editingTarget
        val currentTags = if (isBookmark) bookmarks.firstOrNull { it.syncId == syncId }?.tags.orEmpty()
            else notes.firstOrNull { it.syncId == syncId }?.tags.orEmpty()
        AnnotationTagSheet(
            backdrop = backdrop,
            selected = currentTags,
            available = availableTags,
            onSave = { tags ->
                if (isBookmark) viewModel.updateBookmarkTags(syncId, tags)
                else viewModel.updateNoteTags(syncId, tags)
                onEditorDismiss()
            },
            onDismiss = onEditorDismiss,
            onRename = viewModel::renameAnnotationTag,
            onDelete = viewModel::deleteAnnotationTag
        )
    }
}
