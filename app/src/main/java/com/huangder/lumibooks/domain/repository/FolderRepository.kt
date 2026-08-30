package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.domain.model.BookFolderLink
import com.huangder.lumibooks.domain.model.FolderMoveResult
import com.huangder.lumibooks.domain.model.LibraryFolder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun getAllFolders(): Flow<List<LibraryFolder>>
    fun getAllBookFolderLinks(): Flow<List<BookFolderLink>>
    suspend fun createFolder(rawName: String, parentId: String?): LibraryFolder?
    suspend fun getOrCreateRootFolder(rawName: String): LibraryFolder
    suspend fun renameFolder(folderId: String, rawName: String): Boolean
    suspend fun updateFolderCover(folderId: String, coverPath: String?): Boolean
    suspend fun initializeFolderPreview(folderId: String, orderedBookIds: List<String>): Boolean
    suspend fun moveFolder(folderId: String, targetParentId: String?): FolderMoveResult
    suspend fun deleteFolderTree(folderId: String): List<String>
    suspend fun moveBooks(bookIds: Set<String>, targetFolderId: String?)
}
