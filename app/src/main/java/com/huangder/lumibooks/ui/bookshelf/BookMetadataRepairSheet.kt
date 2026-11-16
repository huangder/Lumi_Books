package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.AppBottomSheet
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.appSheetMaxHeight
import com.huangder.lumibooks.ui.components.appSheetPadding
import com.huangder.lumibooks.ui.home.BookMetadataRepairResult
import com.huangder.lumibooks.ui.home.BookMetadataSuggestion
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BookMetadataRepairSheet(
    bookIds: Set<String>,
    viewModel: HomeViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var suggestions by remember(bookIds) { mutableStateOf<List<BookMetadataSuggestion>?>(null) }
    var checkedIds by remember(bookIds) { mutableStateOf(emptySet<String>()) }
    var previewFailed by remember(bookIds) { mutableStateOf(false) }
    var applying by remember(bookIds) { mutableStateOf(false) }
    var result by remember(bookIds) { mutableStateOf<BookMetadataRepairResult?>(null) }

    LaunchedEffect(bookIds) {
        try {
            val preview = viewModel.previewBookMetadataRepairs(bookIds)
            checkedIds = preview.mapTo(mutableSetOf()) { it.book.id }
            suggestions = preview
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("BookMetadataRepair", "Unable to load preview", error)
            previewFailed = true
        }
    }

    AppBottomSheet(onDismissRequest = { if (!applying) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = appSheetMaxHeight()).padding(appSheetPadding()),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md)
        ) {
            Text(
                stringResource(R.string.book_metadata_repair_title),
                color = AppColors.TextPrimary,
                fontSize = AppType.Section,
                fontWeight = FontWeight.SemiBold
            )
            val preview = suggestions
            val applied = result
            when {
                applied != null -> {
                    MetadataRepairMessage(stringResource(R.string.book_metadata_repair_updated, applied.updated))
                    if (applied.skipped > 0) {
                        MetadataRepairMessage(stringResource(R.string.book_metadata_repair_changed, applied.skipped))
                    }
                    if (applied.failed > 0) {
                        MetadataRepairMessage(stringResource(R.string.book_metadata_repair_failed, applied.failed))
                    }
                }
                previewFailed -> MetadataRepairMessage(stringResource(R.string.book_metadata_repair_preview_error))
                preview == null || applying -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = AppColors.Accent
                )
                preview.isEmpty() -> MetadataRepairMessage(stringResource(R.string.book_metadata_repair_empty))
                else -> {
                    if (bookIds.size > preview.size) {
                        MetadataRepairMessage(
                            stringResource(R.string.book_metadata_repair_no_suggestion, bookIds.size - preview.size)
                        )
                    }
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(preview, key = { it.book.id }) { suggestion ->
                            val id = suggestion.book.id
                            MetadataSuggestionRow(suggestion, checked = id in checkedIds) { checked ->
                                checkedIds = if (checked) checkedIds + id else checkedIds - id
                            }
                            HorizontalDivider(color = AppColors.TextSecondary.copy(alpha = 0.16f))
                        }
                    }
                }
            }
            if (!applying) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                    val canApply = applied == null && !preview.isNullOrEmpty()
                    LiquidGlassButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(
                            stringResource(if (applied != null || preview?.isEmpty() == true) R.string.done else R.string.cancel)
                        )
                    }
                    if (canApply && preview != null) {
                        LiquidGlassButton(
                            onClick = {
                                if (!applying) {
                                    applying = true
                                    val selected = preview.filter { it.book.id in checkedIds }
                                    scope.launch {
                                        try {
                                            result = viewModel.applyBookMetadataRepairs(selected)
                                        } finally {
                                            applying = false
                                        }
                                    }
                                }
                            },
                            enabled = checkedIds.isNotEmpty(),
                            tintedColor = AppColors.Accent,
                            contentColor = AppColors.OnAccent,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.book_metadata_repair_apply, checkedIds.size))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataSuggestionRow(
    suggestion: BookMetadataSuggestion,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(suggestion.fileName, color = AppColors.TextPrimary, fontSize = AppType.Body, fontWeight = FontWeight.Medium)
            if (suggestion.title != suggestion.book.title) {
                MetadataRepairMessage(stringResource(
                    R.string.book_metadata_repair_field_change,
                    stringResource(R.string.book_title_label),
                    suggestion.book.title.ifBlank { "—" },
                    suggestion.title
                ))
            }
            if (suggestion.author != suggestion.book.author) {
                MetadataRepairMessage(stringResource(
                    R.string.book_metadata_repair_field_change,
                    stringResource(R.string.book_author_label),
                    suggestion.book.author.ifBlank { "—" },
                    suggestion.author
                ))
            }
        }
    }
}

@Composable
private fun MetadataRepairMessage(text: String) {
    Text(text, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
}
