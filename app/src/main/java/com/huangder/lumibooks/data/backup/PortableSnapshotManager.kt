package com.huangder.lumibooks.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PortableAssetSource(
    val asset: PortableAsset,
    val openStream: () -> InputStream
)

data class PortableSnapshotBundle(
    val snapshot: PortableSnapshot,
    val assetSources: Map<String, PortableAssetSource>
)

@Singleton
class PortableSnapshotManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val dataStoreManager: DataStoreManager,
    private val syncIdentityStore: SyncIdentityStore,
    private val mineruTokenStore: MineruTokenStore,
    private val externalTtsTokenStore: ExternalTtsTokenStore
) {
    suspend fun capture(includeBookFiles: Boolean = true): PortableSnapshotBundle = withContext(Dispatchers.IO) {
        val deviceId = syncIdentityStore.deviceId()
        val bookDao = database.bookDao()
        val syncDao = database.syncStateDao()
        val books = bookDao.getAllBooksSnapshot()
        val folders = syncDao.getAllFolders()
        val sources = linkedMapOf<String, PortableAssetSource>()
        val pathAssets = mutableMapOf<String, String>()

        fun addSource(kind: String, ownerId: String, displayName: String, opener: () -> InputStream): String? {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val valid = runCatching {
                opener().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        size += count
                    }
                }
            }.isSuccess
            if (!valid || size <= 0L) return null
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val extension = displayName.substringAfterLast('.', "bin")
                .lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "bin"
            val logicalId = "$sha-${stableSuffix("$kind\u0000$ownerId")}"
            val asset = PortableAsset(logicalId, kind, ownerId, "$sha.$extension", sha, size)
            sources.putIfAbsent(logicalId, PortableAssetSource(asset, opener))
            return logicalId
        }

        fun addFile(kind: String, ownerId: String, file: File): String? {
            if (!file.isFile || file.length() <= 0L) return null
            val id = addSource(kind, ownerId, file.name) { file.inputStream().buffered() }
            if (id != null) pathAssets[file.absolutePath] = id
            return id
        }

        val portableBooks = books.map { book ->
            val bodyId = if (includeBookFiles && !book.isCloudOnly && book.filePath.isNotBlank()) {
                addSource(
                    "book",
                    book.id,
                    BookFileAccess.displayName(context, book.filePath) ?: "${book.id}.${book.format.lowercase()}"
                ) { BookFileAccess.openInputStream(context, book.filePath) }
            } else null
            val coverId = book.coverPath?.let(::File)?.let { addFile("book_cover", book.id, it) }
            book.toPortable(bodyId, coverId)
        }

        val portableFolders = folders.map { folder ->
            val assetId = folder.coverPath?.let(::File)?.let { addFile("folder_cover", folder.id, it) }
            folder.copy(coverPath = assetId?.let { "asset://$it" })
        }

        val avatar = File(context.filesDir, "avatars/avatar.jpg")
        addFile("avatar", "profile", avatar)
        listOf("fonts" to "font", "reader_backgrounds" to "reader_background").forEach { (directory, kind) ->
            File(context.filesDir, directory).walkTopDown()
                .filter { it.isFile && !it.name.contains(".blurred-") }
                .forEach { file -> addFile(kind, file.relativeTo(File(context.filesDir, directory)).path, file) }
        }

        val preferences = dataStoreManager.exportPortablePreferences(deviceId).map { preference ->
            if (preference.type != PortablePreference.TYPE_STRING) return@map preference
            preference.copy(
                value = replacePathsInPortableString(
                    preference.value,
                    pathAssets.mapValues { "asset://${it.value}" }
                )
            )
        }

        PortableSnapshotBundle(
            snapshot = PortableSnapshot(
                createdAt = System.currentTimeMillis(),
                sourceDeviceId = deviceId,
                preferences = preferences,
                books = portableBooks,
                folders = portableFolders,
                bookFolderLinks = syncDao.getAllBookFolderLinks(),
                tags = syncDao.getAllTags(),
                bookTagLinks = syncDao.getAllBookTagLinks(),
                readingRecords = syncDao.getAllReadingRecords(),
                bookmarks = syncDao.getAllBookmarks(),
                notes = syncDao.getAllNotes(),
                tombstones = syncDao.getAllTombstones(),
                assets = sources.values.map { it.asset }
            ),
            assetSources = sources
        )
    }

    suspend fun apply(
        snapshot: PortableSnapshot,
        assetFiles: Map<String, File>,
        replace: Boolean,
        replaceAssetDirectories: Boolean = replace
    ) = withContext(Dispatchers.IO) {
        val deviceId = syncIdentityStore.deviceId()
        validateSnapshot(snapshot, assetFiles)
        val oldPreferences = dataStoreManager.exportPortablePreferences(deviceId)
        val preparedAssets = prepareAssets(snapshot, assetFiles, replaceAssetDirectories)
        val assetPaths = preparedAssets.paths
        val preferences = snapshot.preferences.map { preference ->
            if (preference.type != PortablePreference.TYPE_STRING) return@map preference
            preference.copy(
                value = replacePathsInPortableString(
                    preference.value,
                    assetPaths.mapKeys { "asset://${it.key}" }
                )
            )
        }
        try {
            preparedAssets.commit()
            if (replace) dataStoreManager.replacePortablePreferences(preferences, deviceId)
            else dataStoreManager.applyPortablePreferences(preferences)
            if (!mineruTokenStore.hasToken()) dataStoreManager.saveMineruMode("disabled")
            if (!externalTtsTokenStore.hasToken()) dataStoreManager.disableExternalTts()

            database.withTransaction {
                val bookDao = database.bookDao()
                val syncDao = database.syncStateDao()
                val existingBooks = bookDao.getAllBooksSnapshot().associateBy { it.id }
                val existingFolders = syncDao.getAllFolders()
                val existingRecords = syncDao.getAllReadingRecords().associateBy {
                    "${it.bookId}\u0000${it.date}\u0000${it.sourceDeviceId}"
                }
                val existingBookmarks = syncDao.getAllBookmarks().associateBy { it.syncId }
                val existingNotes = syncDao.getAllNotes().associateBy { it.syncId }
                val restoredBooks = snapshot.books.map { book ->
                    val existing = existingBooks[book.id]
                    book.toEntity(
                        filePath = book.bodyAssetId?.let(assetPaths::get)
                            ?: existing?.filePath.orEmpty().takeIf { !replaceAssetDirectories }.orEmpty(),
                        coverPath = book.coverAssetId?.let(assetPaths::get)
                            ?: existing?.coverPath?.takeIf { !replaceAssetDirectories }
                    )
                }
                if (replace) {
                    syncDao.clearBookFolderLinks()
                    syncDao.clearBookTagLinks()
                    syncDao.clearBookmarks()
                    syncDao.clearNotes()
                    syncDao.clearReadingRecords()
                    syncDao.clearFolders()
                    syncDao.clearTags()
                    bookDao.clearBooks()
                    syncDao.clearTombstones()
                }
                if (restoredBooks.isNotEmpty()) bookDao.upsertBooks(restoredBooks)
                val knownFolderIds = if (replace) emptySet() else existingFolders.mapTo(mutableSetOf()) { it.id }
                val existingFoldersById = existingFolders.associateBy { it.id }
                val restoredFolders = sortFoldersParentFirst(snapshot.folders, knownFolderIds).map { folder ->
                    val restoredCover = folder.coverPath
                        ?.takeIf { it.startsWith("asset://") }
                        ?.removePrefix("asset://")
                        ?.let(assetPaths::get)
                    folder.copy(
                        coverPath = restoredCover
                            ?: existingFoldersById[folder.id]?.coverPath?.takeIf { !replaceAssetDirectories }
                    )
                }
                if (restoredFolders.isNotEmpty()) syncDao.upsertFolders(restoredFolders)
                if (snapshot.tags.isNotEmpty()) syncDao.upsertTags(snapshot.tags)
                if (snapshot.bookFolderLinks.isNotEmpty()) syncDao.upsertBookFolderLinks(snapshot.bookFolderLinks)
                if (snapshot.bookTagLinks.isNotEmpty()) syncDao.upsertBookTagLinks(snapshot.bookTagLinks)
                if (snapshot.readingRecords.isNotEmpty()) syncDao.upsertReadingRecords(snapshot.readingRecords.map {
                    val key = "${it.bookId}\u0000${it.date}\u0000${it.sourceDeviceId}"
                    it.copy(id = if (replace) 0 else existingRecords[key]?.id ?: 0)
                })
                if (snapshot.bookmarks.isNotEmpty()) syncDao.upsertBookmarks(snapshot.bookmarks.map {
                    it.copy(id = if (replace) 0 else existingBookmarks[it.syncId]?.id ?: 0)
                })
                if (snapshot.notes.isNotEmpty()) syncDao.upsertNotes(snapshot.notes.map {
                    it.copy(id = if (replace) 0 else existingNotes[it.syncId]?.id ?: 0)
                })
                if (snapshot.tombstones.isNotEmpty()) syncDao.upsertTombstones(snapshot.tombstones)
                applyTombstones(snapshot)
            }
            preparedAssets.finish()
        } catch (error: Throwable) {
            runCatching { dataStoreManager.replacePortablePreferences(oldPreferences, deviceId) }
            preparedAssets.rollback()
            throw error
        } finally {
            preparedAssets.cleanup()
        }
    }

    private suspend fun applyTombstones(snapshot: PortableSnapshot) {
        val dao = database.syncStateDao()
        val grouped = snapshot.tombstones.groupBy { it.namespace }
        val bookmarkTimes = snapshot.bookmarks.associate { it.syncId to it.updatedAt }
        val noteTimes = snapshot.notes.associate { it.syncId to it.updatedAt }
        val folderTimes = snapshot.folders.associate { it.id to it.updatedAt }
        val tagTimes = snapshot.tags.associate { it.id to it.updatedAt }
        val folderLinkTimes = snapshot.bookFolderLinks.associate { it.bookId to it.updatedAt }
        val tagLinkTimes = snapshot.bookTagLinks.associate { "${it.bookId}:${it.tagId}" to it.updatedAt }

        grouped["bookmark"]?.effectiveDeletes(bookmarkTimes)?.takeIf { it.isNotEmpty() }
            ?.let { dao.deleteBookmarksBySyncIds(it) }
        grouped["note"]?.effectiveDeletes(noteTimes)?.takeIf { it.isNotEmpty() }
            ?.let { dao.deleteNotesBySyncIds(it) }
        grouped["book_folder"]?.effectiveDeletes(folderLinkTimes)?.takeIf { it.isNotEmpty() }
            ?.let { dao.deleteBookFolderLinksByBookIds(it) }
        grouped["book_tag"]?.effectiveDeletes(tagLinkTimes)?.takeIf { it.isNotEmpty() }
            ?.let { dao.deleteBookTagLinksByIds(it) }
        grouped["folder"]?.effectiveDeletes(folderTimes)?.takeIf { it.isNotEmpty() }
            ?.let { dao.deleteFoldersByIds(it) }
        grouped["tag"]?.effectiveDeletes(tagTimes)?.takeIf { it.isNotEmpty() }
            ?.let { dao.deleteTagsByIds(it) }
    }

    private fun List<com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity>.effectiveDeletes(
        liveUpdatedAt: Map<String, Long>
    ): List<String> = mapNotNull { tombstone ->
        tombstone.itemId.takeIf { tombstone.deletedAt >= (liveUpdatedAt[it] ?: Long.MIN_VALUE) }
    }

    private fun prepareAssets(
        snapshot: PortableSnapshot,
        files: Map<String, File>,
        replaceDirectories: Boolean
    ): PreparedAssetInstall {
        val token = UUID.randomUUID().toString()
        val roots = assetRoots()
        val stagingRoots = roots.mapValues { (_, target) -> File(target.parentFile, ".${target.name}.restore-$token") }
        val required = referencedAssetIds(snapshot)
        val paths = mutableMapOf<String, String>()
        val stagedFiles = linkedMapOf<File, File>()
        try {
            for (asset in snapshot.assets) {
                val source = files[asset.id] ?: continue
                val root = roots[asset.kind] ?: continue
                val stagingRoot = stagingRoots.getValue(asset.kind)
                val target = assetTarget(root, asset)
                val staged = assetTarget(stagingRoot, asset)
                staged.parentFile?.mkdirs()
                source.copyTo(staged, overwrite = true)
                require(staged.length() == asset.sizeBytes && staged.sha256Portable() == asset.sha256) {
                    "Backup asset is damaged: ${asset.fileName}"
                }
                paths[asset.id] = target.absolutePath
                stagedFiles[target] = staged
            }
            require(required.all { it in paths }) { "Backup is missing a referenced asset" }
        } catch (error: Throwable) {
            stagingRoots.values.distinct().forEach { it.deleteRecursively() }
            throw error
        }
        val directoryPlans = roots.entries
            .associate { (_, target) -> target.canonicalFile to File(target.parentFile, ".${target.name}.restore-$token") }
            .map { (target, staging) -> DirectoryPlan(target, staging, File(target.parentFile, ".${target.name}.previous-$token")) }
        return PreparedAssetInstall(directoryPlans, stagedFiles, paths, replaceDirectories, token)
    }

    private fun validateSnapshot(snapshot: PortableSnapshot, files: Map<String, File>) {
        require(snapshot.schemaVersion == PortableSnapshot.CURRENT_VERSION) { "Unsupported snapshot version" }
        require(snapshot.assets.map { it.id }.distinct().size == snapshot.assets.size) { "Duplicate asset IDs" }
        require(snapshot.books.map { it.id }.distinct().size == snapshot.books.size) { "Duplicate book IDs" }
        require(snapshot.folders.map { it.id }.distinct().size == snapshot.folders.size) { "Duplicate folder IDs" }
        require(snapshot.tags.map { it.id }.distinct().size == snapshot.tags.size) { "Duplicate tag IDs" }
        require(snapshot.bookmarks.all { it.syncId.isNotBlank() }) { "Bookmark without stable ID" }
        require(snapshot.bookmarks.map { it.syncId }.distinct().size == snapshot.bookmarks.size) {
            "Duplicate bookmark stable IDs"
        }
        require(snapshot.notes.all { it.syncId.isNotBlank() }) { "Note without stable ID" }
        require(snapshot.notes.map { it.syncId }.distinct().size == snapshot.notes.size) { "Duplicate note stable IDs" }
        require(snapshot.readingRecords.all { it.sourceDeviceId.isNotBlank() }) { "Reading record without device ID" }
        require(
            snapshot.readingRecords.distinctBy { "${it.bookId}\u0000${it.date}\u0000${it.sourceDeviceId}" }.size ==
                snapshot.readingRecords.size
        ) { "Duplicate reading device contributions" }

        val assets = snapshot.assets.associateBy { it.id }
        for (asset in snapshot.assets) {
            require(asset.id.matches(Regex("[a-f0-9]{64}(?:-[a-f0-9]{16})?"))) { "Invalid asset ID" }
            require(asset.sha256.matches(Regex("[a-f0-9]{64}")) && asset.sizeBytes > 0L) { "Invalid asset metadata" }
            require(asset.fileName == File(asset.fileName).name && '/' !in asset.fileName && '\\' !in asset.fileName) {
                "Invalid asset filename"
            }
        }
        for (assetId in referencedAssetIds(snapshot)) {
            require(assets.containsKey(assetId)) { "Referenced asset is not declared: $assetId" }
            val source = files[assetId]
            require(source?.isFile == true) { "Referenced asset is missing: $assetId" }
        }
    }

    private fun referencedAssetIds(snapshot: PortableSnapshot): Set<String> = buildSet {
        snapshot.books.forEach { book ->
            book.bodyAssetId?.let(::add)
            book.coverAssetId?.let(::add)
        }
        snapshot.folders.forEach { folder ->
            folder.coverPath?.takeIf { it.startsWith("asset://") }?.removePrefix("asset://")?.let(::add)
        }
        val pattern = Regex("asset://([a-f0-9]{64}(?:-[a-f0-9]{16})?)")
        snapshot.preferences.forEach { preference ->
            if (preference.type == PortablePreference.TYPE_STRING) {
                pattern.findAll(preference.value).forEach { add(it.groupValues[1]) }
            }
        }
    }

    private fun assetRoots(): Map<String, File> {
        val books = FileUtils.getBooksDirectory(context)
        val covers = FileUtils.getCoversDirectory(context)
        return mapOf(
            "book" to books,
            "book_cover" to covers,
            "folder_cover" to covers,
            "avatar" to File(context.filesDir, "avatars"),
            "font" to File(context.filesDir, "fonts"),
            "reader_background" to File(context.filesDir, "reader_backgrounds")
        )
    }

    private fun assetTarget(root: File, asset: PortableAsset): File {
        val owner = asset.ownerId.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,100}")) }
            ?: stableSuffix(asset.ownerId)
        val extension = asset.fileName.substringAfterLast('.')
        return when (asset.kind) {
            "avatar" -> File(root, "avatar.jpg")
            "folder_cover" -> File(root, "folder_custom_${owner}_${asset.sha256}.$extension")
            "book_cover" -> File(root, "custom_${owner}_${asset.sha256}.$extension")
            else -> File(root, asset.fileName)
        }
    }

    private fun replacePathsInPortableString(value: String, replacements: Map<String, String>): String {
        fun replaceText(text: String): String = replacements.entries.fold(text) { current, (old, new) ->
            current.replace(old, new).replace(old.replace('\\', '/'), new)
        }

        fun transform(value: Any?): Any? = when (value) {
            is JSONObject -> value.apply {
                keys().asSequence().toList().forEach { key -> put(key, transform(opt(key))) }
            }
            is JSONArray -> value.apply {
                for (index in 0 until length()) put(index, transform(opt(index)))
            }
            is String -> replaceText(value)
            else -> value
        }

        val trimmed = value.trimStart()
        return when {
            trimmed.startsWith('{') -> runCatching { transform(JSONObject(value)).toString() }.getOrElse {
                replaceText(value)
            }
            trimmed.startsWith('[') -> runCatching { transform(JSONArray(value)).toString() }.getOrElse {
                replaceText(value)
            }
            else -> replaceText(value)
        }
    }

    private fun sortFoldersParentFirst(
        folders: List<com.huangder.lumibooks.data.local.entity.FolderEntity>,
        existingIds: Set<String>
    ): List<com.huangder.lumibooks.data.local.entity.FolderEntity> {
        val remaining = folders.associateByTo(linkedMapOf()) { it.id }
        val known = existingIds.toMutableSet()
        val result = mutableListOf<com.huangder.lumibooks.data.local.entity.FolderEntity>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.values.filter { it.parentId == null || it.parentId in known }
            require(ready.isNotEmpty()) { "Folder hierarchy contains a cycle or missing parent" }
            ready.sortedWith(compareBy({ it.createdAt }, { it.id })).forEach {
                result += it
                known += it.id
                remaining.remove(it.id)
            }
        }
        return result
    }

    private data class DirectoryPlan(val target: File, val staging: File, val backup: File)

    private class PreparedAssetInstall(
        private val directories: List<DirectoryPlan>,
        private val stagedFiles: Map<File, File>,
        val paths: Map<String, String>,
        private val replaceDirectories: Boolean,
        private val token: String
    ) {
        private val committedDirectories = mutableListOf<DirectoryPlan>()
        private val committedFiles = mutableListOf<Pair<File, File?>>()

        fun commit() {
            if (replaceDirectories) {
                directories.forEach { plan ->
                    plan.staging.mkdirs()
                    plan.backup.deleteRecursively()
                    val hadTarget = plan.target.exists()
                    if (hadTarget) require(plan.target.renameTo(plan.backup)) { "Unable to stage existing resources" }
                    if (!plan.staging.renameTo(plan.target)) {
                        if (hadTarget) plan.backup.renameTo(plan.target)
                        error("Unable to install restored resources")
                    }
                    committedDirectories += plan
                }
            } else {
                stagedFiles.forEach { (target, staged) ->
                    target.parentFile?.mkdirs()
                    val backup = target.takeIf { it.exists() }?.let {
                        File(target.parentFile, ".${target.name}.previous-$token").also { backup ->
                            backup.delete()
                            require(target.renameTo(backup)) { "Unable to stage existing resource" }
                        }
                    }
                    if (!staged.renameTo(target)) {
                        backup?.renameTo(target)
                        error("Unable to install restored resource")
                    }
                    committedFiles += target to backup
                }
            }
        }

        fun rollback() {
            committedFiles.asReversed().forEach { (target, backup) ->
                target.delete()
                backup?.renameTo(target)
            }
            committedDirectories.asReversed().forEach { plan ->
                plan.target.deleteRecursively()
                if (plan.backup.exists()) plan.backup.renameTo(plan.target)
            }
        }

        fun finish() {
            committedFiles.forEach { (_, backup) -> backup?.delete() }
            committedDirectories.forEach { it.backup.deleteRecursively() }
            committedFiles.clear()
            committedDirectories.clear()
        }

        fun cleanup() {
            directories.forEach {
                it.staging.deleteRecursively()
                if (it.backup.exists() && !it.target.exists()) it.backup.renameTo(it.target)
            }
        }
    }

    private fun BookEntity.toPortable(bodyAssetId: String?, coverAssetId: String?) = PortableBook(
        id, title, author, format, lastReadTime, readingProgress, locatorJson, createdAt, isFavorite,
        isCloudOnly, metadataUpdatedAt, bodyAssetId, coverAssetId, remoteLibraryKey, remoteFileName,
        remoteFileSize, remoteFileSha256
    )

    private fun PortableBook.toEntity(filePath: String, coverPath: String?) = BookEntity(
        id = id, title = title, author = author, filePath = filePath, coverPath = coverPath,
        format = format, lastReadTime = lastReadTime, readingProgress = readingProgress,
        locatorJson = locatorJson, createdAt = createdAt, isFavorite = isFavorite,
        isCloudOnly = bodyAssetId == null && (isCloudOnly || filePath.isBlank()),
        remoteLibraryKey = remoteLibraryKey, remoteFileName = remoteFileName,
        remoteFileSize = remoteFileSize, remoteFileSha256 = remoteFileSha256,
        metadataUpdatedAt = metadataUpdatedAt
    )
}

private fun stableSuffix(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .take(8)
    .joinToString("") { "%02x".format(it) }

private fun File.sha256Portable(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}
