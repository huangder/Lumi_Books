package com.huangder.lumibooks.ui.home.remote

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.WebdavTokenStore
import com.huangder.lumibooks.data.remote.OpdsClient
import com.huangder.lumibooks.data.remote.RemoteBookItem
import com.huangder.lumibooks.data.remote.RemoteBookFiles
import com.huangder.lumibooks.data.remote.RemoteDestination
import com.huangder.lumibooks.data.remote.RemoteDownloadManager
import com.huangder.lumibooks.data.remote.RemoteDownloadState
import com.huangder.lumibooks.data.remote.RemoteErrorKind
import com.huangder.lumibooks.data.remote.RemoteException
import com.huangder.lumibooks.data.remote.RemoteFormat
import com.huangder.lumibooks.data.remote.RemoteLibraryStore
import com.huangder.lumibooks.data.remote.RemotePage
import com.huangder.lumibooks.data.remote.RemoteSource
import com.huangder.lumibooks.data.remote.RemoteSourceType
import com.huangder.lumibooks.data.remote.WebdavLibraryClient
import com.huangder.lumibooks.domain.repository.FolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One browsing level; the stack mirrors folder navigation so back goes up a level. */
data class RemoteLevel(
    val url: String,
    val title: String,
    val page: RemotePage? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: RemoteErrorKind? = null
)

data class AuthorizedFolderOption(val treeUri: String, val name: String)

data class RemoteLibraryUiState(
    val type: RemoteSourceType = RemoteSourceType.OPDS,
    val sources: List<RemoteSource> = emptyList(),
    val activeSource: RemoteSource? = null,
    val levels: List<RemoteLevel> = emptyList(),
    val searchQuery: String? = null,
    val importedKeys: Set<String> = emptySet(),
    val downloadStates: Map<String, RemoteDownloadState> = emptyMap(),
    val authorizedFolders: List<AuthorizedFolderOption> = emptyList(),
    val libraryFolderName: String? = null,
    /** 1 = list, 2/3 = grid columns; shared with the folder books page preference. */
    val layoutMode: Int = 1,
    val sortMode: RemoteSortMode = RemoteSortMode.DEFAULT,
    val sortAscending: Boolean = true,
    /** Direction of the last level change, for the folder-books style level transition. */
    val navigatedForward: Boolean = true
)

