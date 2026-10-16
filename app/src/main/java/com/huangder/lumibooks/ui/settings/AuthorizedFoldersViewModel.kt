package com.huangder.lumibooks.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.util.AuthorizedFolderSnapshotStore
import com.huangder.lumibooks.util.AuthorizedStorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class AuthorizedFolderEntry(val uri: String, val name: String, val path: String)

internal data class AuthorizedFoldersUiState(
    val folders: List<AuthorizedFolderEntry> = emptyList(),
    val loading: Boolean = true,
    val removalFailed: Boolean = false,
    val retryKey: Int = 0
)

@HiltViewModel
class AuthorizedFoldersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val snapshotStore: AuthorizedFolderSnapshotStore,
    private val storageManager: AuthorizedStorageManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthorizedFoldersUiState())
    internal val uiState: StateFlow<AuthorizedFoldersUiState> = _uiState.asStateFlow()
    private val removing = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            dataStoreManager.authorizedBookDirectories.collectLatest { directories ->
                val folders = withContext(Dispatchers.IO) {
                    val snapshot = snapshotStore.load()
                    directories.map { value ->
                        val uri = Uri.parse(value)
                        val path = runCatching { DocumentsContract.getTreeDocumentId(uri) }
                            .getOrDefault(Uri.decode(value))
                        val name = runCatching {
                            storageManager.queryDisplayName(context, storageManager.treeRootUri(uri))
                        }.getOrNull()?.takeIf(String::isNotBlank)
                            ?: snapshot.trees[value]?.rootName?.takeIf(String::isNotBlank)
                            ?: path.substringAfter(':').trim('/').substringAfterLast('/').ifBlank { path }
                        AuthorizedFolderEntry(value, name, path)
                    }
                }
                _uiState.value = _uiState.value.copy(folders = folders, loading = false)
            }
        }
    }

    internal fun removeFolder(folder: AuthorizedFolderEntry) {
        if (!removing.add(folder.uri)) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    storageManager.operationMutex.withLock {
                        val uri = Uri.parse(folder.uri)
                        val resolver = context.contentResolver
                        // Release the exact root and only the modes that were actually persisted.
                        // Missing grants (e.g. removed storage) can still be forgotten from the list.
                        resolver.persistedUriPermissions.firstOrNull { it.uri == uri }?.let { grant ->
                            val flags = (if (grant.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                                (if (grant.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                            if (flags != 0) resolver.releasePersistableUriPermission(uri, flags)
                        }
                        dataStoreManager.removeAuthorizedBookDirectory(folder.uri)
                        snapshotStore.removeTree(folder.uri)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Recreate the swipe container so a failed deletion remains visible and retryable.
                _uiState.value = _uiState.value.copy(
                    removalFailed = true,
                    retryKey = _uiState.value.retryKey + 1
                )
            } finally {
                removing.remove(folder.uri)
            }
        }
    }

    internal fun clearRemovalError() {
        _uiState.value = _uiState.value.copy(removalFailed = false)
    }
}
