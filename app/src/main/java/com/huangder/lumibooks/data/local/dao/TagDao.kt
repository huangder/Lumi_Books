package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity
import com.huangder.lumibooks.data.local.entity.TagEntity
import com.huangder.lumibooks.domain.model.ManagedTag
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TagDao {
    @Query("SELECT tags.id AS `key`, tags.name, tags.groupId, COUNT(books.id) AS usageCount FROM tags LEFT JOIN book_tag_cross_refs AS links ON links.tagId = tags.id LEFT JOIN books ON books.id = links.bookId AND books.isHidden = 0 GROUP BY tags.id ORDER BY tags.normalizedName, tags.id")
    abstract fun observeCatalog(): Flow<List<ManagedTag>>

    @Query("SELECT * FROM tags")
    abstract suspend fun getAllTagsSnapshot(): List<TagEntity>

    @Query("SELECT * FROM book_tag_cross_refs")
    abstract suspend fun getAllBookTagLinksSnapshot(): List<BookTagCrossRefEntity>

    @Query("UPDATE tags SET groupId = :groupId, updatedAt = :updatedAt WHERE id IN (:ids)")
    abstract suspend fun moveTags(ids: List<String>, groupId: String?, updatedAt: Long)

    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    abstract fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM book_tag_cross_refs")
    abstract fun getAllBookTagLinks(): Flow<List<BookTagCrossRefEntity>>

    @Query("SELECT * FROM tags WHERE normalizedName = :normalizedName LIMIT 1")
    abstract suspend fun getTagByNormalizedName(normalizedName: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBookTagLink(link: BookTagCrossRefEntity)

    @Upsert
    abstract suspend fun upsertBookTagLink(link: BookTagCrossRefEntity)

    @Query("DELETE FROM book_tag_cross_refs WHERE bookId = :bookId AND tagId = :tagId")
    abstract suspend fun deleteBookTagLink(bookId: String, tagId: String)

    @Query(
        "UPDATE tags SET name = :name, normalizedName = :normalizedName, updatedAt = :updatedAt " +
            "WHERE id = :tagId"
    )
    abstract suspend fun updateTagName(
        tagId: String,
        name: String,
        normalizedName: String,
        updatedAt: Long
    )

    open suspend fun updateTagName(tagId: String, name: String, normalizedName: String) =
        updateTagName(tagId, name, normalizedName, System.currentTimeMillis())

    @Query("SELECT * FROM tags WHERE id = :tagId OR parentId = :tagId")
    abstract suspend fun getTagAndChildren(tagId: String): List<TagEntity>

    @Query("SELECT * FROM book_tag_cross_refs WHERE tagId = :tagId OR tagId IN (SELECT id FROM tags WHERE parentId = :tagId)")
    abstract suspend fun getLinksForTagTree(tagId: String): List<BookTagCrossRefEntity>

    @Query("UPDATE tags SET parentId = NULL, updatedAt = :updatedAt WHERE parentId = :parentId")
    abstract suspend fun upgradeSecondaryTags(parentId: String, updatedAt: Long)

    @Query("DELETE FROM tags WHERE id = :tagId")
    abstract suspend fun deleteTag(tagId: String)

    @Query("DELETE FROM tags WHERE parentId = :parentId")
    abstract suspend fun deleteSecondaryTags(parentId: String)

    @Transaction
    open suspend fun createAndAssignTag(bookId: String, tag: TagEntity): TagEntity {
        val storedTag = getTagByNormalizedName(tag.normalizedName) ?: run {
            insertTag(tag)
            getTagByNormalizedName(tag.normalizedName) ?: tag
        }
        insertBookTagLink(BookTagCrossRefEntity(bookId = bookId, tagId = storedTag.id))
        return storedTag
    }

    /** 删除一级标签：deleteChildren=false 时子标签升级为一级，true 时级联删除 */
    @Transaction
    open suspend fun deleteTagWithChildren(
        tagId: String,
        deleteChildren: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        if (deleteChildren) {
            deleteSecondaryTags(tagId)
        } else {
            upgradeSecondaryTags(tagId, updatedAt)
        }
        deleteTag(tagId)
    }
}
