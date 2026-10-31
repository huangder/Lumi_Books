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
import com.huangder.lumibooks.data.local.dao.SyncStateDao
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore

class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val syncStateDao: SyncStateDao,
    private val syncIdentityStore: SyncIdentityStore
) : TagRepository {
    override fun getAllTags(): Flow<List<LibraryTag>> =
        tagDao.getAllTags().map { tags -> tags.map { it.toDomain() } }

    override fun getAllBookTagLinks(): Flow<List<BookTagLink>> =
        tagDao.getAllBookTagLinks().map { links -> links.map { it.toDomain() } }

    override suspend fun createAndAssignTag(
        bookId: String,
        rawName: String,
        parentId: String?
    ): LibraryTag? {
        require(TagNameValidator.isValid(rawName))

        val name = TagNameValidator.clean(rawName)
        val normalizedName = TagNameValidator.normalized(name)
        val stored = tagDao.createAndAssignTag(
            bookId = bookId,
            tag = TagEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                normalizedName = normalizedName,
                createdAt = System.currentTimeMillis(),
                parentId = null
            )
        )
        assignTag(bookId, stored.id)
        return stored.toDomain()
    }

    override suspend fun assignTag(bookId: String, tagId: String) {
        val linkTime = tagDao.getAllBookTagLinksSnapshot().find { it.bookId == bookId && it.tagId == tagId }?.updatedAt ?: 0L
        val deletionTime = syncStateDao.getAllTombstones().find { it.namespace == "book_tag" && it.itemId == "$bookId:$tagId" }?.deletedAt ?: 0L
        tagDao.upsertBookTagLink(BookTagCrossRefEntity(bookId, tagId, maxOf(System.currentTimeMillis(), linkTime + 1, deletionTime + 1)))
    }

    override suspend fun removeTagFromBook(bookId: String, tagId: String) {
        val linkTime = tagDao.getAllBookTagLinksSnapshot().find { it.bookId == bookId && it.tagId == tagId }?.updatedAt ?: 0L
        val deletionTime = syncStateDao.getAllTombstones().find { it.namespace == "book_tag" && it.itemId == "$bookId:$tagId" }?.deletedAt ?: 0L
        syncStateDao.upsertTombstones(
            listOf(
                SyncTombstoneEntity(
                    "book_tag",
                    "$bookId:$tagId",
                    maxOf(System.currentTimeMillis(), linkTime + 1, deletionTime + 1),
                    syncIdentityStore.deviceId()
                )
            )
        )
        tagDao.deleteBookTagLink(bookId, tagId)
    }

    override suspend fun renameTag(tagId: String, rawName: String): Boolean {
        if (!TagNameValidator.isValid(rawName)) return false

        val name = TagNameValidator.clean(rawName)
        val normalizedName = TagNameValidator.normalized(name)
        val existing = tagDao.getTagByNormalizedName(normalizedName)
        if (existing != null && existing.id != tagId) return false

        val current = tagDao.getAllTagsSnapshot().find { it.id == tagId } ?: return false
        tagDao.updateTagName(tagId, name, normalizedName, maxOf(System.currentTimeMillis(), current.updatedAt + 1))
        return true
    }

    override suspend fun deleteTag(tagId: String, deleteChildren: Boolean) {
        val now = maxOf(
            System.currentTimeMillis(),
            (tagDao.getAllTagsSnapshot().maxOfOrNull { it.updatedAt } ?: 0L) + 1,
            (tagDao.getAllBookTagLinksSnapshot().maxOfOrNull { it.updatedAt } ?: 0L) + 1
        )
        val deviceId = syncIdentityStore.deviceId()
        val tags = tagDao.getTagAndChildren(tagId).let { if (deleteChildren) it else it.filter { item -> item.id == tagId } }
        val links = tagDao.getLinksForTagTree(tagId).let {
            if (deleteChildren) it else it.filter { link -> link.tagId == tagId }
        }
        syncStateDao.upsertTombstones(
            tags.map { SyncTombstoneEntity("tag", it.id, now, deviceId) } +
                links.map { SyncTombstoneEntity("book_tag", "${it.bookId}:${it.tagId}", now, deviceId) }
        )
        tagDao.deleteTagWithChildren(tagId, deleteChildren, now)
    }

    private fun TagEntity.toDomain() = LibraryTag(
        id = id,
        name = name,
        createdAt = createdAt,
        parentId = parentId,
        updatedAt = updatedAt,
        groupId = groupId
    )

    private fun BookTagCrossRefEntity.toDomain() = BookTagLink(
        bookId = bookId,
        tagId = tagId,
        updatedAt = updatedAt
    )
}
