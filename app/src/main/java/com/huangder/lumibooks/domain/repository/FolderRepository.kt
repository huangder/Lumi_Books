package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.domain.model.BookFolderLink
import com.huangder.lumibooks.domain.model.FolderMoveResult
import com.huangder.lumibooks.domain.model.LibraryFolder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    data class StorageBinding(
        val name: String,
        val treeUri: String,
        val documentUri: String,
        val parentUri: String?
    )

    fun getAllFolders(): Flow<List<LibraryFolder>>
    fun getAllBookFolderLinks(): Flow<List<BookFolderLink>>
    suspend fun createFolder(
        rawName: String,
        parentId: String?,
        storageTreeUri: String? = null,
        storageDocumentUri: String? = null,
        storageParentUri: String? = null
    ): LibraryFolder?
    suspend fun getOrCreateRootFolder(rawName: String): LibraryFolder
    suspend fun getOrCreateFolderPath(
        rootName: String,
        relativeDirectory: String?,
        storageBindings: List<StorageBinding> = emptyList()
    ): LibraryFolder
    suspend fun bindFolder(
        folderId: String,
        storageTreeUri: String?,
        storageDocumentUri: String?,
        storageParentUri: String?
    ): Boolean
    suspend fun reconcileStorageFolder(
        folderId: String,
        name: String,
        storageTreeUri: String?,
        storageDocumentUri: String?,
        storageParentUri: String?,
        storageMissing: Boolean = false
    ): Boolean
    suspend fun markStorageMissing(folderId: String, missing: Boolean): Boolean
    suspend fun reconcileFolderParent(folderId: String, parentId: String?, storageParentUri: String?): Boolean
    suspend fun renameFolder(folderId: String, rawName: String): Boolean
    suspend fun updateFolderCover(folderId: String, coverPath: String?): Boolean
    suspend fun initializeFolderPreview(folderId: String, orderedBookIds: List<String>): Boolean
    suspend fun refreshFolderPreview(folderId: String, orderedBookIds: List<String>): Boolean
    suspend fun moveFolder(folderId: String, targetParentId: String?): FolderMoveResult
    suspend fun deleteFolderTree(folderId: String): List<String>
    suspend fun moveBooks(bookIds: Set<String>, targetFolderId: String?)
}
