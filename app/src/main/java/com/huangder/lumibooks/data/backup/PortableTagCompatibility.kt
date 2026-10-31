package com.huangder.lumibooks.data.backup

import com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity

/** Retain the books visible through an old parent tag when importing its flat successor. */
internal fun PortableSnapshot.flattenLegacyTagHierarchy(): PortableSnapshot {
    if (tags.none { it.parentId != null }) return this
    val parents = tags.associate { it.id to it.parentId }
    val links = linkedMapOf<String, BookTagCrossRefEntity>()
    for (link in bookTagLinks) {
        var tagId: String? = link.tagId
        val visited = mutableSetOf<String>()
        while (tagId != null && tagId in parents && visited.add(tagId)) {
            val expanded = link.copy(tagId = tagId)
            val key = "${link.bookId}:$tagId"
            if (expanded.updatedAt >= (links[key]?.updatedAt ?: Long.MIN_VALUE)) links[key] = expanded
            tagId = parents[tagId]
        }
    }
    return copy(tags = tags.map { it.copy(parentId = null) }, bookTagLinks = links.values.toList())
}
