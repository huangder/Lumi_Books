package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.huangder.lumibooks.data.local.entity.TagGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagGroupDao {
    @Query("SELECT * FROM tag_groups WHERE type = :type AND isDeleted = 0 ORDER BY normalizedName, id")
    fun observeGroups(type: String): Flow<List<TagGroupEntity>>

    @Query("SELECT * FROM tag_groups")
    suspend fun getAllGroups(): List<TagGroupEntity>

    @Upsert
    suspend fun upsertGroups(groups: List<TagGroupEntity>)

    @Query("DELETE FROM tag_groups")
    suspend fun deleteAll()
}
