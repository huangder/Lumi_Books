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
    suspend fun sync(
        config: WebdavConfig,
        password: String,
        wireBookIds: Map<String, String> = emptyMap(),
        deletedBookIds: Set<String> = emptySet()
    ): PortableStateSyncResult {
        val serverUrl = config.serverUrl
        val root = "${config.syncPath}/state-v1"
        client.ensureDirectory(serverUrl, config.username, password, root)
        client.ensureDirectory(serverUrl, config.username, password, "$root/assets")
        val captured = snapshotManager.capture(includeBookFiles = false).forWebdav(config)
        // A legacy placeholder and a newly imported body may represent the same verified wire ID.
        // Keep every reference for merging, but use the physical book's descriptor only once.
        val placeholderIds = captured.snapshot.books.filter { book -> book.isCloudOnly &&
            wireBookIds.any { (local, wire) -> local != book.id && wire == book.id }
        }.mapTo(mutableSetOf()) { it.id }
        val localBundle = captured.copy(snapshot = captured.snapshot.copy(
            books = captured.snapshot.books.filter { it.id !in placeholderIds }
        ).remapWebdavBookIds(wireBookIds))
        val currentIds = captured.snapshot.books.mapTo(mutableSetOf()) { it.id }
        val localBookIds = wireBookIds.filterKeys { it in currentIds }
            .entries.associate { (local, wire) -> wire to local }
        var uploadedAssets = 0
        var downloadedAssets = 0

        repeat(MAX_CONFLICT_RETRIES) { attempt ->
            val remoteVersioned = downloadStateOrNull(serverUrl, root, config.username, password)
            val remote = remoteVersioned?.let {
                PortableSnapshot.fromJson(it.data.toString(Charsets.UTF_8))
            } ?: emptySnapshot(localBundle.snapshot.sourceDeviceId)
            val merged = PortableSnapshotMerger.merge(localBundle.snapshot, remote)
            val remotePayload = selectForRemote(localBundle.snapshot, remote, merged, config)
            val shadowedIds = currentIds.filterTo(mutableSetOf()) { wireBookIds[it]?.let { wire -> wire != it } == true }
            // A progress record is not a downloadable book. Wait for the user's local import.
            if (config.syncReadingProgressOnly && !config.syncBookFiles) {
                shadowedIds += remotePayload.books.map { it.id }.toSet() -
                    localBundle.snapshot.books.map { it.id }.toSet()
            }
            val localPayload = selectForLocal(localBundle.snapshot, remotePayload, config)
                .withoutWebdavBookIds(shadowedIds + deletedBookIds)
            val workDir = File(context.cacheDir, "webdav_state_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val files = mutableMapOf<String, File>()
                // Logical asset IDs include the owner. Several owners can reference one file.
                val remoteAssetFiles = remote.assets.mapTo(mutableSetOf()) { it.webdavFileKey() }
                for (asset in remotePayload.assets) {
                    val localSource = localBundle.assetSources[asset.id]
                    if (localSource != null) {
                        val localFile = materialize(localSource, workDir)
                        files[asset.id] = localFile
                        val fileKey = asset.webdavFileKey()
                        if (fileKey !in remoteAssetFiles) {
                            client.uploadStream(
                                url = WebdavUrl.append(serverUrl, root, "assets", asset.fileName),
                                contentLength = asset.sizeBytes,
                                inputStreamProvider = { localFile.inputStream().buffered() },
                                username = config.username,
                                password = password
                            )
                            remoteAssetFiles += fileKey
                            uploadedAssets++
                        }
                    } else if (assetReferenced(localPayload, asset.id)) {
                        val target = File(workDir, asset.fileName)
                        val result = client.downloadToFile(
                            WebdavUrl.append(serverUrl, root, "assets", asset.fileName),
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
                                WebdavUrl.append(serverUrl, root, "state.json"),
                                bytes,
                                config.username,
                                password,
                                remoteVersioned?.etag,
                                "application/json"
                            )
                        } else {
                            client.upload(
                                WebdavUrl.append(serverUrl, root, "state.json"),
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
                                WebdavUrl.append(serverUrl, root, "state.json"),
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
                        localPayload.remapWebdavBookIds(localBookIds),
                        files,
                        // State snapshots intentionally omit book bodies. Apply them as a
                        // merge so a cloud book discovered from manifest.json cannot disappear
                        // just because it was not present in the state snapshot being applied.
                        replace = false,
                        replaceAssetDirectories = false
                    )
                }
                cleanupUnreferencedAssets(serverUrl, root, config.username, password, remotePayload)
                if (config.syncReadingDetails) {
                    com.huangder.lumibooks.reading.journal.ReadingJournalSettings.recordBackup(context)
                }
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
        client.downloadVersioned(WebdavUrl.append(serverUrl, root, "state.json"), username, password)
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
            snapshot = stripHighlightRuleLibraryForWebdav(snapshot.copy(
                books = snapshot.books.map { it.copy(bodyAssetId = null) },
                assets = allowedAssets
            )),
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
            annotationTags = if (config.syncReadingDetails) merged.annotationTags else remote.annotationTags,
            tagGroups = chooseWebdavTagGroups(merged, remote, config.syncLibraryOrganization, config.syncReadingDetails),
            bookTagsWithoutGroupId = if (config.syncLibraryOrganization) merged.bookTagsWithoutGroupId else remote.unknownBookGroupIds(),
            annotationTagsWithoutGroupId = if (config.syncReadingDetails) merged.annotationTagsWithoutGroupId else remote.unknownAnnotationGroupNames(),
            bookTagLinks = if (config.syncLibraryOrganization) merged.bookTagLinks else remote.bookTagLinks,
            readingRecords = if (config.syncReadingDetails) merged.readingRecords else remote.readingRecords,
            bookmarks = if (config.syncReadingDetails) merged.bookmarks else remote.bookmarks,
            notes = if (config.syncReadingDetails) merged.notes else remote.notes,
            journalEvents = if (config.syncReadingDetails) merged.journalEvents else remote.journalEvents,
            highlightRuleExclusions = if (config.syncReadingDetails) merged.highlightRuleExclusions else remote.highlightRuleExclusions,
            tombstones = merged.tombstones.filter { tombstone ->
                when (tombstone.namespace) {
                    in readingNamespaces -> config.syncReadingDetails
                    in libraryNamespaces -> config.syncLibraryOrganization
                    else -> true
                }
            } + remote.tombstones.filter { tombstone ->
                (tombstone.namespace in readingNamespaces && !config.syncReadingDetails) ||
                    (tombstone.namespace in libraryNamespaces && !config.syncLibraryOrganization)
            },
            assets = (remote.assets + local.assets).associateBy { it.id }.values.toList()
        )
        return stripHighlightRuleLibraryForWebdav(
            selected.copy(assets = selected.assets.filter { assetReferenced(selected, it.id) })
        )
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
        annotationTags = if (config.syncReadingDetails) remotePayload.annotationTags else local.annotationTags,
        tagGroups = chooseWebdavTagGroups(remotePayload, local, config.syncLibraryOrganization, config.syncReadingDetails),
        bookTagsWithoutGroupId = if (config.syncLibraryOrganization) remotePayload.unknownBookGroupIds() else local.unknownBookGroupIds(),
        annotationTagsWithoutGroupId = if (config.syncReadingDetails) remotePayload.unknownAnnotationGroupNames() else local.unknownAnnotationGroupNames(),
        bookTagLinks = if (config.syncLibraryOrganization) remotePayload.bookTagLinks else local.bookTagLinks,
        readingRecords = if (config.syncReadingDetails) remotePayload.readingRecords else local.readingRecords,
        bookmarks = if (config.syncReadingDetails) remotePayload.bookmarks else local.bookmarks,
        notes = if (config.syncReadingDetails) remotePayload.notes else local.notes,
        journalEvents = if (config.syncReadingDetails) remotePayload.journalEvents else local.journalEvents,
        highlightRuleExclusions = if (config.syncReadingDetails) remotePayload.highlightRuleExclusions else local.highlightRuleExclusions,
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
    ): List<PortableBook> = chooseWebdavBooksForDevice(
        localBooks = local.books,
        remoteBooks = remote.books,
        mergedBooks = merged.books,
        syncLibraryOrganization = config.syncLibraryOrganization,
        syncReadingData = config.syncReadingData,
        forLocal = forLocal
    )

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
            client.listDirectory(WebdavUrl.append(serverUrl, root, "assets"), username, password)
                .asSequence()
                .filterNot { it.isCollection }
                .map { it.href.substringBefore('?').trimEnd('/').substringAfterLast('/') }
                .filter { it.matches(Regex("[a-f0-9]{64}\\.[a-z0-9]{1,10}")) }
                .filterNot { it in referencedNames }
                .forEach { name ->
                    client.delete(
                        WebdavUrl.appendEncoded(WebdavUrl.append(serverUrl, root, "assets"), name),
                        username,
                        password
                    )
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
            annotationTags = snapshot.annotationTags.sortedBy { it.name },
            tagGroups = snapshot.tagGroups.sortedBy { it.id },
            bookTagLinks = snapshot.bookTagLinks.sortedBy { "${it.bookId}:${it.tagId}" },
            readingRecords = snapshot.readingRecords.sortedBy { "${it.bookId}:${it.date}:${it.sourceDeviceId}" },
            bookmarks = snapshot.bookmarks.sortedBy { it.syncId },
            notes = snapshot.notes.sortedBy { it.syncId },
            journalEvents = snapshot.journalEvents.sortedBy { it.id },
            highlightRules = snapshot.highlightRules.sortedBy { it.id },
            bookHighlightRuleStates = snapshot.bookHighlightRuleStates.sortedBy { "${it.bookId}:${it.ruleId}" },
            bookHighlightSettings = snapshot.bookHighlightSettings.sortedBy { it.bookId },
            highlightRuleExclusions = snapshot.highlightRuleExclusions.sortedBy { "${it.bookId}:${it.ruleId}:${it.matchKey}" },
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

/** Rule libraries belong to explicit LUMI backups in v1, not automatic WebDAV state sync. */
internal fun stripHighlightRuleLibraryForWebdav(snapshot: PortableSnapshot): PortableSnapshot = snapshot.copy(
    highlightRules = emptyList(),
    bookHighlightRuleStates = emptyList(),
    bookHighlightSettings = emptyList()
)

internal fun chooseWebdavTagGroups(
    merged: PortableSnapshot,
    fallback: PortableSnapshot,
    syncLibraryOrganization: Boolean,
    syncReadingData: Boolean
) = (if (syncLibraryOrganization) merged.tagGroups else fallback.tagGroups).filter { it.type == "book" } +
    (if (syncReadingData) merged.tagGroups else fallback.tagGroups).filter { it.type == "annotation" }

private fun PortableSnapshot.unknownBookGroupIds() = if (hasTagGroups) bookTagsWithoutGroupId else tags.mapTo(mutableSetOf()) { it.id }
private fun PortableSnapshot.unknownAnnotationGroupNames() = if (hasTagGroups) annotationTagsWithoutGroupId else annotationTags.mapTo(mutableSetOf()) { it.name }

/**
 * 选取应用到本机（[forLocal]=true）或写回服务器 state.json（[forLocal]=false）的书籍列表。
 *
 * `isCloudOnly`/`filePath` 表示“书本体是否已在本机”，是设备本地状态。WebDAV 状态同步
 * 刻意不携带书本体（`bodyAssetId` 恒为空），合并结果无法可靠推导该字段；因此本机应用时
 * 一律保留 local 侧的值，写回服务器时保留 remote 侧的值，绝不用合并结果覆盖。
 */
internal fun chooseWebdavBooksForDevice(
    localBooks: List<PortableBook>,
    remoteBooks: List<PortableBook>,
    mergedBooks: List<PortableBook>,
    syncLibraryOrganization: Boolean,
    syncReadingData: Boolean,
    forLocal: Boolean
): List<PortableBook> {
    val localMap = localBooks.associateBy { it.id }
    val remoteMap = remoteBooks.associateBy { it.id }
    val mergedMap = mergedBooks.associateBy { it.id }
    val fallback = if (forLocal) localMap else remoteMap
    return (fallback.keys + mergedMap.keys).mapNotNull { id ->
        val base = if (syncLibraryOrganization) mergedMap[id] else
            fallback[id] ?: localMap[id].takeIf { !forLocal && syncReadingData }
        val progress = if (syncReadingData) mergedMap[id] else fallback[id]
        base?.copy(
            lastReadTime = progress?.lastReadTime ?: base.lastReadTime,
            readingProgress = progress?.readingProgress ?: base.readingProgress,
            locatorJson = if (progress != null) progress.locatorJson else base.locatorJson,
            isManuallyMarkedRead = progress?.isManuallyMarkedRead ?: base.isManuallyMarkedRead,
            isCloudOnly = fallback[id]?.isCloudOnly ?: base.isCloudOnly
        )
    }
}

private fun PortableAsset.webdavFileKey() = Triple(fileName, sha256, sizeBytes)

internal fun File.sha256(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}
