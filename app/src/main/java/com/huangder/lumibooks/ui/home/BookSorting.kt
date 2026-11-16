package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.domain.model.Book
import java.util.Locale

enum class SortBy {
    LAST_READ, TITLE, AUTHOR, DATE_ADDED, CUSTOM
}

internal fun sortBooksForLibrary(
    books: List<Book>,
    sortBy: SortBy,
    customOrder: List<String> = emptyList()
): List<Book> =
    when (sortBy) {
        SortBy.LAST_READ -> books.sortedWith(
            compareByDescending<Book> { it.isPinned }
                .thenByDescending { it.lastReadTime }
                .thenBy { it.id }
        )
        SortBy.DATE_ADDED -> books.sortedWith(
            compareByDescending<Book> { it.isPinned }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )
        SortBy.TITLE -> books.sortedWith(
            compareByDescending<Book> { it.isPinned }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        SortBy.AUTHOR -> books.sortedWith(
            compareByDescending<Book> { it.isPinned }
                .thenBy { it.author.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        SortBy.CUSTOM -> {
            val position = HashMap<String, Int>(customOrder.size * 2)
            customOrder.forEachIndexed { index, id -> position.putIfAbsent(id, index) }
            // Books that were never placed (new imports) come first, newest first.
            books.sortedWith(
                compareByDescending<Book> { it.isPinned }
                    .thenBy { if (it.id in position) 1 else 0 }
                    .thenByDescending { if (it.id in position) 0L else it.createdAt }
                    .thenBy { position[it.id] ?: 0 }
                    .thenBy { it.id }
            )
        }
    }

/**
 * Writes the new order of a visible subset (a folder, or one pinned group) back into the
 * global custom order: the subset keeps the slots it already occupied, in its new order.
 * Ids that were not in the global order yet are placed in front, so they keep the position
 * they had on screen relative to each other.
 */
internal fun mergeCustomOrder(global: List<String>, reorderedSubset: List<String>): List<String> {
    if (reorderedSubset.isEmpty()) return global
    val subset = LinkedHashSet(reorderedSubset)
    val known = global.toHashSet()
    val missing = subset.filterNot { it in known }
    val base = missing + global.filter { it.isNotEmpty() }
    val queue = ArrayDeque(subset.toList())
    val seen = HashSet<String>()
    val result = ArrayList<String>(base.size)
    for (id in base) {
        if (!seen.add(id)) continue
        result += if (id in subset) queue.removeFirst() else id
    }
    return result
}
