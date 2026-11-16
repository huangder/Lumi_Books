package com.huangder.lumibooks.ui.bookshelf.spine

/** A shelf row as laid out: the ids on it, in order, and whether a row break started it. */
internal data class SpineRowModel(val ids: List<String>, val explicit: Boolean)

/** Book order and row breaks ([SpineEntry.breakBefore] by book id) that reproduce a shelf. */
internal data class SpineArrangement(val order: List<String>, val breaks: Map<String, Int>)

/**
 * Puts [dragged] on shelf row [targetRow] (counted across bays) at [insertAt] among that row's
 * remaining items, and returns the order and row breaks that keep it there.
 *
 * Rows keep their books: every row gets a break of its own, so taking a book out of a row
 * leaves a gap rather than pulling books up from below. The exception is the run of full rows
 * right after the target row, which stay flowing: a book pushed out of a full target row moves
 * on to the next row instead of opening a new one. Folders lead the shelf and never get breaks.
 *
 * Returns null when the move would put books of different groups out of order (pinned books
 * must stay ahead of the rest), as judged by [groupOf].
 */
internal fun arrangeSpineRows(
    rows: List<SpineRowModel>,
    dragged: String,
    targetRow: Int,
    insertAt: Int,
    isFolder: (String) -> Boolean,
    groupOf: (String) -> Int
): SpineArrangement? {
    if (targetRow < 0) return null
    val ids = rows.map { row -> row.ids.filterTo(mutableListOf()) { it != dragged } }.toMutableList()
    val explicit = rows.mapTo(mutableListOf()) { it.explicit }
    while (ids.size <= targetRow) {
        ids += mutableListOf<String>()
        explicit += false
    }
    val target = ids[targetRow]
    val folders = target.count(isFolder)
    target.add(insertAt.coerceIn(folders, target.size), dragged)

    val order = ids.flatten().filterNot(isFolder)
    for (i in 1 until order.size) {
        if (groupOf(order[i]) < groupOf(order[i - 1])) return null
    }

    val breaks = HashMap<String, Int>()
    var previous = -1
    var cascading = false
    ids.forEachIndexed { r, row ->
        val first = row.firstOrNull()
        if (first == null) {
            cascading = false
            return@forEachIndexed
        }
        val flowing = r > targetRow && cascading && !explicit[r] && r - previous == 1
        cascading = r == targetRow || flowing
        if (!isFolder(first) && !flowing) {
            val gap = r - previous
            val value = if (previous < 0) (if (r == 0) 0 else r + 1) else gap
            if (value > 0) breaks[first] = value
        }
        previous = r
    }
    return SpineArrangement(order, breaks)
}
