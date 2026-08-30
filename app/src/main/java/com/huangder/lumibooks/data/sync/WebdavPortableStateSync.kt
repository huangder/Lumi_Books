package com.huangder.lumibooks.data.sync

import android.content.Context
import com.huangder.lumibooks.data.backup.PortableAsset
import com.huangder.lumibooks.data.backup.PortableAssetSource
import com.huangder.lumibooks.data.backup.PortableBook
import com.huangder.lumibooks.data.backup.PortableSnapshot
import com.huangder.lumibooks.data.backup.PortableSnapshotBundle
import com.huangder.lumibooks.data.backup.PortableSnapshotManager
import com.huangder.lumibooks.data.backup.PortableSnapshotMerger
import com.huangder.lumibooks.domain.model.WebdavConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PortableStateSyncResult(
    val uploadedAssets: Int,
    val downloadedAssets: Int
)

@Singleton
class WebdavPortableStateSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: WebdavClient,
    private val snapshotManager: PortableSnapshotManager
) {
    suspend fun sync(config: WebdavConfig, password: String): PortableStateSyncResult {
        val serverUrl = config.serverUrl
        val root = "${config.syncPath}/state-v1"
        client.ensureDirectory(serverUrl, config.username, password, root)
        client.ensureDirectory(serverUrl, config.username, password, "$root/assets")
        val localBundle = snapshotManager.capture(includeBookFiles = false).forWebdav(config)
        var uploadedAssets = 0
        var downloadedAssets = 0

        repeat(MAX_CONFLICT_RETRIES) { attempt ->
            val remoteVersioned = downloadStateOrNull(serverUrl, root, config.username, password)
            val remote = remoteVersioned?.let {
                PortableSnapshot.fromJson(it.data.toString(Charsets.UTF_8))
            } ?: emptySnapshot(localBundle.snapshot.sourceDeviceId)
            val merged = PortableSnapshotMerger.merge(localBundle.snapshot, remote)
            val remotePayload = selectForRemote(localBundle.snapshot, remote, merged, config)
            val localPayload = selectForLocal(localBundle.snapshot, remotePayload, config)
            val workDir = File(context.cacheDir, "webdav_state_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val files = mutableMapOf<String, File>()
                val remoteAssetIds = remote.assets.mapTo(mutableSetOf()) { it.id }
                for (asset in remotePayload.assets) {
                    val localSource = localBundle.assetSources[asset.id]
                    if (localSource != null) {
                        val localFile = materialize(localSource, workDir)
                        files[asset.id] = localFile
                        if (asset.id !in remoteAssetIds) {
                            client.uploadStream(
                                url = "$serverUrl/$root/assets/${asset.fileName}",
                                contentLength = asset.sizeBytes,
                                inputStreamProvider = localSource.openStream,
                                username = config.username,
                                password = password
                            )
                            uploadedAssets++
                        }
                    } else if (assetReferenced(localPayload, asset.id)) {
                        val target = File(workDir, asset.fileName)
                        val result = client.downloadToFile(
                            "$serverUrl/$root/assets/${asset.fileName}",
                            target,
                            config.username,
                            password,
                            asset.sizeBytes
                        )
                        require(result.sha256 == asset.sha256) { "Synced asset checksum mismatch" }
                        files[asset.id] = target
                        downloadedAssets++
                    }
                }

                if (remoteVersioned == null || stateFingerprint(remotePayload) != stateFingerprint(remote)) {
                    val bytes = remotePayload.toJson().toByteArray(Charsets.UTF_8)
                    try {
                        if (remoteVersioned == null || remoteVersioned.etag != null) {
                            client.uploadConditional(
                                "$serverUrl/$root/state.json",
                                bytes,
                                config.username,
                                password,
                                remoteVersioned?.etag,
                                "application/json"
                            )
                        } else {
                            client.upload(
                                "$serverUrl/$root/state.json",
                                bytes,
                                config.username,
                                password,
                                "application/json"
                            )
                        }
                    } catch (error: WebdavException) {
                        if (error.statusCode == 412 && attempt + 1 < MAX_CONFLICT_RETRIES) return@repeat
                        if (error.statusCode in setOf(400, 405, 501)) {
                            client.upload(
                                "$serverUrl/$root/state.json",
                                bytes,
                                config.username,
                                password,
                                "application/json"
                            )
                        } else throw error
                    }
                }
                if (stateFingerprint(localPayload) != stateFingerprint(localBundle.snapshot)) {
                    snapshotManager.apply(
                        localPayload,
                        files,
                        replace = true,
                        replaceAssetDirectories = false
                    )
                }
                cleanupUnreferencedAssets(serverUrl, root, config.username, password, remotePayload)
                return PortableStateSyncResult(uploadedAssets, downloadedAssets)
            } finally {
                workDir.deleteRecursively()
            }
        }
        error("WebDAV state changed repeatedly during synchronization")
    }

    private suspend fun downloadStateOrNull(
        serverUrl: String,
        root: String,
        username: String,
        password: String
    ): WebdavVersionedData? = try {
        client.downloadVersioned("$serverUrl/$root/state.json", username, password)
    } catch (error: WebdavException) {
        if (error.statusCode == 404) null else throw error
    }

    private fun PortableSnapshotBundle.forWebdav(config: WebdavConfig): PortableSnapshotBundle {
        val allowedKinds = buildSet {
            if (config.syncProfileAndSettings) addAll(listOf("avatar", "font", "reader_background"))
            if (config.syncLibraryOrganization) addAll(listOf("book_cover", "folder_cover"))
        }
        val allowedAssets = snapshot.assets.filter { it.kind in allowedKinds }
        val allowedIds = allowedAssets.mapTo(mutableSetOf()) { it.id }
        return copy(
            snapshot = snapshot.copy(
                books = snapshot.books.map { it.copy(bodyAssetId = null) },
                assets = allowedAssets
            ),
            assetSources = assetSources.filterKeys { it in allowedIds }
        )
    }

    private fun selectForRemote(
        local: PortableSnapshot,
        remote: PortableSnapshot,
        merged: PortableSnapshot,
        config: WebdavConfig
    ): PortableSnapshot {
        val readingNamespaces = setOf("bookmark", "note")
        val libraryNamespaces = setOf("book", "folder", "tag", "book_folder", "book_tag")
        val selected = merged.copy(
            preferences = if (config.syncProfileAndSettings) merged.preferences else remote.preferences,
            books = chooseBooks(local, remote, merged, config, forLocal = false),
            folders = if (config.syncLibraryOrganization) merged.folders else remote.folders,
            bookFolderLinks = if (config.syncLibraryOrganization) merged.bookFolderLinks else remote.bookFolderLinks,
            tags = if (config.syncLibraryOrganization) merged.tags else remote.tags,
            bookTagLinks = if (config.syncLibraryOrganization) merged.bookTagLinks else remote.bookTagLinks,
            readingRecords = if (config.syncReadingData) merged.readingRecords else remote.readingRecords,
            bookmarks = if (config.syncReadingData) merged.bookmarks else remote.bookmarks,
            notes = if (config.syncReadingData) merged.notes else remote.notes,
            tombstones = merged.tombstones.filter { tombstone ->
                when (tombstone.namespace) {
                    in readingNamespaces -> config.syncReadingData
                    in libraryNamespaces -> config.syncLibraryOrganization
                    else -> true
                }
            } + remote.tombstones.filter { tombstone ->
                (tombstone.namespace in readingNamespaces && !config.syncReadingData) ||
                    (tombstone.namespace in libraryNamespaces && !config.syncLibraryOrganization)
            },
            assets = (remote.assets + local.assets).associateBy { it.id }.values.toList()
        )
        return selected.copy(assets = selected.assets.filter { assetReferenced(selected, it.id) })
    }

    private fun selectForLocal(
        local: PortableSnapshot,
        remotePayload: PortableSnapshot,
        config: WebdavConfig
    ) = remotePayload.copy(
        preferences = if (config.syncProfileAndSettings) remotePayload.preferences else local.preferences,
        books = chooseBooks(local, remotePayload, remotePayload, config, forLocal = true),
        folders = if (config.syncLibraryOrganization) remotePayload.folders else local.folders,
        bookFolderLinks = if (config.syncLibraryOrganization) remotePayload.bookFolderLinks else local.bookFolderLinks,
        tags = if (config.syncLibraryOrganization) remotePayload.tags else local.tags,
        bookTagLinks = if (config.syncLibraryOrganization) remotePayload.bookTagLinks else local.bookTagLinks,
        readingRecords = if (config.syncReadingData) remotePayload.readingRecords else local.readingRecords,
        bookmarks = if (config.syncReadingData) remotePayload.bookmarks else local.bookmarks,
        notes = if (config.syncReadingData) remotePayload.notes else local.notes,
        tombstones = (local.tombstones + remotePayload.tombstones).associateBy {
            "${it.namespace}:${it.itemId}"
        }.values.toList(),
        assets = (local.assets + remotePayload.assets).associateBy { it.id }.values.toList()
    )

    private fun chooseBooks(
        local: PortableSnapshot,
        remote: PortableSnapshot,
        merged: PortableSnapshot,
        config: WebdavConfig,
        forLocal: Boolean
    ): List<PortableBook> {
        if (config.syncLibraryOrganization && config.syncReadingData) return merged.books
        val localMap = local.books.associateBy { it.id }
        val remoteMap = remote.books.associateBy { it.id }
        val mergedMap = merged.books.associateBy { it.id }
        val fallback = if (forLocal) localMap else remoteMap
        return (fallback.keys + mergedMap.keys).mapNotNull { id ->
            val base = if (config.syncLibraryOrganization) mergedMap[id] else fallback[id]
            val progress = if (config.syncReadingData) mergedMap[id] else fallback[id]
            base?.copy(
                lastReadTime = progress?.lastReadTime ?: base.lastReadTime,
                readingProgress = progress?.readingProgress ?: base.readingProgress,
                locatorJson = progress?.locatorJson ?: base.locatorJson
            )
        }
    }

    private fun materialize(source: PortableAssetSource, directory: File): File {
        val target = File(directory, source.asset.fileName)
        source.openStream().use { input -> target.outputStream().buffered().use { input.copyTo(it) } }
        require(target.length() == source.asset.sizeBytes && target.sha256() == source.asset.sha256)
        return target
    }

    private fun assetReferenced(snapshot: PortableSnapshot, assetId: String): Boolean =
        snapshot.preferences.any { it.value.contains("asset://$assetId") } ||
            snapshot.books.any { it.coverAssetId == assetId } ||
            snapshot.folders.any { it.coverPath == "asset://$assetId" }

    private suspend fun cleanupUnreferencedAssets(
        serverUrl: String,
        root: String,
        username: String,
        password: String,
        committed: PortableSnapshot
    ) {
        val referencedNames = committed.assets.mapTo(mutableSetOf()) { it.fileName }
        runCatching {
            client.listDirectory("$serverUrl/$root/assets", username, password)
                .asSequence()
                .filterNot { it.isCollection }
                .map { it.href.substringBefore('?').trimEnd('/').substringAfterLast('/') }
                .filter { it.matches(Regex("[a-f0-9]{64}\\.[a-z0-9]{1,10}")) }
                .filterNot { it in referencedNames }
                .forEach { name ->
                    client.delete("$serverUrl/$root/assets/$name", username, password)
                }
        }
    }

    private fun emptySnapshot(deviceId: String) = PortableSnapshot(
        createdAt = 0L,
        sourceDeviceId = deviceId,
        preferences = emptyList(), books = emptyList(), folders = emptyList(),
        bookFolderLinks = emptyList(), tags = emptyList(), bookTagLinks = emptyList(),
        readingRecords = emptyList(), bookmarks = emptyList(), notes = emptyList(),
        tombstones = emptyList(), assets = emptyList()
    )

    private fun stateFingerprint(snapshot: PortableSnapshot): String {
        val canonical = snapshot.copy(
            createdAt = 0L,
            sourceDeviceId = "",
            preferences = snapshot.preferences.sortedBy { it.key },
            books = snapshot.books.sortedBy { it.id },
            folders = snapshot.folders.sortedBy { it.id },
            bookFolderLinks = snapshot.bookFolderLinks.sortedBy { it.bookId },
            tags = snapshot.tags.sortedBy { it.id },
            bookTagLinks = snapshot.bookTagLinks.sortedBy { "${it.bookId}:${it.tagId}" },
            readingRecords = snapshot.readingRecords.sortedBy { "${it.bookId}:${it.date}:${it.sourceDeviceId}" },
            bookmarks = snapshot.bookmarks.sortedBy { it.syncId },
            notes = snapshot.notes.sortedBy { it.syncId },
            tombstones = snapshot.tombstones.sortedBy { "${it.namespace}:${it.itemId}" },
            assets = snapshot.assets.sortedBy { it.id }
        ).toJson().toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(canonical)
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_CONFLICT_RETRIES = 3
    }
}

private fun File.sha256(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}
