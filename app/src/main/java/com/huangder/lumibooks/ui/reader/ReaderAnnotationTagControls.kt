package com.huangder.lumibooks.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.components.AnnotationTagSheet
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled

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
    val motionEnabled = LocalMotionEnabled.current && !LocalEInkMode.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    // Keep the visibility host composed so clearing the target can finish its exit.
    AnimatedVisibility(
        visible = quickTarget != null,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        enter = if (motionEnabled) {
            fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 }
        } else EnterTransition.None,
        exit = if (motionEnabled) {
            fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 }
        } else ExitTransition.None
    ) {
        ProvideLiquidGlassBackdrop(backdrop) {
            LiquidGlassTextButton(
                text = stringResource(R.string.annotation_tag_add),
                onClick = { quickTarget?.let(onQuickClick) },
                tintedColor = if (isLiquidGlass) null else AppColors.CardBg,
                contentColor = AppColors.TextPrimary
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
            onCreate = viewModel::createAnnotationTag,
            onSave = { tags ->
                if (isBookmark) viewModel.saveBookmarkTags(syncId, tags)
                else viewModel.saveNoteTags(syncId, tags)
            },
            onDismiss = onEditorDismiss,
            onRename = viewModel::renameAnnotationTag,
            onDelete = viewModel::deleteAnnotationTag
        )
    }
}
