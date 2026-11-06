package com.huangder.lumibooks.ui.privacy

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.components.CollapsingPageScaffold
import com.huangder.lumibooks.ui.components.GroupedCard
import com.huangder.lumibooks.ui.settings.DetailActivity
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppType
import kotlin.math.roundToInt

internal fun openArchivedBooks(context: Context) {
    context.startActivity(DetailActivity.createIntent(context, "archived_books"))
}

@Composable
internal fun ArchivedBooksRoute(onBack: () -> Unit, viewModel: ArchivedBooksViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.messageRes) {
        state.messageRes?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessage() }
    }
    ArchivedBooksContent(state, onBack, viewModel::restore, viewModel::reload)
}

@Composable
internal fun ArchivedBooksContent(
    state: ArchivedBooksUiState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onRetry: () -> Unit
) {
    CollapsingPageScaffold(title = stringResource(R.string.archived_books_title), onBack = onBack) {
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.archive_summary), Modifier.padding(horizontal = 28.dp),
                    color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
            }
            if (state.isLoading) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Accent)
                }
            }
            state.errorRes?.let { error -> item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                    Text(stringResource(error), color = AppColors.Destructive)
                    if (error == R.string.archive_load_failed) TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.archive_retry), color = AppColors.Accent)
                    }
                }
            } }
            if (!state.isLoading && state.books.isEmpty() && state.errorRes == null) item {
                Text(stringResource(R.string.archive_empty), Modifier.fillMaxWidth().padding(28.dp),
                    color = AppColors.TextSecondary, fontSize = AppType.Body)
            }
            items(state.books, key = { it.id }) { book ->
                ArchivedBookRow(book, book.id in state.restoringIds) { onRestore(book.id) }
            }
        }
    }
}

@Composable
private fun ArchivedBookRow(book: Book, restoring: Boolean, onRestore: () -> Unit) {
    GroupedCard {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp, 66.dp).clip(AppRoundedCornerShape(6.dp)).background(AppColors.PageBg)) {
                AsyncImage(model = book.coverPath, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = AppColors.TextPrimary, fontSize = AppType.Body)
                Text(book.author, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = AppColors.TextSecondary, fontSize = AppType.Caption)
                val progress = book.readingProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                Text("${(progress * 100).roundToInt()}%", color = AppColors.TextSecondary, fontSize = AppType.Caption)
            }
            TextButton(onClick = onRestore, enabled = !restoring) {
                Text(stringResource(if (restoring) R.string.archive_restoring else R.string.restore_book),
                    color = AppColors.Accent)
            }
        }
    }
}
