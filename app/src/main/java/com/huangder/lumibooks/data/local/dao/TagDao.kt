package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity
import com.huangder.lumibooks.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TagDao {
    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    abstract fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE parentId IS NULL ORDER BY createdAt ASC")
    abstract fun getPrimaryTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE parentId = :parentId ORDER BY createdAt ASC")
    abstract fun getSecondaryTags(parentId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM book_tag_cross_refs")
    abstract fun getAllBookTagLinks(): Flow<List<BookTagCrossRefEntity>>

    @Query("SELECT * FROM tags WHERE normalizedName = :normalizedName LIMIT 1")
    abstract suspend fun getTagByNormalizedName(normalizedName: String): TagEntity?

    @Query("SELECT * FROM tags WHERE normalizedName = :normalizedName AND parentId = :parentId LIMIT 1")
    abstract suspend fun getTagByNormalizedNameAndParent(normalizedName: String, parentId: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBookTagLink(link: BookTagCrossRefEntity)

    @Query("DELETE FROM book_tag_cross_refs WHERE bookId = :bookId AND tagId = :tagId")
    abstract suspend fun deleteBookTagLink(bookId: String, tagId: String)

    @Query("UPDATE tags SET name = :name, normalizedName = :normalizedName WHERE id = :tagId")
    abstract suspend fun updateTagName(tagId: String, name: String, normalizedName: String)

    @Query("UPDATE tags SET parentId = :parentId WHERE id = :tagId")
    abstract suspend fun moveTag(tagId: String, parentId: String?)

    @Query("UPDATE tags SET parentId = NULL WHERE parentId = :parentId")
    abstract suspend fun upgradeSecondaryTags(parentId: String)

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
}
