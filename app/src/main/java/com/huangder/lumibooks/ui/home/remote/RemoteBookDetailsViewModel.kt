package com.huangder.lumibooks.ui.home.remote

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.remote.LocalBookMetadata
import com.huangder.lumibooks.data.remote.OpdsClient
import com.huangder.lumibooks.data.remote.RemoteBookFiles
import com.huangder.lumibooks.data.remote.RemoteBookItem
import com.huangder.lumibooks.data.remote.RemoteBookMetadataReader
import com.huangder.lumibooks.data.remote.RemoteDestination
import com.huangder.lumibooks.data.remote.RemoteDownloadManager
import com.huangder.lumibooks.data.remote.RemoteDownloadState
import com.huangder.lumibooks.data.remote.RemoteErrorKind
import com.huangder.lumibooks.data.remote.RemoteException
import com.huangder.lumibooks.data.remote.RemoteFormat
import com.huangder.lumibooks.data.remote.RemoteLibraryStore
import com.huangder.lumibooks.data.remote.RemoteSource
import com.huangder.lumibooks.data.remote.RemoteSourceType
import com.huangder.lumibooks.data.remote.WebdavLibraryClient
import com.huangder.lumibooks.domain.repository.FolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RemoteBookDetailsUiState(
    val source: RemoteSource? = null,
    val item: RemoteBookItem? = null,
    val selectedFormatUrl: String? = null,
    /** Sizes learned with HEAD for formats the catalog did not describe. */
    val probedSizes: Map<String, Long> = emptyMap(),
    val localMetadata: LocalBookMetadata? = null,
    val readingMetadata: Boolean = false,
    val metadataError: RemoteErrorKind? = null,
    /** WebDAV metadata needs the file itself; large files or metered links wait for the user. */
    val metadataNeedsConfirmation: Boolean = false,
    val importedBookId: String? = null,
    val downloadState: RemoteDownloadState? = null,
    val authorizedFolders: List<AuthorizedFolderOption> = emptyList(),
    val libraryFolderName: String? = null
) {
    val selectedFormat: RemoteFormat?
        get() = item?.supportedFormats?.firstOrNull { it.url == selectedFormatUrl } ?: item?.preferredFormat

    fun sizeOf(format: RemoteFormat?): Long =
        format?.let { it.sizeBytes.takeIf { size -> size > 0L } ?: probedSizes[it.url] } ?: 0L
}

