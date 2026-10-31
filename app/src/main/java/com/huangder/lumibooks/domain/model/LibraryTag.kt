package com.huangder.lumibooks.domain.model

import java.util.Locale

data class LibraryTag(
    val id: String,
    val name: String,
    val createdAt: Long,
    /** Retained for compatibility with older backups. New labels are flat. */
    val parentId: String? = null,
    val updatedAt: Long = createdAt,
    val groupId: String? = null
)

data class BookTagLink(
    val bookId: String,
    val tagId: String,
    val updatedAt: Long = 0L
)

object TagNameValidator {
    const val MAX_LENGTH = 20

    fun clean(rawName: String): String = rawName.trim()

    fun isValid(rawName: String): Boolean {
        val name = clean(rawName)
        return name.isNotEmpty() && name.length <= MAX_LENGTH
    }

    fun normalized(rawName: String): String = clean(rawName).lowercase(Locale.ROOT)
}
