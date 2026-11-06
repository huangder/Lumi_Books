package com.huangder.lumibooks.ui.bookshelf.spine

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal enum class SpinePose { Upright, Leaning, Flat }

/** Per-book look, fixed for one process so the shelf does not reshuffle while the app is open. */
internal data class SpineTraits(
    /** Spine thickness in dp. */
    val thickness: Float,
    /** Height relative to the tallest book a shelf row can hold. */
    val heightFraction: Float,
    val pose: SpinePose,
    /** Lean magnitude in degrees; only used when the book ends up leaning. */
    val leanDegrees: Float,
    val dropJitterMs: Int,
    /** Rotation while falling, settled to the final angle on landing. */
    val dropTilt: Float
)

internal data class SpineEntry(val id: String, val isFolder: Boolean = false)

/**
 * One item in row coordinates, in dp. The box is unrotated: [x] is its left edge from the shelf
 * start and [lift] its bottom above the board. A leaning book rotates by [angle] around its
 * bottom-left corner, which stays on the board while the top rests on the neighbour to the left;
 * a flat book is a horizontal box lying on the board or on the one below.
 */
internal data class PlacedSpine(
    val id: String,
    val isFolder: Boolean,
    val x: Float,
    val width: Float,
    val height: Float,
    val lift: Float,
    val pose: SpinePose,
    val angle: Float,
    /** Position in the page, for the drop stagger. */
    val order: Int,
    val traits: SpineTraits
)

internal data class SpineRow(val items: List<PlacedSpine>)

internal data class SpinePage(val rows: List<SpineRow>)

internal object SpineLayout {
    const val MIN_ROW_HEIGHT = 150f
    const val GAP = 1.5f
    const val FLAT_GAP = 5f
    const val FOLDER_THICKNESS = 46f
    const val MAX_STACK = 3
    /** A lean needs a neighbour at least this tall, relative to the leaning book. */
    const val LEAN_SUPPORT = 0.75f
    /** Flat stacks stay well below the shelf above. */
    const val MAX_STACK_HEIGHT_FRACTION = 0.55f

    private const val MIX = -7046029254386353131L

    /** Largest extra space added between two neighbours when a full row is spread out. */
    const val MAX_JUSTIFY_GAP = 12f

    /**
     * Spreads a full row across the shelf so consecutive bays meet without a hole. Books move in
     * groups: a leaning book stays against the neighbour it rests on, a stack moves as one.
     */
    fun justified(items: List<PlacedSpine>, shelfWidth: Float): List<PlacedSpine> {
        if (items.size < 2) return items.toList()
        val groups = IntArray(items.size)
        var group = 0
        items.forEachIndexed { index, item ->
            val attached = index > 0 && (item.lift > 0f || item.pose == SpinePose.Leaning)
            if (index > 0 && !attached) group++
            groups[index] = group
        }
        if (group == 0) return items.toList()
        val extent = items.maxOf { rightEdge(it) }
        val leftover = shelfWidth - extent
        if (leftover <= 0f) return items.toList()
        val step = min(leftover / group, MAX_JUSTIFY_GAP)
        return items.mapIndexed { index, item -> item.copy(x = item.x + step * groups[index]) }
    }

    /** Rightmost point of a placed book, after any lean (about its bottom-left corner). */
    fun rightEdge(item: PlacedSpine): Float = if (item.pose == SpinePose.Leaning) {
        item.x + item.width * cos(Math.toRadians(item.angle.toDouble())).toFloat()
    } else item.x + item.width

    fun rowCount(availableHeight: Float): Int = if (availableHeight / 4f >= MIN_ROW_HEIGHT) 4 else 3

    fun traits(id: String, seed: Long, isFolder: Boolean = false): SpineTraits {
        val random = Random(seed xor (id.hashCode().toLong() * MIX))
        val thickness = if (isFolder) FOLDER_THICKNESS else 20f + random.nextFloat() * random.nextFloat() * 20f
        val heightFraction = 0.80f + random.nextFloat() * 0.20f
        val roll = random.nextFloat()
        val pose = when {
            isFolder -> SpinePose.Upright
            roll < 0.80f -> SpinePose.Upright
            roll < 0.92f -> SpinePose.Leaning
            else -> SpinePose.Flat
        }
        val lean = 7f + random.nextFloat() * 6f
        val jitter = random.nextInt(0, 70)
        val tiltRange = if (pose == SpinePose.Flat) 25f else 8f
        val tilt = (random.nextFloat() * 2f - 1f) * tiltRange
        return SpineTraits(thickness, heightFraction, pose, lean, jitter, tilt)
    }

