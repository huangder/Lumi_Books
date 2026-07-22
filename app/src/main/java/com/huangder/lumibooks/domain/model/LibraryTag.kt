package com.huangder.lumibooks.domain.model

import java.util.Locale

data class LibraryTag(
    val id: String,
    val name: String,
    val createdAt: Long
)

data class BookTagLink(
    val bookId: String,
    val tagId: String
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
