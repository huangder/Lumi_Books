package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.huangder.lumibooks.data.local.entity.AnnotationTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationTagDao {
    @Query("SELECT * FROM annotation_tags ORDER BY name")
    fun observeTags(): Flow<List<AnnotationTagEntity>>

    @Query("SELECT * FROM annotation_tags")
    suspend fun getAllTags(): List<AnnotationTagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureTags(tags: List<AnnotationTagEntity>)

    @Upsert
    suspend fun upsertTags(tags: List<AnnotationTagEntity>)

    @Query("UPDATE annotation_tags SET groupId = :groupId, updatedAt = :updatedAt WHERE name IN (:names) AND isDeleted = 0")
    suspend fun moveTags(names: List<String>, groupId: String?, updatedAt: Long)

    @Query("UPDATE annotation_tags SET groupId = NULL, updatedAt = :updatedAt WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: String, updatedAt: Long)

    @Query("DELETE FROM annotation_tags")
    suspend fun clearTags()
}
