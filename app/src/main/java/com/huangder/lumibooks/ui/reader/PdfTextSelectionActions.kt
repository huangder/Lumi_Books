package com.huangder.lumibooks.ui.reader

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.excerpt.ExcerptRequest
import com.huangder.lumibooks.ui.excerpt.ExcerptSheet
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal val PdfSelectionSupportedActions = com.huangder.lumibooks.domain.model.SelectionMenuAction.entries
    .filterNot { it == com.huangder.lumibooks.domain.model.SelectionMenuAction.REPLACE }.toSet()

@Stable
internal class PdfSelectionActions {
    var excerpt by mutableStateOf<ExcerptRequest?>(null)
    var dictionary by mutableStateOf<String?>(null)
    var webQuery by mutableStateOf<String?>(null)
    var externalDictionary by mutableStateOf<DictionaryLookupRequest?>(null)
    var searchVisible by mutableStateOf(false)
    var query by mutableStateOf("")
    var submittedQuery by mutableStateOf("")
    var revision by mutableIntStateOf(0)
    val sheetOpen get() = excerpt != null || dictionary != null || webQuery != null || searchVisible

    fun excerpt(book: Book, text: String, note: String, pageTitle: String) {
        excerpt = ExcerptRequest(book.title, book.author, pageTitle, text, note, book.coverPath)
    }

    fun search(text: String) {
        query = text
        submittedQuery = text.trim()
        revision++
        searchVisible = true
    }
}

/** Selection actions use the same sheets and dictionary integrations as the text reader. */
@Composable
internal fun PdfSelectionActionSheets(
    actions: PdfSelectionActions, controller: PdfTextAnnotationController, pageCount: Int,
    backdrop: Backdrop?, onJump: (Note) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var results by remember(controller) { mutableStateOf<List<ReaderViewModel.SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    LaunchedEffect(actions.searchVisible, actions.revision) {
        if (!actions.searchVisible) { searching = false; return@LaunchedEffect }
        results = emptyList()
        searched = false
        val query = actions.submittedQuery
        if (query.isEmpty()) return@LaunchedEffect
        searching = true
        try {
            results = searchPdfText(controller.session, pageCount, query, pageTitle = { page ->
                context.getString(R.string.pdf_page_number, page + 1)
            })
            searched = true
        } finally { searching = false }
    }
    SearchSheet(visible = actions.searchVisible, query = actions.query, results = results,
        isSearching = searching, hasSearched = searched,
        onQueryChange = { actions.query = it },
        onSearch = { actions.submittedQuery = actions.query.trim(); actions.revision++ },
        onResultClick = { result ->
            scope.launch {
                val model = try { controller.session.page(result.chapterIndex) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                val locator = model?.locator(result.charOffset, result.charOffset + result.matchLength)
                actions.searchVisible = false
                onJump(Note(bookId = "", chapterIndex = result.chapterIndex,
                    startPosition = locator?.start ?: result.charOffset,
                    endPosition = locator?.end ?: (result.charOffset + result.matchLength),
                    selectedText = locator?.quote ?: actions.submittedQuery,
                    startLocatorJson = locator?.encode(), endLocatorJson = locator?.encode(),
                    note = "", color = DefaultReaderHighlightColorWithAlpha, createdAt = System.currentTimeMillis()))
            }
        }, onDismiss = { actions.searchVisible = false })
    LocalDictionarySheet(query = actions.dictionary, glassBackdrop = backdrop, forceSolid = false,
        onDismiss = { actions.dictionary = null; controller.clear() },
        onExternal = {
            val request = prepareDictionaryLookup(context, actions.dictionary.orEmpty())
            if (request == null || request.apps.isEmpty()) {
                Toast.makeText(context, R.string.dictionary_no_app, Toast.LENGTH_SHORT).show()
            } else { actions.externalDictionary = request; actions.dictionary = null }
        },
        onWebSearch = { actions.webQuery = actions.dictionary; actions.dictionary = null; controller.clear() })
    NetworkSearchSheet(visible = actions.webQuery != null, initialQuery = actions.webQuery.orEmpty(),
        onDismiss = { actions.webQuery = null })
    actions.excerpt?.let { ExcerptSheet(it, onClose = { actions.excerpt = null }, glassBackdrop = backdrop) }
}

/** Explicit searches scan lazily and stop on cancellation or the result limit. */
internal suspend fun searchPdfText(
    session: PdfTextSession, pageCount: Int, query: String, pageTitle: (Int) -> String,
    limit: Int = 200
): List<ReaderViewModel.SearchResult> {
    if (query.isBlank() || limit <= 0) return emptyList()
    val results = mutableListOf<ReaderViewModel.SearchResult>()
    for (page in 0 until pageCount) {
        currentCoroutineContext().ensureActive()
        val model = try { session.page(page) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } ?: continue
        var offset = model.text.indexOf(query, ignoreCase = true)
        while (offset >= 0) {
            results += ReaderViewModel.SearchResult(page, pageTitle(page), offset,
                model.text.substring((offset - 35).coerceAtLeast(0),
                    (offset + query.length + 35).coerceAtMost(model.text.length)), query.length)
            if (results.size >= limit) return results
            offset = model.text.indexOf(query, offset + query.length, ignoreCase = true)
        }
    }
    return results
}
