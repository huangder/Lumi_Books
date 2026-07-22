package com.huangder.lumibooks.data.repository

import com.huangder.lumibooks.data.local.dao.TagDao
import com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity
import com.huangder.lumibooks.data.local.entity.TagEntity
import com.huangder.lumibooks.domain.model.BookTagLink
import com.huangder.lumibooks.domain.model.LibraryTag
import com.huangder.lumibooks.domain.model.TagNameValidator
import com.huangder.lumibooks.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao
) : TagRepository {
    override fun getAllTags(): Flow<List<LibraryTag>> =
        tagDao.getAllTags().map { tags -> tags.map { it.toDomain() } }

    override fun getAllBookTagLinks(): Flow<List<BookTagLink>> =
        tagDao.getAllBookTagLinks().map { links -> links.map { it.toDomain() } }

    override suspend fun createAndAssignTag(bookId: String, rawName: String) {
        if (!TagNameValidator.isValid(rawName)) return

        val name = TagNameValidator.clean(rawName)
        tagDao.createAndAssignTag(
            bookId = bookId,
            tag = TagEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                normalizedName = TagNameValidator.normalized(name),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun assignTag(bookId: String, tagId: String) {
        tagDao.insertBookTagLink(BookTagCrossRefEntity(bookId = bookId, tagId = tagId))
    }

    override suspend fun removeTagFromBook(bookId: String, tagId: String) {
        tagDao.deleteBookTagLink(bookId, tagId)
    }

    override suspend fun renameTag(tagId: String, rawName: String): Boolean {
        if (!TagNameValidator.isValid(rawName)) return false

        val name = TagNameValidator.clean(rawName)
        val normalizedName = TagNameValidator.normalized(name)
        val existing = tagDao.getTagByNormalizedName(normalizedName)
        if (existing != null && existing.id != tagId) return false

        tagDao.updateTagName(tagId, name, normalizedName)
        return true
    }

    override suspend fun deleteTag(tagId: String) {
        tagDao.deleteTag(tagId)
    }

    private fun TagEntity.toDomain() = LibraryTag(
        id = id,
        name = name,
        createdAt = createdAt
    )

    private fun BookTagCrossRefEntity.toDomain() = BookTagLink(
        bookId = bookId,
        tagId = tagId
    )
}
