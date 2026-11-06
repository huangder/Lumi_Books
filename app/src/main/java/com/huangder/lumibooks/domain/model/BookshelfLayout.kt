package com.huangder.lumibooks.domain.model

/** Persisted values: never reinterpret the three existing layouts as column counts for Cover Flow. */
internal object BookshelfLayout {
    const val List = 1
    const val Grid = 2
    const val CompactGrid = 3
    const val CoverFlow = 4
    /** Book spines on wooden shelves. */
    const val Spine = 5

    fun normalize(value: Int): Int = value.coerceIn(List, Spine)
    fun conventional(value: Int): Int =
        if (value == CoverFlow || value == Spine) Grid else value.coerceIn(List, CompactGrid)
}
