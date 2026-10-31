package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LibraryTag
import com.huangder.lumibooks.domain.model.TagTypes
import com.huangder.lumibooks.ui.components.AnnotationTagSheet
import com.huangder.lumibooks.ui.components.rememberTagPickerRepository

/** Both book entry points share the category browser used by annotation editors. */
@Composable
fun BookTagBottomSheet(
    tags: List<LibraryTag>,
    selectedTagIds: Set<String>,
    onDismiss: () -> Unit,
    onTagCheckedChange: (LibraryTag, Boolean) -> Unit,
    onCreateTag: (String, String?) -> Unit,
    onDeleteTag: (LibraryTag, Boolean) -> Unit,
    bookId: String? = null
) {
    val repository = rememberTagPickerRepository()
    AnnotationTagSheet(
        selected = selectedTagIds.toList(), available = tags.map { it.id },
        catalogType = TagTypes.BOOK, onDismiss = onDismiss,
        onSave = { keys ->
            if (repository != null && bookId != null) {
                val result = repository.replaceBookTags(bookId, keys.toSet())
                check(result.success) { result.message.orEmpty() }
            } else {
                tags.forEach { tag ->
                    val checked = tag.id in keys
                    if (checked != (tag.id in selectedTagIds)) onTagCheckedChange(tag, checked)
                }
            }
        },
        onCreate = { onCreateTag(it, null) },
        onDelete = { key -> tags.firstOrNull { it.id == key }?.let { onDeleteTag(it, false) } }
    )
}

@Composable
fun BatchBookTagSheet(
    tags: List<LibraryTag>,
    selectedBookCount: Int,
    onDismiss: () -> Unit,
    onCreateTag: (String) -> Unit,
    onApply: (Set<String>) -> Unit,
    bookIds: Set<String> = emptySet()
) {
    val repository = rememberTagPickerRepository()
    AnnotationTagSheet(
        selected = emptyList(), available = tags.map { it.id },
        catalogType = TagTypes.BOOK,
        subtitle = stringResource(R.string.selected_books_count, selectedBookCount),
        onDismiss = onDismiss, onCreate = { onCreateTag(it) },
        onSave = { keys ->
            if (repository != null && bookIds.isNotEmpty()) {
                val result = repository.addTagsToBooks(bookIds, keys.toSet())
                check(result.success) { result.message.orEmpty() }
            } else onApply(keys.toSet())
        }
    )
}
