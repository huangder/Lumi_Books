package com.huangder.lumibooks.domain.model

object TagTypes {
    const val BOOK = "book"
    const val ANNOTATION = "annotation"
    fun isValid(type: String) = type == BOOK || type == ANNOTATION
}

data class ManagedTag(
    val key: String,
    val name: String,
    val groupId: String?,
    val usageCount: Int
)

data class TagOperationResult(val success: Boolean, val message: String? = null)

data class TagCatalogMutation(val type: String, val oldKey: String, val newKey: String?)
