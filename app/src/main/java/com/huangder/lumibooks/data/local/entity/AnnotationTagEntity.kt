package com.huangder.lumibooks.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Annotation labels outlive their last note/bookmark; deletion must be explicit. */
@Entity(tableName = "annotation_tags")
data class AnnotationTagEntity(
    @PrimaryKey val name: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false
)
