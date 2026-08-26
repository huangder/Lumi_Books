package com.huangder.lumibooks.data.repository

import com.huangder.lumibooks.data.local.dao.FolderDao
import com.huangder.lumibooks.data.local.entity.BookFolderCrossRefEntity
import com.huangder.lumibooks.data.local.entity.FolderEntity
import com.huangder.lumibooks.domain.model.BookFolderLink
import com.huangder.lumibooks.domain.model.FolderNameValidator
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
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

    override suspend fun renameFolder(folderId: String, rawName: String): Boolean {
        if (!FolderNameValidator.isValid(rawName)) return false
        val name = FolderNameValidator.clean(rawName)
        return folderDao.renameFolderIfAvailable(
            folderId = folderId,
            name = name,
            normalizedName = FolderNameValidator.normalized(name)
        )
    }

    override suspend fun deleteFolderTree(folderId: String) {
        folderDao.deleteFolder(folderId)
    }

    override suspend fun moveBooks(bookIds: Set<String>, targetFolderId: String?) {
        folderDao.moveBooks(bookIds, targetFolderId)
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

    private fun FolderEntity.toDomain() = LibraryFolder(id, name, parentId, createdAt)

    private fun BookFolderCrossRefEntity.toDomain() = BookFolderLink(bookId, folderId)
}
