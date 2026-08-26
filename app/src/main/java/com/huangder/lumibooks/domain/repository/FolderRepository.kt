package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.domain.model.BookFolderLink
import com.huangder.lumibooks.domain.model.LibraryFolder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun getAllFolders(): Flow<List<LibraryFolder>>
    fun getAllBookFolderLinks(): Flow<List<BookFolderLink>>
    suspend fun createFolder(rawName: String, parentId: String?): LibraryFolder?
    suspend fun getOrCreateRootFolder(rawName: String): LibraryFolder
    suspend fun renameFolder(folderId: String, rawName: String): Boolean
    suspend fun deleteFolderTree(folderId: String)
    suspend fun moveBooks(bookIds: Set<String>, targetFolderId: String?)
}