@HiltViewModel
class RemoteBookDetailsViewModel @Inject constructor(
    private val application: Application,
    private val store: RemoteLibraryStore,
    private val opdsClient: OpdsClient,
    private val webdavClient: WebdavLibraryClient,
    private val downloads: RemoteDownloadManager,
    private val dataStoreManager: DataStoreManager,
    private val folderRepository: FolderRepository
) : ViewModel() {
    private val local = MutableStateFlow(RemoteBookDetailsUiState())
    private var targetFolderId: String? = null
    private var started = false
    private val previewDirectory = File(application.cacheDir, "remote_preview").apply { mkdirs() }
    private var previewFile: File? = null

    val uiState: StateFlow<RemoteBookDetailsUiState> = combine(
        local,
        store.state,
        downloads.states,
        dataStoreManager.authorizedBookDirectories
    ) { state, stored, downloadStates, directories ->
        val source = state.source
        val item = state.item
        val rememberedBookId = if (source != null && item != null) {
            stored.importedBooks[RemoteBookFiles.remoteDocumentKey(source.id, item.key)]
        } else {
            null
        }
        val downloadState = item?.let { downloadStates[it.key] }
        state.copy(
            importedBookId = (downloadState as? RemoteDownloadState.Completed)?.bookId ?: rememberedBookId,
            downloadState = downloadState,
            authorizedFolders = directories.map { uri -> AuthorizedFolderOption(uri, folderName(uri)) }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RemoteBookDetailsUiState())

    fun start(source: RemoteSource, item: RemoteBookItem, libraryFolderId: String?) {
        if (started) return
        started = true
        targetFolderId = libraryFolderId
        local.value = RemoteBookDetailsUiState(source = source, item = item, selectedFormatUrl = item.preferredFormat?.url)
        viewModelScope.launch { store.load() }
        if (libraryFolderId != null) {
            viewModelScope.launch {
                val name = folderRepository.getAllFolders().first().firstOrNull { it.id == libraryFolderId }?.name
                local.update { it.copy(libraryFolderName = name) }
            }
        }
        if (source.type == RemoteSourceType.OPDS) {
            probeMissingSizes(source, item)
        } else {
            val format = item.preferredFormat
            val extension = format?.extension
            if (extension == "epub" || extension == "cbz") {
                val small = format.sizeBytes in 1..AUTO_METADATA_MAX_BYTES
                val metered = (application.getSystemService(ConnectivityManager::class.java))?.isActiveNetworkMetered != false
                if (small && !metered) readMetadata() else local.update { it.copy(metadataNeedsConfirmation = true) }
            }
        }
    }

    fun selectFormat(format: RemoteFormat) = local.update { it.copy(selectedFormatUrl = format.url) }

    fun password(sourceId: String): String? = store.password(sourceId)

    /** Downloads a WebDAV EPUB/CBZ into the cache to read its description and cover. */
    fun readMetadata() {
        val state = local.value
        val source = state.source ?: return
        val item = state.item ?: return
        val format = item.preferredFormat ?: return
        if (state.readingMetadata) return
        local.update { it.copy(readingMetadata = true, metadataError = null, metadataNeedsConfirmation = false) }
        viewModelScope.launch {
            val result = runCatching {
                val file = File(previewDirectory, "${RemoteBookFiles.sha256Hex(item.key).take(24)}.${format.extension}")
                if (!file.isFile || file.length() != format.sizeBytes) {
                    webdavClient.download(source, store.password(source.id), format.url, file, { false }) { _, _ -> }
                }
                previewFile = file
                withContext(Dispatchers.IO) {
                    RemoteBookMetadataReader.read(
                        application,
                        file,
                        format.extension.orEmpty(),
                        File(previewDirectory, "${file.nameWithoutExtension}_cover.jpg")
                    )
                }
            }
            local.update { current ->
                result.fold(
                    onSuccess = { metadata -> current.copy(readingMetadata = false, localMetadata = metadata ?: LocalBookMetadata()) },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        current.copy(
                            readingMetadata = false,
                            metadataError = (error as? RemoteException)?.kind ?: RemoteErrorKind.UNKNOWN
                        )
                    }
                )
            }
        }
    }

    fun appDestination(): RemoteDestination = RemoteDestination.AppStorage(targetFolderId)

    fun download(destination: RemoteDestination) {
        val state = uiState.value
        val source = state.source ?: return
        val item = state.item ?: return
        val format = state.selectedFormat ?: return
        // A cached WebDAV preview is the exact file; reuse it instead of downloading twice.
        val prefetched = previewFile
            ?.takeIf { source.type == RemoteSourceType.WEBDAV && it.isFile && format.url == item.preferredFormat?.url }
            ?.let { mapOf(item.key to it) }
            .orEmpty()
        downloads.enqueue(source, listOf(item to format), destination, prefetched)
    }

    fun cancelDownload() {
        uiState.value.item?.let { downloads.cancel(it.key) }
    }

    fun clearFailure() {
        uiState.value.item?.let { downloads.clearFinished(it.key) }
    }

    suspend fun authorizeFolder(treeUri: Uri): AuthorizedFolderOption? = runCatching {
        application.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        dataStoreManager.addAuthorizedBookDirectory(treeUri.toString())
        AuthorizedFolderOption(treeUri.toString(), folderName(treeUri.toString()))
    }.getOrNull()

    private fun probeMissingSizes(source: RemoteSource, item: RemoteBookItem) {
        val missing = item.supportedFormats.filter { it.sizeBytes <= 0L }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val password = store.password(source.id)
            missing.forEach { format ->
                val size = runCatching { opdsClient.contentLength(source, password, format.url) }.getOrDefault(0L)
                if (size > 0L) local.update { it.copy(probedSizes = it.probedSizes + (format.url to size)) }
            }
        }
    }

    private fun folderName(uri: String): String =
        authorizedFolderDisplayName(uri, application.getString(R.string.bookshelf_title))

    override fun onCleared() {
        // Previews are only useful while the page is open; the import copies what it needs.
        previewDirectory.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val AUTO_METADATA_MAX_BYTES = 30L * 1024 * 1024
    }
}