    /**
     * Flows entries left to right, then top to bottom, filling a page of [rows] shelves before
     * the next. Every page has exactly [rows] rows; an empty library still has one empty page.
     */
    fun paginate(
        entries: List<SpineEntry>,
        seed: Long,
        shelfWidth: Float,
        maxBookHeight: Float,
        rows: Int
    ): List<SpinePage> {
        require(rows > 0)
        val pages = mutableListOf<SpinePage>()
        val pageRows = mutableListOf<SpineRow>()
        val row = mutableListOf<PlacedSpine>()
        var cursor = 0f
        var order = 0
        var stackSize = 0
        var stackX = 0f
        var stackWidth = 0f
        var stackHeight = 0f

        fun closeRow(justify: Boolean = true) {
            pageRows += SpineRow(if (justify) justified(row, shelfWidth) else row.toList())
            row.clear()
            cursor = 0f
            stackSize = 0
            if (pageRows.size == rows) {
                pages += SpinePage(pageRows.toList())
                pageRows.clear()
                order = 0
            }
        }

        for (entry in entries) {
            val traits = traits(entry.id, seed, entry.isFolder)
            var height = maxBookHeight * traits.heightFraction
            val width = traits.thickness
            var pose = traits.pose

            if (pose == SpinePose.Flat) {
                val length = height * 0.92f
                val thick = width * 0.85f
                val previous = row.lastOrNull()
                if (stackSize in 1 until MAX_STACK && previous?.pose == SpinePose.Flat &&
                    stackHeight + thick <= maxBookHeight * MAX_STACK_HEIGHT_FRACTION
                ) {
                    val offset = ((traits.dropJitterMs % 9) - 4).toFloat()
                    val x = (stackX + (stackWidth - length) / 2f + offset)
                        .coerceIn(0f, (shelfWidth - length).coerceAtLeast(0f))
                    row += PlacedSpine(entry.id, entry.isFolder, x, length, thick, stackHeight,
                        SpinePose.Flat, 0f, order++, traits)
                    stackHeight += thick
                    stackSize++
                    cursor = maxOf(cursor, x + length + FLAT_GAP)
                    continue
                }
                // At the end of a row a book that will not lie down there stands up instead,
                // so the row stays full.
                val fitsStanding = row.isNotEmpty() && cursor + length > shelfWidth && cursor + width <= shelfWidth
                if (length <= shelfWidth && !fitsStanding) {
                    if (cursor + length > shelfWidth && row.isNotEmpty()) closeRow()
                    row += PlacedSpine(entry.id, entry.isFolder, cursor, length, thick, 0f,
                        SpinePose.Flat, 0f, order++, traits)
                    stackX = cursor
                    stackWidth = length
                    stackHeight = thick
                    stackSize = 1
                    cursor += length + FLAT_GAP
                    continue
                }
                pose = SpinePose.Upright
            }
            stackSize = 0

            if (pose == SpinePose.Leaning) {
                val radians = Math.toRadians(traits.leanDegrees.toDouble())
                // Tilted, the top-right corner rises by width·sin; keep it under the shelf above.
                val tallest = ((maxBookHeight - width * sin(radians)) / cos(radians)).toFloat()
                height = min(height, tallest)
                val footprint = (width * cos(radians) + height * sin(radians)).toFloat()
                if (cursor + footprint > shelfWidth && row.isNotEmpty() && cursor + width <= shelfWidth) {
                    height = maxBookHeight * traits.heightFraction
                    pose = SpinePose.Upright
                } else if (cursor + footprint > shelfWidth && row.isNotEmpty()) closeRow()
            }
            if (pose == SpinePose.Leaning) {
                val radians = Math.toRadians(traits.leanDegrees.toDouble())
                val footprint = (width * cos(radians) + height * sin(radians)).toFloat()
                val previous = row.lastOrNull()
                val supported = previous == null ||
                    (previous.pose == SpinePose.Upright && previous.height >= height * LEAN_SUPPORT)
                if (supported) {
                    // The top-left corner rests on the neighbour (or the shelf wall) at [cursor];
                    // the bottom-left corner stands on the board, the bottom-right lifts off it.
                    val x = cursor + (height * sin(radians)).toFloat()
                    row += PlacedSpine(entry.id, entry.isFolder, x, width, height, 0f,
                        SpinePose.Leaning, -traits.leanDegrees, order++, traits)
                    cursor += footprint + GAP
                    continue
                }
                height = maxBookHeight * traits.heightFraction
                pose = SpinePose.Upright
            }

            if (cursor + width > shelfWidth && row.isNotEmpty()) closeRow()
            row += PlacedSpine(entry.id, entry.isFolder, cursor, width, height, 0f,
                SpinePose.Upright, 0f, order++, traits)
            cursor += width + GAP
        }

        // The last row of the library is left ragged, like the end of a real shelf.
        if (row.isNotEmpty()) closeRow(justify = false)
        if (pageRows.isNotEmpty() || pages.isEmpty()) {
            while (pageRows.size < rows) pageRows += SpineRow(emptyList())
            pages += SpinePage(pageRows.toList())
        }
        return pages
    }
}
