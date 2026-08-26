package com.huangder.lumibooks.domain.model

import java.util.Locale

data class LibraryFolder(
    val id: String,
    val name: String,
    val parentId: String?,
    val createdAt: Long
)

data class BookFolderLink(
    val bookId: String,
    val folderId: String
)

object FolderNameValidator {
    const val MAX_LENGTH = 20

    fun clean(rawName: String): String = rawName.trim()

    fun isValid(rawName: String): Boolean {
        val name = clean(rawName)
        return name.isNotEmpty() && name.length <= MAX_LENGTH
    }

    fun normalized(rawName: String): String = clean(rawName).lowercase(Locale.ROOT)
}
