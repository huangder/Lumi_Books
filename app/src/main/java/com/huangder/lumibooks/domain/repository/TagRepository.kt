package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.domain.model.BookTagLink
import com.huangder.lumibooks.domain.model.LibraryTag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun getAllTags(): Flow<List<LibraryTag>>
    fun getPrimaryTags(): Flow<List<LibraryTag>>
    fun getSecondaryTags(parentId: String): Flow<List<LibraryTag>>
    fun getAllBookTagLinks(): Flow<List<BookTagLink>>
    suspend fun createAndAssignTag(bookId: String, rawName: String, parentId: String? = null): LibraryTag
    suspend fun assignTag(bookId: String, tagId: String)
    suspend fun removeTagFromBook(bookId: String, tagId: String)
    suspend fun renameTag(tagId: String, rawName: String): Boolean
    suspend fun moveTag(tagId: String, parentId: String?)
    suspend fun deleteTag(tagId: String, deleteChildren: Boolean = false)
}
