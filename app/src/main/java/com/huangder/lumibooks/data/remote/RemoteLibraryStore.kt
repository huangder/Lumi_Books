package com.huangder.lumibooks.data.remote

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remote library sources and the remote books they already imported, kept in a private JSON
 * file. It deliberately lives outside DataStore so it never joins the portable settings sync.
 */
@Singleton
class RemoteLibraryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val credentialStore: RemoteCredentialStore
) {
    data class State(
        val sources: List<RemoteSource> = emptyList(),
        /** Remote document key (see [RemoteBookFiles.remoteDocumentKey]) -> imported book id. */
        val importedBooks: Map<String, String> = emptyMap()
    )

    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()
    private var loaded = false
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun load(): State = mutex.withLock { loadLocked() }

    suspend fun saveSource(source: RemoteSource, password: String?) {
        mutex.withLock {
            val current = loadLocked()
            val sources = current.sources.filterNot { it.id == source.id } + source
            if (password != null) withContext(Dispatchers.IO) { credentialStore.save(source.id, password) }
            writeLocked(current.copy(sources = sources.sortedBy { it.createdAt }))
        }
    }

    suspend fun deleteSource(sourceId: String) {
        mutex.withLock {
            val current = loadLocked()
            withContext(Dispatchers.IO) { credentialStore.clear(sourceId) }
            writeLocked(current.copy(sources = current.sources.filterNot { it.id == sourceId }))
        }
    }

    suspend fun recordImported(remoteDocumentKey: String, bookId: String) {
        mutex.withLock {
            val current = loadLocked()
            if (current.importedBooks[remoteDocumentKey] == bookId) return@withLock
            writeLocked(current.copy(importedBooks = current.importedBooks + (remoteDocumentKey to bookId)))
        }
    }

    fun password(sourceId: String): String? = credentialStore.read(sourceId)

    private suspend fun loadLocked(): State {
        if (loaded) return _state.value
        val stored = withContext(Dispatchers.IO) {
            runCatching {
                if (!file.isFile) return@runCatching State()
                val root = JSONObject(file.readText())
                val sources = root.optJSONArray("sources") ?: JSONArray()
                val imported = root.optJSONObject("importedBooks") ?: JSONObject()
                State(
                    sources = (0 until sources.length()).mapNotNull { index ->
                        sources.optJSONObject(index)?.let(RemoteSource::fromJson)
                    },
                    importedBooks = imported.keys().asSequence().associateWith { key ->
                        imported.optString(key)
                    }.filterValues(String::isNotBlank)
                )
            }.getOrElse { error ->
                Log.w(TAG, "Unable to read remote library store", error)
                State()
            }
        }
        _state.value = stored
        loaded = true
        return stored
    }

    private suspend fun writeLocked(state: State) {
        _state.value = state
        withContext(Dispatchers.IO) {
            runCatching {
                val root = JSONObject()
                    .put("version", 1)
                    .put("sources", JSONArray().apply { state.sources.forEach { put(it.toJson()) } })
                    .put("importedBooks", JSONObject().apply {
                        state.importedBooks.forEach { (key, bookId) -> put(key, bookId) }
                    })
                val parent = file.parentFile
                parent?.mkdirs()
                val temp = File.createTempFile(FILE_NAME, ".tmp", parent)
                try {
                    temp.writeText(root.toString())
                    runCatching {
                        Files.move(
                            temp.toPath(),
                            file.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }.recoverCatching {
                        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }.getOrThrow()
                } finally {
                    if (temp.exists()) temp.delete()
                }
            }.onFailure { error -> Log.w(TAG, "Unable to persist remote library store", error) }
        }
    }

    private companion object {
        const val TAG = "RemoteLibraryStore"
        const val FILE_NAME = "remote_library.json"
    }
}
