package com.huangder.lumibooks.util

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers what each authorized folder contained at its last scan so the folder-books page can
 * open without touching SAF. The payload lives in the app's private files directory as a
 * rebuildable cache: it is never synced or backed up, and a damaged file just means "scan again".
 */
@Singleton
class AuthorizedFolderSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val snapshotFile = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(AuthorizedFolderSnapshot())
    private var loaded = false

    val snapshot: StateFlow<AuthorizedFolderSnapshot> = _snapshot.asStateFlow()

    /** Loads the payload once; later calls just return the in-memory value. */
    suspend fun load(): AuthorizedFolderSnapshot = mutex.withLock { loadLocked() }

    /**
     * Replaces the remembered content of [treeUri] with [scan] and returns how many book files
     * appeared compared with the previous snapshot.
     */
    suspend fun replaceTree(
        treeUri: Uri,
        scan: AuthorizedStorageManager.ScanResult,
        rootName: String
    ): Int = mutex.withLock {
        val current = loadLocked()
        val key = treeUri.toString()
        val previous = current.trees[key]?.documents.orEmpty()
        val documents = scan.documents.map { it.toSnapshot() }
        val tree = AuthorizedFolderTreeSnapshot(
            treeUri = key,
            rootName = rootName,
            scannedAt = System.currentTimeMillis(),
            directories = scan.directories.map { it.toSnapshot() },
            documents = documents
        )
        val updated = AuthorizedFolderSnapshot(current.trees + (key to tree))
        writeLocked(updated)
        _snapshot.value = updated
        authorizedFolderNewDocumentCount(previous, documents)
    }

    /**
     * Merges a single known document into an already remembered tree, so a book moved into an
     * authorized folder shows up on the folder-books page without waiting for the next scan.
     * Trees that were never scanned are intentionally left untouched: the page rescans them.
     */
    suspend fun addDocument(
        treeUri: Uri,
        document: AuthorizedFolderDocumentSnapshot
    ) = mutex.withLock {
        val current = loadLocked()
        val key = treeUri.toString()
        val tree = current.trees[key] ?: return@withLock
        val identity = document.identity
        val documents = tree.documents.filterNot { it.identity == identity } + document
        val updated = AuthorizedFolderSnapshot(
            current.trees + (key to tree.copy(documents = documents))
        )
        writeLocked(updated)
        _snapshot.value = updated
    }

    private suspend fun loadLocked(): AuthorizedFolderSnapshot {
        if (loaded) return _snapshot.value
        val stored = withContext(Dispatchers.IO) {
            runCatching { snapshotFile.takeIf(File::isFile)?.readText() }
                .getOrNull()
                ?.let(AuthorizedFolderSnapshot::fromJson)
                ?: AuthorizedFolderSnapshot()
        }
        _snapshot.value = stored
        loaded = true
        return stored
    }

    private suspend fun writeLocked(snapshot: AuthorizedFolderSnapshot) {
        withContext(Dispatchers.IO) {
            runCatching {
                val parent = snapshotFile.parentFile
                parent?.mkdirs()
                val temp = File.createTempFile(FILE_NAME, "$TEMP_SUFFIX", parent)
                try {
                    temp.writeText(snapshot.toJson())
                    moveAtomically(temp, snapshotFile)
                } finally {
                    if (temp.exists()) temp.delete()
                }
            }.onFailure { error ->
                Log.w(TAG, "Unable to persist authorized folder snapshot", error)
            }
        }
    }

    private fun moveAtomically(source: File, target: File) {
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }.getOrElse {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val FILE_NAME = "authorized_folder_snapshot.json"
        private const val TEMP_SUFFIX = ".tmp"
        private const val TAG = "AuthorizedSnapshot"
    }
}
