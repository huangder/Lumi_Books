package com.huangder.lumibooks.ui.bookshelf.spine

import kotlin.math.abs

internal const val SPINE_FOLDER_PREFIX = "folder:"

internal fun isSpineDragHorizontal(dx: Float, dy: Float, slop: Float): Boolean =
    abs(dx) >= slop && abs(dx) >= abs(dy) * 0.5f

/** Books follow the selected sort. Saved folder slots can be interleaved among them. */
internal fun spineShelfOrder(
    bookIds: List<String>,
    folderIds: List<String>,
    savedOrder: List<String>
): List<String> {
    val folders = folderIds.toHashSet()
    val books = bookIds.toHashSet()
    val saved = savedOrder.filter { it in books || it in folders }.distinct()
    val known = saved.toHashSet()
    val unplacedBooks = bookIds.count { it !in known }
    val bookSlots = bookIds.iterator()
    return buildList {
        addAll(folderIds.filterNot { it in known })
        repeat(unplacedBooks) { add(bookSlots.next()) }
        for (id in saved) add(if (id in folders) id else bookSlots.next())
    }
}

/**
 * Row membership is independent of horizontal bays. Each row packs from the far left.
 * [stacked] items lie on the flat book before them; [poses] are the placements chosen per book.
 */
internal data class SpineArrangement(
    val order: List<String>,
    val rowsByItem: Map<String, Int>,
    val stacked: Set<String> = emptySet(),
    val poses: Map<String, SpinePose> = emptyMap()
) {
    fun layout(shelfWidth: Float, maxBookHeight: Float, rowsPerBay: Int): List<SpinePage> {
        fun paginate(ids: List<String>, rows: Int) = SpineLayout.paginate(
            ids.map { SpineEntry(it, it.startsWith(SPINE_FOLDER_PREFIX), poses[it], it in stacked) },
            SpineLayout.SHELF_SEED, shelfWidth, maxBookHeight, rows
        )
        if (order.none { it in rowsByItem }) return paginate(order, rowsPerBay)

        val rows = order.groupBy { (rowsByItem[it] ?: 0).coerceIn(0, rowsPerBay - 1) }
        val segments = List(rowsPerBay) { row ->
            paginate(rows[row].orEmpty(), rows = 1).map { it.rows.single() }
        }
        return List(segments.maxOf { it.size }) { bay ->
            SpinePage(segments.map { it.getOrNull(bay) ?: SpineRow(emptyList()) })
        }
    }
}

/**
 * Slot relative to the visible segment of the row, excluding the dragged item. With [stackOn]
 * the item is laid on top of the flat stack that holds that item instead.
 */
internal data class SpineDropTarget(val bay: Int, val row: Int, val slot: Int, val stackOn: String? = null)

/**
 * The flat item under [x] in a row, for dropping a flat book onto it. Only the middle of a pile
 * counts, so its two ends still insert beside it.
 */
internal fun spineStackTarget(
    pages: List<SpinePage>,
    pageIndex: Int,
    rowIndex: Int,
    x: Float,
    dragged: String
): SpineDropTarget? {
    val row = pages.getOrNull(pageIndex)?.rows?.getOrNull(rowIndex) ?: return null
    val under = row.items.lastOrNull { item ->
        item.id != dragged && !item.isFolder && item.pose == SpinePose.Flat &&
            x >= item.x + item.width * 0.15f && x <= item.x + item.width * 0.85f
    } ?: return null
    return SpineDropTarget(pageIndex, rowIndex, slot = 0, stackOn = under.id)
}

/** Top of the pile [base] belongs to: following items in its row that are lifted onto it. */
private fun stackTop(items: List<PlacedSpine>, base: String, dragged: String): String {
    val rest = items.filter { it.id != dragged }
    var index = rest.indexOfFirst { it.id == base }
    if (index < 0) return base
    while (index + 1 < rest.size && rest[index + 1].lift > 0f && rest[index + 1].pose == SpinePose.Flat) index++
    return rest[index].id
}

internal fun spineDropTarget(
    pages: List<SpinePage>,
    pageIndex: Int,
    rowIndex: Int,
    x: Float,
    dragged: String
): SpineDropTarget? {
    val row = pages.getOrNull(pageIndex)?.rows?.getOrNull(rowIndex) ?: return null
    val slot = row.items.count { it.id != dragged && it.x + it.width / 2f < x }
    return SpineDropTarget(pageIndex, rowIndex, slot)
}

internal fun arrangeSpineRows(
    source: SpineArrangement,
    pages: List<SpinePage>,
    dragged: String,
    target: SpineDropTarget,
    isPinned: (String) -> Boolean
): SpineArrangement? {
    if (dragged !in source.order) return null
    val targetItems = pages.getOrNull(target.bay)?.rows?.getOrNull(target.row)?.items ?: return null
    val shelfRows = buildMap {
        for (page in pages) {
            page.rows.forEachIndexed { row, segment -> segment.items.forEach { put(it.id, row) } }
        }
    }
    target.stackOn?.let { base ->
        if (base == dragged || base !in source.order) return null
        val top = stackTop(targetItems, base, dragged)
        val order = source.order.filterNot { it == dragged }.toMutableList()
        order.add(order.indexOf(top) + 1, dragged)
        val books = order.filterNot { it.startsWith(SPINE_FOLDER_PREFIX) }
        if (books.dropWhile(isPinned).any(isPinned)) return null
        return source.copy(order = order, rowsByItem = shelfRows + (dragged to target.row),
            stacked = source.stacked + dragged)
    }
    val rowOrder = source.order.filter { shelfRows[it] == target.row }
    val remainingInRow = rowOrder.filterNot { it == dragged }
    val slotInRow = pages.take(target.bay).sumOf { page ->
        page.rows[target.row].items.count { it.id != dragged }
    } + target.slot.coerceIn(0, targetItems.count { it.id != dragged })
    if (rowOrder.indexOf(dragged) == slotInRow) return source.copy(stacked = source.stacked - dragged)

    val order = source.order.filterNot { it == dragged }.toMutableList()
    val next = remainingInRow.getOrNull(slotInRow)
    val previous = remainingInRow.getOrNull(slotInRow - 1)
    val slot = when {
        next != null -> order.indexOf(next)
        previous != null -> order.indexOf(previous) + 1
        else -> {
            // An empty row still has a position in the shelf's reading order.
            val following = pages.asSequence().drop(target.bay).flatMapIndexed { bay, page ->
                page.rows.asSequence().drop(if (bay == 0) target.row + 1 else 0)
            }.flatMap { it.items.asSequence() }.firstOrNull { it.id != dragged }
            following?.let { order.indexOf(it.id) } ?: order.size
        }
    }
    order.add(slot, dragged)
    val books = order.filterNot { it.startsWith(SPINE_FOLDER_PREFIX) }
    if (books.dropWhile(isPinned).any(isPinned)) return null

    return source.copy(order = order, rowsByItem = shelfRows + (dragged to target.row),
        stacked = source.stacked - dragged)
}
