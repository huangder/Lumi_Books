package com.huangder.lumibooks.data.repository

import com.huangder.lumibooks.data.local.dao.FolderDao
import com.huangder.lumibooks.data.local.entity.BookFolderCrossRefEntity
import com.huangder.lumibooks.data.local.entity.FolderEntity
import com.huangder.lumibooks.domain.model.BookFolderLink
import com.huangder.lumibooks.domain.model.FolderNameValidator
import com.huangder.lumibooks.domain.model.FolderMoveResult
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject
import com.huangder.lumibooks.data.local.dao.SyncStateDao
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    private val syncStateDao: SyncStateDao,
    private val syncIdentityStore: SyncIdentityStore
) : FolderRepository {
    override fun getAllFolders(): Flow<List<LibraryFolder>> =
        folderDao.getAllFolders().map { folders -> folders.map { it.toDomain() } }

    override fun getAllBookFolderLinks(): Flow<List<BookFolderLink>> =
        folderDao.getAllBookFolderLinks().map { links -> links.map { it.toDomain() } }

    override suspend fun createFolder(rawName: String, parentId: String?): LibraryFolder? {
        require(FolderNameValidator.isValid(rawName))
        return folderDao.createFolderIfAvailable(newFolder(rawName, parentId))?.toDomain()
    }

    override suspend fun getOrCreateRootFolder(rawName: String): LibraryFolder {
        require(FolderNameValidator.isValid(rawName))
        return folderDao.getOrCreateRootFolder(newFolder(rawName, null)).toDomain()
    }

    override suspend fun getOrCreateFolderPath(
        rootName: String,
        relativeDirectory: String?
    ): LibraryFolder {
        val sanitizedRoot = sanitizeFolderSegment(rootName)
            ?: error("Invalid authorized folder name")
        val path = buildList {
            add(newFolder(sanitizedRoot, null))
            relativeDirectory
                .orEmpty()
                .split('/')
                .asSequence()
                .mapNotNull(::sanitizeFolderSegment)
                .forEach { segment ->
                    add(newFolder(segment, last().id))
                }
        }
        return folderDao.getOrCreateFolderPath(path).toDomain()
    }

    override suspend fun renameFolder(folderId: String, rawName: String): Boolean {
        if (!FolderNameValidator.isValid(rawName)) return false
        val name = FolderNameValidator.clean(rawName)
        return folderDao.renameFolderIfAvailable(
            folderId = folderId,
            name = name,
            normalizedName = FolderNameValidator.normalized(name)
        )
    }

    override suspend fun updateFolderCover(folderId: String, coverPath: String?): Boolean =
        folderDao.updateFolderCover(folderId, coverPath, System.currentTimeMillis()) > 0

    override suspend fun initializeFolderPreview(
        folderId: String,
        orderedBookIds: List<String>
    ): Boolean {
        val ids = orderedBookIds
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
            .toList()
        if (ids.isEmpty()) return false
        return folderDao.initializeFolderPreviewIfUnset(
            folderId = folderId,
            previewBookIds = JSONArray(ids).toString(),
            updatedAt = System.currentTimeMillis()
        ) > 0
    }

    override suspend fun moveFolder(
        folderId: String,
        targetParentId: String?
    ): FolderMoveResult = folderDao.moveFolder(folderId, targetParentId)

    override suspend fun deleteFolderTree(folderId: String): List<String> {
        val now = System.currentTimeMillis()
        val deviceId = syncIdentityStore.deviceId()
        val folderIds = folderDao.getFolderTreeIds(folderId)
        val links = folderDao.getBookLinksInTree(folderId)
        syncStateDao.upsertTombstones(
            folderIds.map { SyncTombstoneEntity("folder", it, now, deviceId) } +
                links.map { SyncTombstoneEntity("book_folder", it.bookId, now, deviceId) }
        )
        return folderDao.deleteFolderTree(folderId)
    }

    override suspend fun moveBooks(bookIds: Set<String>, targetFolderId: String?) {
        folderDao.moveBooks(bookIds, targetFolderId)
        if (targetFolderId == null && bookIds.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val deviceId = syncIdentityStore.deviceId()
            syncStateDao.upsertTombstones(
                bookIds.map { SyncTombstoneEntity("book_folder", it, now, deviceId) }
            )
        }
    }

    private fun newFolder(rawName: String, parentId: String?): FolderEntity {
        val name = FolderNameValidator.clean(rawName)
        return FolderEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            normalizedName = FolderNameValidator.normalized(name),
            parentId = parentId,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun sanitizeFolderSegment(rawName: String): String? =
        FolderNameValidator.clean(rawName)
            .take(FolderNameValidator.MAX_LENGTH)
            .takeIf(FolderNameValidator::isValid)

    private fun FolderEntity.toDomain() = LibraryFolder(
        id = id,
        name = name,
        parentId = parentId,
        createdAt = createdAt,
        coverPath = coverPath,
        previewBookIds = previewBookIds?.let { raw ->
            runCatching {
                JSONArray(raw).let { array ->
                    buildList {
                        for (index in 0 until minOf(array.length(), 4)) {
                            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }
            }.getOrDefault(emptyList())
        },
        updatedAt = updatedAt
    )

    private fun BookFolderCrossRefEntity.toDomain() = BookFolderLink(bookId, folderId, updatedAt)
}