@HiltViewModel
class RemoteLibraryViewModel @Inject constructor(
    private val application: Application,
    private val store: RemoteLibraryStore,
    private val opdsClient: OpdsClient,
    private val webdavClient: WebdavLibraryClient,
    private val downloads: RemoteDownloadManager,
    private val dataStoreManager: DataStoreManager,
    private val webdavTokenStore: WebdavTokenStore,
    private val folderRepository: FolderRepository
) : ViewModel() {
    private val browse = MutableStateFlow(RemoteLibraryUiState())
    private var loadJob: Job? = null
    private var libraryFolderId: String? = null

    val uiState: StateFlow<RemoteLibraryUiState> = combine(
        browse,
        store.state,
        downloads.states,
        dataStoreManager.authorizedBookDirectories,
        dataStoreManager.folderBooksLayoutMode
    ) { state, stored, downloadStates, directories, layoutMode ->
        val sources = stored.sources.filter { it.type == state.type }
        val active = state.activeSource?.let { current -> sources.firstOrNull { it.id == current.id } }
        state.copy(
            sources = sources,
            activeSource = active,
            levels = if (active == null) emptyList() else state.levels,
            importedKeys = active?.let { source ->
                state.levels.lastOrNull()?.page?.books.orEmpty()
                    .filter { RemoteBookFiles.remoteDocumentKey(source.id, it.key) in stored.importedBooks }
                    .mapTo(mutableSetOf()) { it.key }
            }.orEmpty(),
            downloadStates = downloadStates,
            authorizedFolders = directories.map { uri ->
                AuthorizedFolderOption(uri, authorizedFolderName(uri))
            },
            layoutMode = layoutMode.coerceIn(1, 3)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RemoteLibraryUiState())

    val batchFinished = downloads.batchFinished

    init {
        viewModelScope.launch { store.load() }
    }

    fun start(type: RemoteSourceType, targetFolderId: String?) {
        libraryFolderId = targetFolderId
        browse.update { it.copy(type = type) }
        if (targetFolderId != null) {
            viewModelScope.launch {
                val name = folderRepository.getAllFolders().first().firstOrNull { it.id == targetFolderId }?.name
                browse.update { it.copy(libraryFolderName = name) }
            }
        }
    }

    fun openSource(source: RemoteSource) {
        browse.update { it.copy(activeSource = source, levels = emptyList(), searchQuery = null, navigatedForward = true) }
        pushLevel(source.url, source.name)
    }

    fun openFolder(url: String, title: String) {
        browse.update { it.copy(navigatedForward = true) }
        pushLevel(url, title)
    }

    fun cycleLayoutMode() {
        val next = uiState.value.layoutMode % 3 + 1
        viewModelScope.launch { dataStoreManager.saveFolderBooksLayoutMode(next) }
    }

    fun setSortMode(mode: RemoteSortMode) = browse.update {
        it.copy(sortMode = mode, sortAscending = mode == RemoteSortMode.NAME || mode == RemoteSortMode.FORMAT || mode == RemoteSortMode.DEFAULT)
    }

    fun setSortAscending(ascending: Boolean) = browse.update { it.copy(sortAscending = ascending) }

    /** Returns false when already at the source list, so the Activity can finish. */
    fun navigateBack(): Boolean {
        val state = browse.value
        if (state.searchQuery != null) {
            browse.update { it.copy(searchQuery = null) }
            return true
        }
        if (state.activeSource == null) return false
        loadJob?.cancel()
        browse.update { current ->
            if (current.levels.size <= 1) {
                current.copy(activeSource = null, levels = emptyList(), navigatedForward = false)
            } else {
                current.copy(levels = current.levels.dropLast(1), navigatedForward = false)
            }
        }
        return true
    }

    fun setSearchQuery(query: String?) = browse.update { it.copy(searchQuery = query) }

    fun submitSearch() {
        val source = browse.value.activeSource ?: return
        val query = browse.value.searchQuery?.trim()?.takeIf(String::isNotBlank) ?: return
        val template = browse.value.levels.firstOrNull()?.page?.searchTemplate ?: return
        browse.update { it.copy(searchQuery = null) }
        val title = application.getString(R.string.remote_library_search_results, query)
        val level = RemoteLevel(url = "search:$query", title = title)
        browse.update { it.copy(levels = it.levels + level) }
        load(level) { opdsClient.search(source, store.password(source.id), template, query) }
    }

    fun retry() {
        val level = browse.value.levels.lastOrNull() ?: return
        if (level.url.startsWith("search:")) {
            browse.update { it.copy(levels = it.levels.dropLast(1)) }
            setSearchQuery(level.url.removePrefix("search:"))
            submitSearch()
        } else {
            browse.update { it.copy(levels = it.levels.dropLast(1)) }
            pushLevel(level.url, level.title)
        }
    }

    /** Reloads the current level in place (no level transition). */
    fun refresh() {
        val source = browse.value.activeSource ?: return
        val level = browse.value.levels.lastOrNull() ?: return
        if (level.url.startsWith("search:")) return
        updateTop { it.copy(loading = true, error = null) }
        load(level) {
            val password = store.password(source.id)
            when (source.type) {
                RemoteSourceType.OPDS -> opdsClient.loadPage(source, password, level.url)
                RemoteSourceType.WEBDAV -> webdavClient.list(source, password, level.url)
            }
        }
    }

    fun loadMore() {
        val source = browse.value.activeSource ?: return
        val level = browse.value.levels.lastOrNull() ?: return
        val next = level.page?.nextUrl ?: return
        if (level.loadingMore) return
        updateTop { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val more = runCatching { opdsClient.loadPage(source, store.password(source.id), next) }
            updateTop { top ->
                if (top.url != level.url) return@updateTop top
                more.fold(
                    onSuccess = { page ->
                        val current = top.page ?: page
                        top.copy(
                            loadingMore = false,
                            page = current.copy(
                                folders = (current.folders + page.folders).distinctBy { it.url },
                                books = (current.books + page.books).distinctBy { it.key },
                                nextUrl = page.nextUrl?.takeIf { it != next }
                            )
                        )
                    },
                    onFailure = { top.copy(loadingMore = false) }
                )
            }
        }
    }

    private fun pushLevel(url: String, title: String) {
        val source = browse.value.activeSource ?: return
        val level = RemoteLevel(url = url, title = title)
        browse.update { it.copy(levels = it.levels + level) }
        load(level) {
            val password = store.password(source.id)
            when (source.type) {
                RemoteSourceType.OPDS -> opdsClient.loadPage(source, password, url)
                RemoteSourceType.WEBDAV -> webdavClient.list(source, password, url)
            }
        }
    }

    private fun load(level: RemoteLevel, fetch: suspend () -> RemotePage) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = try {
                Result.success(fetch())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: RemoteException) {
                Result.failure(error)
            } catch (error: Exception) {
                Result.failure(RemoteException(RemoteErrorKind.UNKNOWN, cause = error))
            }
            updateTop { top ->
                if (top.url != level.url) return@updateTop top
                result.fold(
                    onSuccess = { page ->
                        top.copy(page = page, loading = false, error = null, title = top.title.ifBlank { page.title })
                    },
                    onFailure = { error ->
                        top.copy(loading = false, error = (error as? RemoteException)?.kind ?: RemoteErrorKind.UNKNOWN)
                    }
                )
            }
        }
    }

    private fun updateTop(transform: (RemoteLevel) -> RemoteLevel) {
        browse.update { state ->
            val top = state.levels.lastOrNull() ?: return@update state
            state.copy(levels = state.levels.dropLast(1) + transform(top))
        }
    }

    // ── Sources ──────────────────────────────────────────────────────

    fun hasPassword(sourceId: String): Boolean = store.password(sourceId) != null

    fun password(sourceId: String): String? = store.password(sourceId)

    /** Sync account prefill for the WebDAV form: (url, username, password). */
    suspend fun webdavSyncAccount(): Triple<String, String, String?>? {
        val config = dataStoreManager.webdavConfig.first().normalized()
        if (config.serverUrl.isBlank()) return null
        return Triple(config.serverUrl, config.username, webdavTokenStore.read())
    }

    /**
     * Tests the connection with the typed values and saves the source only when the library
     * answers. A blank password while editing keeps the stored one.
     */
    suspend fun testAndSave(
        existing: RemoteSource?,
        name: String,
        url: String,
        username: String,
        password: String
    ): RemoteErrorKind? {
        val type = browse.value.type
        val trimmedUrl = url.trim()
        val source = RemoteSource(
            id = existing?.id ?: UUID.randomUUID().toString(),
            type = type,
            name = name.trim().ifBlank { Uri.parse(trimmedUrl).host ?: trimmedUrl },
            url = trimmedUrl,
            username = username.trim(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        val effectivePassword = password.ifEmpty { existing?.let { store.password(it.id) }.orEmpty() }
        val error = try {
            when (type) {
                RemoteSourceType.OPDS -> opdsClient.loadPage(source, effectivePassword)
                RemoteSourceType.WEBDAV -> webdavClient.list(source, effectivePassword)
            }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RemoteException) {
            failure.kind
        } catch (_: Exception) {
            RemoteErrorKind.UNKNOWN
        }
        if (error == null) {
            store.saveSource(source, password.takeIf { it.isNotEmpty() || existing == null })
        }
        return error
    }

    fun deleteSource(source: RemoteSource) {
        viewModelScope.launch { store.deleteSource(source.id) }
    }

    // ── Downloads ────────────────────────────────────────────────────

    fun appDestination(): RemoteDestination = RemoteDestination.AppStorage(libraryFolderId)

    fun download(items: List<Pair<RemoteBookItem, RemoteFormat>>, destination: RemoteDestination) {
        val source = browse.value.activeSource ?: return
        downloads.enqueue(source, items, destination)
    }

    fun cancelDownload(itemKey: String) = downloads.cancel(itemKey)

    /** Persists a freshly picked folder like the import sheet's "authorize a folder" flow. */
    suspend fun authorizeFolder(treeUri: Uri): AuthorizedFolderOption? = runCatching {
        application.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        dataStoreManager.addAuthorizedBookDirectory(treeUri.toString())
        AuthorizedFolderOption(treeUri.toString(), authorizedFolderName(treeUri.toString()))
    }.getOrNull()

    private fun authorizedFolderName(uri: String): String =
        authorizedFolderDisplayName(uri, application.getString(R.string.bookshelf_title))
}
