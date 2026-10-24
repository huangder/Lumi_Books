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

    @Query("DELETE FROM annotation_tags")
    suspend fun clearTags()
}
